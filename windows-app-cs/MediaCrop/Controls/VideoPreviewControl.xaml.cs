using System.Windows;
using System.Windows.Controls;
using MediaCrop.Models;

namespace MediaCrop.Controls;

public partial class VideoPreviewControl : UserControl
{
    public event Action<CropRect>? CropChanged;
    public event Action<string>?   MediaError;

    public MediaElement VideoPlayer => Player;

    private long _startMs;

    public VideoPreviewControl()
    {
        InitializeComponent();
        Overlay.CropChanged += crop => CropChanged?.Invoke(crop);
    }

    public void Load(VideoInfo info, long startMs)
    {
        _startMs = startMs;
        Overlay.SetVideoInfo(info);
        Player.Source = new Uri(info.Path, UriKind.Absolute);
        // MediaOpened will fire once the file is ready; we seek + pause there.
        Player.Play();
    }

    public void SetAspect(double aspect)
    {
        Overlay.SetAspect(aspect);
    }

    public CropRect GetCrop() => Overlay.GetCrop();

    private void Player_MediaOpened(object sender, RoutedEventArgs e)
    {
        Player.Pause();
        Player.Position = TimeSpan.FromMilliseconds(_startMs);
        Overlay.InvalidateVisual();
    }

    private void Player_MediaFailed(object? sender, ExceptionRoutedEventArgs e)
    {
        MediaError?.Invoke(e.ErrorException?.Message ?? "Unknown media error");
    }
}
