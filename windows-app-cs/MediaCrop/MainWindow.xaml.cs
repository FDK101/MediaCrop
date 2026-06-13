using System.Windows;
using MediaCrop.Models;
using MediaCrop.Views;

namespace MediaCrop;

public partial class MainWindow : Window
{
    private readonly HomeScreen   _home;
    private readonly EditorScreen _editor;

    public MainWindow()
    {
        InitializeComponent();
        _home   = new HomeScreen();
        _editor = new EditorScreen();

        _home.VideoSelected    += OpenEditor;
        _editor.BackRequested  += GoHome;

        MainContent.Content = _home;
    }

    private void OpenEditor(VideoInfo info)
    {
        _editor.LoadVideo(info);
        MainContent.Content = _editor;
    }

    private void GoHome()
    {
        MainContent.Content = _home;
    }
}
