using System.Windows;
using System.Windows.Controls;
using MediaCrop.Helpers;

namespace MediaCrop.Controls;

public partial class TrimControlsPanel : UserControl
{
    private long _duration   = 1;
    private long _trimStart  = 0;
    private long _trimEnd    = 1;
    private long _position   = 0;
    private bool _suspendSeekSlider;

    public event Action<long>? TrimStartChanged;
    public event Action<long>? TrimEndChanged;
    public event Action<long>? SeekRequested;

    public TrimControlsPanel()
    {
        InitializeComponent();

        PlayRow.SetLabel("PLAY");
        InRow.SetLabel("IN");
        OutRow.SetLabel("OUT");

        RangeSlider.StartChanged  += OnRangeStart;
        RangeSlider.EndChanged    += OnRangeEnd;
        RangeSlider.SeekRequested += ms => SeekRequested?.Invoke(ms);

        PlayRow.Adjusted += ms =>
        {
            ms = Math.Clamp(ms, 0, _duration);
            SeekRequested?.Invoke(ms);
        };

        InRow.Adjusted += ms =>
        {
            ms = Math.Max(0, Math.Min(ms, _trimEnd - 33));
            _trimStart = ms;
            InRow.SetValue(ms);
            RangeSlider.SetTrim(_trimStart, _trimEnd);
            RefreshLabels();
            TrimStartChanged?.Invoke(ms);
        };

        OutRow.Adjusted += ms =>
        {
            ms = Math.Max(_trimStart + 33, Math.Min(ms, _duration));
            _trimEnd = ms;
            OutRow.SetValue(ms);
            RangeSlider.SetTrim(_trimStart, _trimEnd);
            RefreshLabels();
            TrimEndChanged?.Invoke(ms);
        };
    }

    // ── Public setters ────────────────────────────────────────────────────────

    public void SetDuration(long ms)
    {
        _duration = Math.Max(1, ms);
        SeekSlider.Maximum = ms;
        RangeSlider.SetDuration(ms);
    }

    public void SetTrim(long start, long end)
    {
        _trimStart = start;
        _trimEnd   = end;
        RefreshLabels();
        RangeSlider.SetTrim(start, end);
        InRow.SetValue(start);
        OutRow.SetValue(end);
    }

    public void SetPosition(long ms)
    {
        _position = ms;
        PosLabel.Text = $"▶  {TimeFormatter.Format(ms)}";
        PlayRow.SetValue(ms);
        RangeSlider.SetPosition(ms);
        _suspendSeekSlider = true;
        SeekSlider.Value   = ms;
        _suspendSeekSlider = false;
    }

    // ── Seek slider ───────────────────────────────────────────────────────────

    private void SeekSlider_ValueChanged(object sender, RoutedPropertyChangedEventArgs<double> e)
    {
        if (!_suspendSeekSlider)
            SeekRequested?.Invoke((long)e.NewValue);
    }

    // ── Range slider callbacks ────────────────────────────────────────────────

    private void OnRangeStart(long ms)
    {
        _trimStart = ms;
        InRow.SetValue(ms);
        RefreshLabels();
        TrimStartChanged?.Invoke(ms);
    }

    private void OnRangeEnd(long ms)
    {
        _trimEnd = ms;
        OutRow.SetValue(ms);
        RefreshLabels();
        TrimEndChanged?.Invoke(ms);
    }

    // ── Labels ────────────────────────────────────────────────────────────────

    private void RefreshLabels()
    {
        InLabel.Text      = TimeFormatter.Format(_trimStart);
        OutLabel.Text     = TimeFormatter.Format(_trimEnd);
        DurLabel.Text     = TimeFormatter.Format(_trimEnd - _trimStart);
        ClipDurLabel.Text = TimeFormatter.Format(_trimEnd - _trimStart);
    }
}
