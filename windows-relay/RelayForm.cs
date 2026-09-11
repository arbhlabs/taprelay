namespace ArbhLabs.TapRelay.PcRelay;

/// <summary>The one small window: pairing code, what just happened, start-with-Windows.</summary>
public sealed class RelayForm : Form
{
    private readonly RelayServer _server;
    private readonly TrustStore _trust;
    private readonly Label _code = new() { Font = new Font("Segoe UI", 28, FontStyle.Bold), AutoSize = true };
    private readonly Label _status = new() { AutoSize = true, ForeColor = Color.DimGray };
    private readonly ListBox _log = new() { Height = 110, Dock = DockStyle.Fill, IntegralHeight = false };
    private readonly NotifyIcon _tray;
    private bool _allowClose;

    public RelayForm(RelayServer server, TrustStore trust, bool showWindow)
    {
        _server = server;
        _trust = trust;
        Text = "TapRelay PC Relay";
        Icon = SystemIcons.Application;
        FormBorderStyle = FormBorderStyle.FixedDialog;
        MaximizeBox = false;
        StartPosition = FormStartPosition.CenterScreen;
        AutoSize = true;
        AutoSizeMode = AutoSizeMode.GrowAndShrink;
        Padding = new Padding(16);

        var layout = new TableLayoutPanel { ColumnCount = 1, AutoSize = true, Dock = DockStyle.Fill, Width = 380 };
        layout.Controls.Add(new Label
        {
            AutoSize = true,
            MaximumSize = new Size(380, 0),
            Text = "In TapRelay on your phone: ⋮ → Windows PC → Find my PC, then type this code:"
        });
        layout.Controls.Add(_code);
        var newCode = new Button { Text = "New code", AutoSize = true };
        newCode.Click += (_, _) => _server.NewPairingCode();
        layout.Controls.Add(newCode);
        layout.Controls.Add(_status);
        layout.Controls.Add(new Label { Text = "Recent actions", AutoSize = true, Margin = new Padding(0, 10, 0, 2) });
        var logHost = new Panel { Width = 380, Height = 110 };
        logHost.Controls.Add(_log);
        layout.Controls.Add(logHost);

        var autostart = new CheckBox { Text = "Start with Windows (in the tray)", AutoSize = true, Checked = Program.StartsWithWindows };
        autostart.CheckedChanged += (_, _) => Program.StartsWithWindows = autostart.Checked;
        layout.Controls.Add(autostart);
        var forget = new Button { Text = "Forget paired phones", AutoSize = true };
        forget.Click += (_, _) => { _trust.ForgetAll(); Refresh(); };
        layout.Controls.Add(forget);
        Controls.Add(layout);

        _tray = new NotifyIcon { Icon = SystemIcons.Application, Text = "TapRelay PC Relay", Visible = true };
        var menu = new ContextMenuStrip();
        menu.Items.Add("Show", null, (_, _) => ShowWindow());
        menu.Items.Add("Quit", null, (_, _) => { _allowClose = true; Close(); });
        _tray.ContextMenuStrip = menu;
        _tray.DoubleClick += (_, _) => ShowWindow();

        // First launch: start with Windows by default so the controller keeps working after a reboot.
        if (!_trust.HasLaunchedBefore) { Program.StartsWithWindows = true; autostart.Checked = true; _trust.MarkLaunched(); }
        // Keep the startup entry pointing at wherever this copy now lives (it may have been moved).
        else if (Program.StartsWithWindows) Program.StartsWithWindows = true;

        _server.StateChanged += () => BeginInvoke(Refresh);
        _server.Activity += message => BeginInvoke(() =>
        {
            _log.Items.Insert(0, $"{DateTime.Now:HH:mm:ss}  {message}");
            while (_log.Items.Count > 50) _log.Items.RemoveAt(_log.Items.Count - 1);
            Refresh();
        });
        Refresh();
        if (!showWindow) { ShowInTaskbar = false; WindowState = FormWindowState.Minimized; Load += (_, _) => Hide(); }
    }

    private new void Refresh()
    {
        _code.Text = _server.PairingCode;
        _status.Text = _trust.PairedCount == 0
            ? $"Not paired yet • listening on port {RelayServer.HttpPort}"
            : $"{_trust.PairedCount} phone(s) paired • listening on port {RelayServer.HttpPort}";
    }

    private void ShowWindow()
    {
        ShowInTaskbar = true;
        Show();
        WindowState = FormWindowState.Normal;
        Activate();
    }

    protected override void OnFormClosing(FormClosingEventArgs e)
    {
        // Closing the window keeps the relay running in the tray; Quit from the tray really exits.
        if (!_allowClose && e.CloseReason == CloseReason.UserClosing)
        {
            e.Cancel = true;
            Hide();
            _tray.ShowBalloonTip(2000, "TapRelay PC Relay", "Still running in the tray.", ToolTipIcon.Info);
            return;
        }
        _tray.Visible = false;
        base.OnFormClosing(e);
    }
}
