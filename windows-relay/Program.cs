using Microsoft.Win32;

namespace ArbhLabs.TapRelay.PcRelay;

internal static class Program
{
    private const string RunKey = @"Software\Microsoft\Windows\CurrentVersion\Run";
    private const string RunName = "TapRelay PC Relay";

    [STAThread]
    private static void Main(string[] args)
    {
        using var single = new Mutex(true, @"Local\ArbhLabs.TapRelay.PcRelay", out var first);
        if (!first) return; // already running in the tray

        ApplicationConfiguration.Initialize();
        var trust = new TrustStore();
        using var server = new RelayServer(trust);
        server.Start();
        // Open the window on a manual launch (it shows the pairing code); stay in the tray at login.
        Application.Run(new RelayForm(server, trust, showWindow: !args.Contains("--tray")));
    }

    public static bool StartsWithWindows
    {
        get
        {
            using var key = Registry.CurrentUser.OpenSubKey(RunKey);
            return key?.GetValue(RunName) is string;
        }
        set
        {
            using var key = Registry.CurrentUser.CreateSubKey(RunKey);
            if (value) key.SetValue(RunName, $"\"{Environment.ProcessPath}\" --tray");
            else key.DeleteValue(RunName, false);
        }
    }
}
