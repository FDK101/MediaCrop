using System.IO;
using System.Windows;
using System.Windows.Controls;
using MediaCrop.Helpers;
using MediaCrop.Models;
using Microsoft.Win32;

namespace MediaCrop.Views;

public partial class HomeScreen : UserControl
{
    private readonly AppSettings _settings;
    private bool _suppressChange;

    public event Action<VideoInfo>? VideoSelected;

    private static readonly string VideoFilter =
        "Video Files|*.mp4;*.mov;*.mkv;*.avi;*.m4v;*.wmv;*.webm;*.flv;*.ts;*.mts|All Files|*.*";

    public HomeScreen()
    {
        InitializeComponent();
        _settings = AppSettings.Load();

        _suppressChange    = true;
        StartTimeTxt.Text  = _settings.DefaultStartMs.ToString();
        _suppressChange    = false;
    }

    // ── File pick ─────────────────────────────────────────────────────────────

    private void OnPickFile(object sender, RoutedEventArgs e)
    {
        var dlg = new OpenFileDialog { Filter = VideoFilter, Title = "Open Video" };
        if (dlg.ShowDialog() == true)
            TryLoad(dlg.FileName);
    }

    // ── Drag-and-drop ─────────────────────────────────────────────────────────

    private void OnDragEnter(object sender, DragEventArgs e)
    {
        e.Effects = e.Data.GetDataPresent(DataFormats.FileDrop)
            ? DragDropEffects.Copy
            : DragDropEffects.None;
        e.Handled = true;
    }

    private void OnDrop(object sender, DragEventArgs e)
    {
        if (e.Data.GetData(DataFormats.FileDrop) is string[] files && files.Length > 0)
            TryLoad(files[0]);
    }

    // ── Load ──────────────────────────────────────────────────────────────────

    private void TryLoad(string path)
    {
        StatusLabel.Text = "Reading file…";
        try
        {
            var info = VideoProcessor.ProbeVideo(path);
            StatusLabel.Text = "";
            VideoSelected?.Invoke(info);
        }
        catch (Exception ex)
        {
            StatusLabel.Text = ex.Message;
        }
    }

    // ── Default start setting ─────────────────────────────────────────────────

    private void StartTimeTxt_TextChanged(object sender, TextChangedEventArgs e)
    {
        if (_suppressChange) return;
        if (int.TryParse(StartTimeTxt.Text, out int val) && val >= 0)
        {
            _settings.DefaultStartMs = val;
            _settings.Save();
        }
    }
}
