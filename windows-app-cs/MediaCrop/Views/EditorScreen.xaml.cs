using System.Diagnostics;
using System.IO;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Threading;
using MediaCrop.Helpers;
using MediaCrop.Models;

namespace MediaCrop.Views;

public partial class EditorScreen : UserControl
{
    public event Action? BackRequested;

    private VideoInfo? _info;
    private long       _trimStart;
    private long       _trimEnd;
    private bool       _isPlaying;
    private string     _activePreset = "9:16";

    private readonly DispatcherTimer _pollTimer;
    private CancellationTokenSource? _exportCts;

    public EditorScreen()
    {
        InitializeComponent();

        _pollTimer          = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(100) };
        _pollTimer.Tick    += OnPollTick;

        Preview.CropChanged += _ => { /* stored in overlay; read at export */ };
        Preview.MediaError  += msg => MessageBox.Show(msg, "Media Error", MessageBoxButton.OK, MessageBoxImage.Warning);

        TrimPanel.TrimStartChanged += ms => _trimStart = ms;
        TrimPanel.TrimEndChanged   += ms => _trimEnd   = ms;
        TrimPanel.SeekRequested    += Seek;

        // Default preset styling
        UpdatePresetStyles();
    }

    // ── Load ──────────────────────────────────────────────────────────────────

    public void LoadVideo(VideoInfo info)
    {
        _info = info;

        var settings    = AppSettings.Load();
        _trimStart      = Math.Min(settings.DefaultStartMs, Math.Max(0, info.DurationMs - 33));
        _trimEnd        = info.DurationMs;

        InfoLabel.Text  = $"{info.DisplayWidth}×{info.DisplayHeight}  " +
                          $"{info.DurationMs / 60000}:{(info.DurationMs % 60000) / 1000:D2}";

        TrimPanel.SetDuration(info.DurationMs);
        TrimPanel.SetTrim(_trimStart, _trimEnd);

        Preview.Load(info, _trimStart);

        _activePreset = "9:16";
        Preview.SetAspect(9.0 / 16.0);
        UpdatePresetStyles();

        _pollTimer.Start();
    }

    // ── Polling ───────────────────────────────────────────────────────────────

    private void OnPollTick(object? sender, EventArgs e)
    {
        var player = Preview.VideoPlayer;
        long pos   = (long)player.Position.TotalMilliseconds;
        TrimPanel.SetPosition(pos);

        bool playing = _isPlaying;
        BtnPlay.Content = playing ? "⏸" : "▶";

        if (playing && pos >= _trimEnd)
        {
            player.Pause();
            _isPlaying = false;
            Seek(_trimStart);
        }
    }

    // ── Preset buttons ────────────────────────────────────────────────────────

    private void OnPreset916(object s, RoutedEventArgs e)    => ApplyPreset("9:16",  9.0 / 16.0);
    private void OnPresetSquare(object s, RoutedEventArgs e) => ApplyPreset("1:1",   1.0);
    private void OnPreset169(object s, RoutedEventArgs e)    => ApplyPreset("16:9",  16.0 / 9.0);

    private void OnPresetAdb(object s, RoutedEventArgs e)
    {
        try
        {
            var (w, h) = AdbHelper.GetDeviceScreenSize();
            _activePreset = "ADB";
            UpdatePresetStyles();
            Preview.SetAspect((double)w / h);
        }
        catch (Exception ex)
        {
            MessageBox.Show(ex.Message, "ADB Error", MessageBoxButton.OK, MessageBoxImage.Warning);
        }
    }

    private void ApplyPreset(string name, double aspect)
    {
        _activePreset = name;
        UpdatePresetStyles();
        Preview.SetAspect(aspect);
    }

    private void UpdatePresetStyles()
    {
        // TryFindResource is safe to call before the element is in the visual tree
        if (TryFindResource("PresetButtonActive") is not Style active)   return;
        if (TryFindResource("PresetButton")       is not Style inactive) return;

        Btn916.Style    = _activePreset == "9:16"  ? active : inactive;
        BtnSquare.Style = _activePreset == "1:1"   ? active : inactive;
        Btn169.Style    = _activePreset == "16:9"  ? active : inactive;
        BtnAdb.Opacity  = _activePreset == "ADB"   ? 1.0 : 0.75;
    }

    // ── Playback ──────────────────────────────────────────────────────────────

    private void BtnPlay_Click(object s, RoutedEventArgs e)
    {
        var player = Preview.VideoPlayer;
        if (_isPlaying)
        {
            player.Pause();
            _isPlaying = false;
        }
        else
        {
            if ((long)player.Position.TotalMilliseconds >= _trimEnd)
                Seek(_trimStart);
            player.Play();
            _isPlaying = true;
        }
        BtnPlay.Content = _isPlaying ? "⏸" : "▶";
    }

    private void BtnPrev_Click(object s, RoutedEventArgs e) => Seek(_trimStart);
    private void BtnNext_Click(object s, RoutedEventArgs e) => Seek(_trimEnd);

    private void Seek(long ms)
    {
        Preview.VideoPlayer.Position = TimeSpan.FromMilliseconds(ms);
        TrimPanel.SetPosition(ms);
    }

    // ── Set IN / OUT ──────────────────────────────────────────────────────────

    private void BtnSetIn_Click(object s, RoutedEventArgs e)
    {
        long pos = (long)Preview.VideoPlayer.Position.TotalMilliseconds;
        _trimStart = Math.Max(0, Math.Min(pos, _trimEnd - 33));
        TrimPanel.SetTrim(_trimStart, _trimEnd);
    }

    private void BtnSetOut_Click(object s, RoutedEventArgs e)
    {
        long pos = (long)Preview.VideoPlayer.Position.TotalMilliseconds;
        _trimEnd = Math.Max(_trimStart + 33, Math.Min(pos, _info?.DurationMs ?? pos));
        TrimPanel.SetTrim(_trimStart, _trimEnd);
    }

    // ── Back ──────────────────────────────────────────────────────────────────

    private void BtnBack_Click(object s, RoutedEventArgs e)
    {
        _pollTimer.Stop();
        _isPlaying = false;
        Preview.VideoPlayer.Stop();
        Preview.VideoPlayer.Source = null;
        BackRequested?.Invoke();
    }

    // ── Export ────────────────────────────────────────────────────────────────

    private async void BtnExport_Click(object s, RoutedEventArgs e)
    {
        if (_info == null) return;

        var outDir  = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.MyVideos), "Cropped");
        Directory.CreateDirectory(outDir);
        string outPath = Path.Combine(outDir, $"MediaCrop_{DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()}.mp4");

        BtnExport.IsEnabled = false;
        _exportCts          = new CancellationTokenSource();

        var dlg = new ExportProgressWindow { Owner = Window.GetWindow(this) };
        dlg.CancelRequested += () => _exportCts.Cancel();
        dlg.Show();

        var progress = new Progress<double>(f => dlg.SetProgress(f));

        try
        {
            await VideoProcessor.ExportAsync(
                _info, Preview.GetCrop(), _trimStart, _trimEnd,
                outPath, progress, _exportCts.Token);

            dlg.Close();
            var result = MessageBox.Show(
                $"Video saved to:\n{outPath}\n\nOpen folder?",
                "Export Complete",
                MessageBoxButton.YesNo,
                MessageBoxImage.Information);

            if (result == MessageBoxResult.Yes)
                Process.Start("explorer.exe", $"/select,\"{outPath}\"");
        }
        catch (OperationCanceledException)
        {
            dlg.Close();
        }
        catch (Exception ex)
        {
            dlg.Close();
            MessageBox.Show(ex.Message, "Export Failed", MessageBoxButton.OK, MessageBoxImage.Error);
        }
        finally
        {
            BtnExport.IsEnabled = true;
            _exportCts = null;
        }
    }
}
