using System.Diagnostics;
using System.Runtime.InteropServices;

namespace ArbhLabs.TapRelay.PcRelay;

/// <summary>
/// Everything a paired phone may ask this PC to do. Media keys go through SendInput, so the
/// browser's media session (YouTube in Chrome/Edge) reacts even when it is not the focused window.
/// </summary>
public sealed class ActionRunner
{
    public (bool ok, string message) Run(string action, string? value)
    {
        try
        {
            switch (action)
            {
                case "media.play_pause": Tap(VK_MEDIA_PLAY_PAUSE); return (true, "Play/Pause");
                case "media.next": Tap(VK_MEDIA_NEXT_TRACK); return (true, "Next track");
                case "media.previous": Tap(VK_MEDIA_PREV_TRACK); return (true, "Previous track");
                case "media.stop": Tap(VK_MEDIA_STOP); return (true, "Stop");
                case "media.volume_up": Tap(VK_VOLUME_UP); return (true, "Volume up");
                case "media.volume_down": Tap(VK_VOLUME_DOWN); return (true, "Volume down");
                case "media.mute": Tap(VK_VOLUME_MUTE); return (true, "Mute");
                case "keys.send":
                    if (string.IsNullOrWhiteSpace(value)) return (false, "No shortcut given");
                    return SendShortcut(value);
                case "open.target":
                    if (string.IsNullOrWhiteSpace(value)) return (false, "Nothing to open");
                    Process.Start(new ProcessStartInfo(value.Trim()) { UseShellExecute = true });
                    return (true, $"Opened {value.Trim()}");
                case "system.lock":
                    return LockWorkStation() ? (true, "Locked") : (false, "Could not lock");
                case "display.off":
                    PostMessage(HWND_BROADCAST, WM_SYSCOMMAND, SC_MONITORPOWER, 2);
                    return (true, "Screen off");
                case "system.sleep":
                    // Delay so the HTTP reply reaches the phone before the network goes down.
                    Task.Delay(800).ContinueWith(_ => SetSuspendState(false, false, false));
                    return (true, "Going to sleep");
                default:
                    return (false, $"Unknown action {action}");
            }
        }
        catch (Exception e)
        {
            return (false, e.Message);
        }
    }

    // ---------------------------------------------------------------- shortcuts

    private static readonly Dictionary<string, ushort> Named = new(StringComparer.OrdinalIgnoreCase)
    {
        ["ctrl"] = 0x11, ["control"] = 0x11, ["alt"] = 0x12, ["shift"] = 0x10, ["win"] = 0x5B, ["windows"] = 0x5B,
        ["enter"] = 0x0D, ["return"] = 0x0D, ["space"] = 0x20, ["tab"] = 0x09, ["esc"] = 0x1B, ["escape"] = 0x1B,
        ["backspace"] = 0x08, ["delete"] = 0x2E, ["del"] = 0x2E, ["insert"] = 0x2D, ["home"] = 0x24, ["end"] = 0x23,
        ["pageup"] = 0x21, ["pagedown"] = 0x22, ["left"] = 0x25, ["up"] = 0x26, ["right"] = 0x27, ["down"] = 0x28,
        ["printscreen"] = 0x2C, ["prtsc"] = 0x2C, ["capslock"] = 0x14,
        ["playpause"] = VK_MEDIA_PLAY_PAUSE, ["next"] = VK_MEDIA_NEXT_TRACK, ["prev"] = VK_MEDIA_PREV_TRACK,
        ["volup"] = VK_VOLUME_UP, ["voldown"] = VK_VOLUME_DOWN, ["mute"] = VK_VOLUME_MUTE,
        [";"] = 0xBA, ["="] = 0xBB, [","] = 0xBC, ["-"] = 0xBD, ["."] = 0xBE, ["/"] = 0xBF, ["`"] = 0xC0,
        ["["] = 0xDB, ["\\"] = 0xDC, ["]"] = 0xDD, ["'"] = 0xDE
    };

    /// <summary>Parses "ctrl+shift+m", "alt+tab", "f11", "win+d", "space" and presses it once.</summary>
    public static (bool, string) SendShortcut(string combo)
    {
        var keys = new List<ushort>();
        foreach (var raw in combo.Split('+', StringSplitOptions.TrimEntries | StringSplitOptions.RemoveEmptyEntries))
        {
            if (Named.TryGetValue(raw, out var vk)) keys.Add(vk);
            else if (raw.Length == 1 && char.IsAsciiLetterOrDigit(raw[0])) keys.Add(char.ToUpperInvariant(raw[0]));
            else if ((raw[0] is 'f' or 'F') && int.TryParse(raw[1..], out var f) && f is >= 1 and <= 24) keys.Add((ushort)(0x70 + f - 1));
            else return (false, $"Unknown key '{raw}'");
        }
        if (keys.Count == 0) return (false, "Empty shortcut");
        var inputs = new List<INPUT>();
        foreach (var k in keys) inputs.Add(Key(k, false));
        for (var i = keys.Count - 1; i >= 0; i--) inputs.Add(Key(keys[i], true));
        SendInput((uint)inputs.Count, inputs.ToArray(), Marshal.SizeOf<INPUT>());
        return (true, $"Pressed {combo}");
    }

    private static void Tap(ushort vk) => SendInput(2, new[] { Key(vk, false), Key(vk, true) }, Marshal.SizeOf<INPUT>());

    private static INPUT Key(ushort vk, bool up) => new()
    {
        type = 1,
        U = new InputUnion { ki = new KEYBDINPUT { wVk = vk, dwFlags = (up ? 0x0002u : 0u) | (IsExtended(vk) ? 0x0001u : 0u) } }
    };

    private static bool IsExtended(ushort vk) => vk is >= 0x21 and <= 0x2E || vk is 0x5B or >= 0xA6 and <= 0xB7;

    // ---------------------------------------------------------------- Win32

    private const ushort VK_MEDIA_PLAY_PAUSE = 0xB3, VK_MEDIA_NEXT_TRACK = 0xB0, VK_MEDIA_PREV_TRACK = 0xB1,
        VK_MEDIA_STOP = 0xB2, VK_VOLUME_UP = 0xAF, VK_VOLUME_DOWN = 0xAE, VK_VOLUME_MUTE = 0xAD;
    private static readonly IntPtr HWND_BROADCAST = new(0xFFFF);
    private const uint WM_SYSCOMMAND = 0x0112;
    private static readonly IntPtr SC_MONITORPOWER = new(0xF170);

    [StructLayout(LayoutKind.Sequential)]
    private struct INPUT { public uint type; public InputUnion U; }

    [StructLayout(LayoutKind.Explicit)]
    private struct InputUnion
    {
        [FieldOffset(0)] public MOUSEINPUT mi;
        [FieldOffset(0)] public KEYBDINPUT ki;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct MOUSEINPUT { public int dx, dy; public uint mouseData, dwFlags, time; public IntPtr dwExtraInfo; }

    [StructLayout(LayoutKind.Sequential)]
    private struct KEYBDINPUT { public ushort wVk, wScan; public uint dwFlags, time; public IntPtr dwExtraInfo; }

    [DllImport("user32.dll", SetLastError = true)] private static extern uint SendInput(uint n, INPUT[] inputs, int size);
    [DllImport("user32.dll")] private static extern bool LockWorkStation();
    [DllImport("user32.dll")] private static extern bool PostMessage(IntPtr hWnd, uint msg, IntPtr wParam, IntPtr lParam);
    [DllImport("powrprof.dll")] private static extern bool SetSuspendState(bool hibernate, bool force, bool disableWake);
}
