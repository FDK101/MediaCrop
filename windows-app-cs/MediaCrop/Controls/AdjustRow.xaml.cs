using System.Windows;
using System.Windows.Controls;
using MediaCrop.Helpers;

namespace MediaCrop.Controls;

public partial class AdjustRow : UserControl
{
    private long _valueMs;

    /// <summary>Fires with the new absolute value (ms) when an adjust button is clicked.</summary>
    public event Action<long>? Adjusted;

    public AdjustRow()
    {
        InitializeComponent();
    }

    public void SetLabel(string label) => LabelText.Text = label;

    public void SetValue(long ms)
    {
        _valueMs      = ms;
        TimeText.Text = TimeFormatter.Format(ms);
    }

    private void Emit(long delta) => Adjusted?.Invoke(_valueMs + delta);

    private void BtnM1s_Click(object s, RoutedEventArgs e)  => Emit(-1000);
    private void BtnM100_Click(object s, RoutedEventArgs e) => Emit(-100);
    private void BtnM1_Click(object s, RoutedEventArgs e)   => Emit(-1);
    private void BtnP1_Click(object s, RoutedEventArgs e)   => Emit(1);
    private void BtnP100_Click(object s, RoutedEventArgs e) => Emit(100);
    private void BtnP1s_Click(object s, RoutedEventArgs e)  => Emit(1000);
}
