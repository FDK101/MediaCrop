using System.Windows;

namespace MediaCrop.Views;

public partial class ExportProgressWindow : Window
{
    public event Action? CancelRequested;
    private bool _cancelled;

    public ExportProgressWindow()
    {
        InitializeComponent();
    }

    public void SetProgress(double fraction)
    {
        Bar.Value         = fraction * 100;
        StatusText.Text   = $"Exporting… {fraction * 100:F0}%";
    }

    private void CancelBtn_Click(object sender, RoutedEventArgs e)
    {
        _cancelled = true;
        CancelRequested?.Invoke();
        Close();
    }

    private void Window_Closing(object? sender, System.ComponentModel.CancelEventArgs e)
    {
        if (!_cancelled)
            CancelRequested?.Invoke();
    }
}
