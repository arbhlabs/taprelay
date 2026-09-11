using System.Security.Cryptography;
using System.Text;
using System.Text.Json;

namespace ArbhLabs.TapRelay.PcRelay;

/// <summary>
/// Paired phones, stored as SHA-256 hashes of their tokens in
/// %APPDATA%\ARBH Labs\TapRelay PC Relay\trust.json - never the tokens themselves.
/// </summary>
public sealed class TrustStore
{
    private sealed class Data
    {
        public string RelayId { get; set; } = Guid.NewGuid().ToString("N");
        public List<string> TokenHashes { get; set; } = new();
        public bool Launched { get; set; }
    }

    public bool HasLaunchedBefore => _data.Launched;

    public void MarkLaunched()
    {
        lock (_lock) { _data.Launched = true; Save(); }
    }

    private readonly string _path;
    private readonly object _lock = new();
    private Data _data;

    public TrustStore()
    {
        var dir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "ARBH Labs", "TapRelay PC Relay");
        Directory.CreateDirectory(dir);
        _path = Path.Combine(dir, "trust.json");
        try { _data = JsonSerializer.Deserialize<Data>(File.ReadAllText(_path)) ?? new Data(); }
        catch { _data = new Data(); }
        Save();
    }

    public string RelayId => _data.RelayId;
    public int PairedCount { get { lock (_lock) return _data.TokenHashes.Count; } }

    public void Add(string token)
    {
        lock (_lock) { _data.TokenHashes.Add(Hash(token)); Save(); }
    }

    public bool IsTrusted(string token)
    {
        if (string.IsNullOrEmpty(token)) return false;
        var h = Hash(token);
        lock (_lock) return _data.TokenHashes.Any(x => CryptographicOperations.FixedTimeEquals(
            Encoding.ASCII.GetBytes(x), Encoding.ASCII.GetBytes(h)));
    }

    public void ForgetAll()
    {
        lock (_lock) { _data.TokenHashes.Clear(); Save(); }
    }

    private static string Hash(string token) => Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(token)));

    private void Save() => File.WriteAllText(_path, JsonSerializer.Serialize(_data));
}
