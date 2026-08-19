using Microsoft.UI.Xaml;
using Microsoft.UI.Windowing;
using Windows.Graphics;

// To learn more about WinUI, the WinUI project structure,
// and more about our project templates, see: http://aka.ms/winui-project-info.

namespace SpwLyrics_WinUI;

/// <summary>
/// The application window. This hosts a Frame that displays pages. Add your
/// UI and logic to MainPage.xaml / MainPage.xaml.cs instead of here so you
/// can use Page features such as navigation events and the Loaded lifecycle.
/// </summary>
public sealed partial class MainWindow : Window
{
    public MainWindow()
    {
        StartupDiagnostics.Stage("MainWindow.InitializeComponent.begin");
        InitializeComponent();
        StartupDiagnostics.Stage("MainWindow.InitializeComponent.complete");

        AppWindow.SetIcon(Path.Combine(AppContext.BaseDirectory, "Assets", "AppIcon.ico"));
        StartupDiagnostics.Stage("MainWindow.SetIcon.complete");
        AppWindow.Resize(new SizeInt32(1180, 760));
        StartupDiagnostics.Stage("MainWindow.Resize.complete");

        // Navigate the root frame to the main page on startup.
        StartupDiagnostics.Stage($"MainWindow.Navigate.complete={RootFrame.Navigate(typeof(MainPage))}");
    }

    public void ActivateForInput()
    {
        if (AppWindow.Presenter is OverlappedPresenter presenter && presenter.State == OverlappedPresenterState.Minimized)
        {
            presenter.Restore(activateWindow: true);
        }
        Activate();
        AppWindow.Show(activateWindow: true);
        DispatcherQueue.TryEnqueue(() => RootFrame.Focus(Microsoft.UI.Xaml.FocusState.Programmatic));
    }
}
