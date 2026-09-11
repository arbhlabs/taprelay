using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace ArbhLabs.TapRelay.PcRelay;

/// <summary>
/// TapRelay's PC relay contract (docs/PC_RELAY_PROTOCOL.md), protocol 1:
/// UDP 38117 "TAPRELAY_DISCOVER/1" -> JSON DiscoveryResponse; HTTP POST /v1/pair and /v1/action.
/// Only a phone that typed the pairing code shown on this PC gets a token, and every action needs it.
/// </summary>
public sealed class RelayServer : IDisposable
{
    public const int Protocol = 1;
    public const int DiscoveryPort = 38117;
    public const int HttpPort = 38118;

    private readonly TrustStore _trust;
    private readonly ActionRunner _runner = new();
    private readonly CancellationTokenSource _cts = new();
    private readonly LinkedList<(string id, ActionResponse response)> _recent = new();
    private int _failedPairs;

    public string PairingCode { get; private set; } = NewCode();
    public event Action? StateChanged;
    public event Action<string>? Activity;

    public RelayServer(TrustStore trust) => _trust = trust;

    public static readonly JsonSerializerOptions Json = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        DefaultIgnoreCondition = JsonIgnoreCondition.Never
    };

    public void Start()
    {
        _ = Task.Run(() => DiscoveryLoop(_cts.Token));
        _ = Task.Run(() => HttpLoop(_cts.Token));
    }

    public void NewPairingCode()
    {
        PairingCode = NewCode();
        _failedPairs = 0;
        StateChanged?.Invoke();
    }

    private static string NewCode() => RandomNumberGenerator.GetInt32(0, 1_000_000).ToString("D6");

    // ---------------------------------------------------------------- discovery

    private async Task DiscoveryLoop(CancellationToken ct)
    {
        using var udp = new UdpClient(AddressFamily.InterNetwork);
        udp.Client.SetSocketOption(SocketOptionLevel.Socket, SocketOptionName.ReuseAddress, true);
        udp.Client.Bind(new IPEndPoint(IPAddress.Any, DiscoveryPort));
        while (!ct.IsCancellationRequested)
        {
            UdpReceiveResult packet;
            try { packet = await udp.ReceiveAsync(ct); }
            catch (OperationCanceledException) { return; }
            catch (SocketException) { continue; }

            var text = Encoding.UTF8.GetString(packet.Buffer).Trim();
            if (text != $"TAPRELAY_DISCOVER/{Protocol}") continue;
            var reply = new DiscoveryResponse(Protocol, Environment.MachineName,
                LocalAddressFor(packet.RemoteEndPoint.Address), HttpPort, true);
            var bytes = JsonSerializer.SerializeToUtf8Bytes(reply, Json);
            try { await udp.SendAsync(bytes, packet.RemoteEndPoint, ct); } catch { /* phone went away */ }
        }
    }

    /// <summary>The address of this PC on the same subnet as the phone (not a VPN adapter).</summary>
    public static string LocalAddressFor(IPAddress remote)
    {
        var r = remote.GetAddressBytes();
        string? fallback = null;
        foreach (var nic in NetworkInterface.GetAllNetworkInterfaces())
        {
            if (nic.OperationalStatus != OperationalStatus.Up) continue;
            foreach (var ua in nic.GetIPProperties().UnicastAddresses)
            {
                if (ua.Address.AddressFamily != AddressFamily.InterNetwork || IPAddress.IsLoopback(ua.Address)) continue;
                var a = ua.Address.GetAddressBytes();
                var mask = ua.IPv4Mask?.GetAddressBytes() ?? new byte[] { 255, 255, 255, 0 };
                var same = true;
                for (var i = 0; i < 4; i++) if ((a[i] & mask[i]) != (r[i] & mask[i])) same = false;
                if (same) return ua.Address.ToString();
                if (fallback == null && a[0] == 192 && a[1] == 168) fallback = ua.Address.ToString();
            }
        }
        return fallback ?? "127.0.0.1";
    }

    // ---------------------------------------------------------------- HTTP

    private async Task HttpLoop(CancellationToken ct)
    {
        var listener = new TcpListener(IPAddress.Any, HttpPort);
        listener.Start();
        while (!ct.IsCancellationRequested)
        {
            TcpClient client;
            try { client = await listener.AcceptTcpClientAsync(ct); }
            catch (OperationCanceledException) { break; }
            _ = Task.Run(() => Handle(client, ct));
        }
        listener.Stop();
    }

    private async Task Handle(TcpClient client, CancellationToken ct)
    {
        using var _ = client;
        client.ReceiveTimeout = 5000;
        client.SendTimeout = 5000;
        using var stream = client.GetStream();
        try
        {
            var (method, path, headers, body) = await ReadRequest(stream, ct);
            var (status, payload) = Route(method, path, headers, body);
            await WriteResponse(stream, status, payload, ct);
        }
        catch
        {
            // Malformed or abandoned request: drop the connection.
        }
    }

    private (int status, object payload) Route(string method, string path, Dictionary<string, string> headers, string body)
    {
        if (method != "POST") return (405, new { error = "POST only" });
        switch (path)
        {
            case "/v1/pair":
            {
                var req = JsonSerializer.Deserialize<PairRequest>(body, Json);
                if (req == null || req.Protocol != Protocol) return (400, new { error = "unsupported protocol" });
                if (req.PairingCode?.Trim() != PairingCode)
                {
                    // Five wrong guesses retire the code, so it cannot be brute-forced.
                    if (++_failedPairs >= 5) NewPairingCode();
                    Activity?.Invoke("Pairing refused: wrong code");
                    return (403, new { error = "wrong pairing code" });
                }
                var token = Convert.ToBase64String(RandomNumberGenerator.GetBytes(32));
                _trust.Add(token);
                NewPairingCode();
                Activity?.Invoke("Phone paired");
                return (200, new PairResponse(Protocol, _trust.RelayId, token));
            }
            case "/v1/action":
            {
                var req = JsonSerializer.Deserialize<ActionRequest>(body, Json);
                if (req == null) return (400, new { error = "bad request" });
                var auth = headers.GetValueOrDefault("authorization", "");
                var token = auth.StartsWith("Bearer ", StringComparison.OrdinalIgnoreCase) ? auth[7..].Trim() : "";
                if (!_trust.IsTrusted(token))
                    return (401, new ActionResponse(Protocol, req.EventId, false, true, "This phone is not paired. Pair it again."));
                if (req.Protocol != Protocol)
                    return (400, new ActionResponse(Protocol, req.EventId, false, true, "Protocol mismatch"));

                lock (_recent)
                {
                    // A retried delivery returns the first result instead of pressing twice.
                    foreach (var (id, previous) in _recent) if (id == req.EventId) return (200, previous);
                }
                var (ok, message) = _runner.Run(req.Action, req.Value);
                var response = new ActionResponse(Protocol, req.EventId, ok, true, message);
                lock (_recent)
                {
                    _recent.AddFirst((req.EventId, response));
                    while (_recent.Count > 200) _recent.RemoveLast();
                }
                Activity?.Invoke(ok ? message : $"Failed: {message}");
                return (200, response);
            }
            default:
                return (404, new { error = "not found" });
        }
    }

    private static async Task<(string, string, Dictionary<string, string>, string)> ReadRequest(NetworkStream stream, CancellationToken ct)
    {
        var buffer = new List<byte>(4096);
        var chunk = new byte[4096];
        int headerEnd = -1;
        while (headerEnd < 0)
        {
            var n = await stream.ReadAsync(chunk, ct);
            if (n == 0) throw new IOException("closed");
            buffer.AddRange(chunk.AsSpan(0, n).ToArray());
            if (buffer.Count > 64 * 1024) throw new IOException("too large");
            headerEnd = IndexOf(buffer, "\r\n\r\n"u8.ToArray());
        }
        var headText = Encoding.ASCII.GetString(buffer.GetRange(0, headerEnd).ToArray());
        var lines = headText.Split("\r\n");
        var first = lines[0].Split(' ');
        var headers = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
        foreach (var line in lines.Skip(1))
        {
            var i = line.IndexOf(':');
            if (i > 0) headers[line[..i].Trim().ToLowerInvariant()] = line[(i + 1)..].Trim();
        }
        var length = int.TryParse(headers.GetValueOrDefault("content-length"), out var l) ? Math.Clamp(l, 0, 64 * 1024) : 0;
        var bodyBytes = buffer.Skip(headerEnd + 4).ToList();
        while (bodyBytes.Count < length)
        {
            var n = await stream.ReadAsync(chunk, ct);
            if (n == 0) break;
            bodyBytes.AddRange(chunk.AsSpan(0, n).ToArray());
        }
        var path = first.Length > 1 ? first[1].Split('?')[0] : "/";
        return (first[0].ToUpperInvariant(), path, headers, Encoding.UTF8.GetString(bodyBytes.Take(length).ToArray()));
    }

    private static int IndexOf(List<byte> haystack, byte[] needle)
    {
        for (var i = 0; i <= haystack.Count - needle.Length; i++)
        {
            var match = true;
            for (var j = 0; j < needle.Length; j++) if (haystack[i + j] != needle[j]) { match = false; break; }
            if (match) return i;
        }
        return -1;
    }

    private static async Task WriteResponse(NetworkStream stream, int status, object payload, CancellationToken ct)
    {
        var body = JsonSerializer.SerializeToUtf8Bytes(payload, payload.GetType(), Json);
        var reason = status switch { 200 => "OK", 400 => "Bad Request", 401 => "Unauthorized", 403 => "Forbidden", 404 => "Not Found", _ => "Error" };
        var head = $"HTTP/1.1 {status} {reason}\r\nContent-Type: application/json\r\nContent-Length: {body.Length}\r\nConnection: close\r\n\r\n";
        await stream.WriteAsync(Encoding.ASCII.GetBytes(head), ct);
        await stream.WriteAsync(body, ct);
    }

    public void Dispose() => _cts.Cancel();
}

public sealed record DiscoveryResponse(int Protocol, string Name, string Host, int Port, bool PairingRequired);
public sealed record PairRequest(int Protocol, string? PairingCode);
public sealed record PairResponse(int Protocol, string RelayId, string Token);
public sealed record ActionRequest(int Protocol, string EventId, string OwnerId, string Action, string? Value);
public sealed record ActionResponse(int Protocol, string EventId, bool Accepted, bool Online, string? Message);
