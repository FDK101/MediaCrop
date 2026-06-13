using System.Windows;
using System.Windows.Input;
using System.Windows.Media;

namespace MediaCrop.Controls;

/// <summary>Two-handle trim slider with playback position indicator.</summary>
public class RangeSliderControl : FrameworkElement
{
    static readonly Color ActiveCol   = Color.FromRgb(0x63, 0x66, 0xF1);
    static readonly Color InactiveCol = Color.FromRgb(0x37, 0x41, 0x51);
    static readonly Color ThumbCol    = Color.FromRgb(0x63, 0x66, 0xF1);
    static readonly Color PosCol      = Color.FromRgb(0x06, 0xB6, 0xD4);

    const int ThumbW = 10;
    const int ThumbH = 22;
    const int TrackH =  6;
    const int Margin = 10;

    private long    _duration = 1;
    private long    _start    = 0;
    private long    _end      = 1;
    private long    _position = 0;
    private string? _drag;    // "start" | "end" | "seek"

    public event Action<long>? StartChanged;
    public event Action<long>? EndChanged;
    public event Action<long>? SeekRequested;

    public RangeSliderControl()
    {
        Height = 36;
    }

    // ── Public setters ────────────────────────────────────────────────────────

    public void SetDuration(long ms) { _duration = Math.Max(1, ms); _end = _duration; InvalidateVisual(); }
    public void SetTrim(long start, long end) { _start = start; _end = end; InvalidateVisual(); }
    public void SetPosition(long ms) { _position = ms; InvalidateVisual(); }

    // ── Geometry ─────────────────────────────────────────────────────────────

    protected override HitTestResult? HitTestCore(PointHitTestParameters p)
        => new PointHitTestResult(this, p.HitPoint);

    private Rect TrackRect()
    {
        double y = (ActualHeight - TrackH) / 2;
        return new Rect(Margin, y, ActualWidth - 2 * Margin, TrackH);
    }

    private double MsToX(long ms)
    {
        var tr = TrackRect();
        if (tr.Width < 1) return tr.X;
        return tr.X + (double)ms / _duration * tr.Width;
    }

    private long XToMs(double x)
    {
        var tr = TrackRect();
        if (tr.Width < 1) return 0;
        double ratio = (x - tr.X) / tr.Width;
        return (long)(Math.Clamp(ratio, 0, 1) * _duration);
    }

    // ── Render ────────────────────────────────────────────────────────────────

    protected override void OnRender(DrawingContext dc)
    {
        var tr = TrackRect();

        dc.DrawRoundedRectangle(new SolidColorBrush(InactiveCol), null, tr, 3, 3);

        double sx = MsToX(_start), ex = MsToX(_end);
        dc.DrawRectangle(new SolidColorBrush(ActiveCol), null,
            new Rect(sx, tr.Y, ex - sx, tr.Height));

        double px = MsToX(_position);
        dc.DrawLine(new Pen(new SolidColorBrush(PosCol), 2),
            new Point(px, tr.Y - 5), new Point(px, tr.Bottom + 5));

        double cy = ActualHeight / 2;
        foreach (double mx in new[] { sx, ex })
        {
            dc.DrawRoundedRectangle(new SolidColorBrush(ThumbCol), null,
                new Rect(mx - ThumbW / 2.0, cy - ThumbH / 2.0, ThumbW, ThumbH), 4, 4);
        }
    }

    // ── Mouse ─────────────────────────────────────────────────────────────────

    protected override void OnMouseLeftButtonDown(MouseButtonEventArgs e)
    {
        double x = e.GetPosition(this).X;
        double sx = MsToX(_start), ex = MsToX(_end);
        double tw = ThumbW + 5;
        if (Math.Abs(x - sx) <= tw)       { _drag = "start"; CaptureMouse(); }
        else if (Math.Abs(x - ex) <= tw)  { _drag = "end";   CaptureMouse(); }
        else
        {
            _drag = "seek";
            SeekRequested?.Invoke(XToMs(x));
        }
        e.Handled = true;
    }

    protected override void OnMouseMove(MouseEventArgs e)
    {
        if (_drag == null || e.LeftButton == MouseButtonState.Released) return;
        long ms = XToMs(e.GetPosition(this).X);
        if (_drag == "start")
        {
            long ns = Math.Max(0, Math.Min(ms, _end - 33));
            if (ns != _start) { _start = ns; StartChanged?.Invoke(ns); InvalidateVisual(); }
        }
        else if (_drag == "end")
        {
            long ne = Math.Min(_duration, Math.Max(ms, _start + 33));
            if (ne != _end) { _end = ne; EndChanged?.Invoke(ne); InvalidateVisual(); }
        }
        else if (_drag == "seek")
        {
            SeekRequested?.Invoke(ms);
        }
    }

    protected override void OnMouseLeftButtonUp(MouseButtonEventArgs e)
    {
        _drag = null;
        ReleaseMouseCapture();
    }
}
