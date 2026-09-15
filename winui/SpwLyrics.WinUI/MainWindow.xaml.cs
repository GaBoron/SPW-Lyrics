using Microsoft.UI.Xaml;
using Microsoft.UI.Windowing;
using Windows.Graphics;

// To learn more about WinUI, the WinUI project structure,
// and more about our project templates, see: http://aka.ms/winui-project-info.

namespace SpwLyrics_WinUI;

/// <summary>
/// Hosts the companion's manual-search and library batch-processing pages.
/// </summary>
public sealed partial class MainWindow : Window
{
    private bool _changingPage;

    public MainWindow(string? initialMode)
    {
        StartupDiagnostics.Stage("MainWindow.InitializeComponent.begin");
        InitializeComponent();
        StartupDiagnostics.Stage("MainWindow.InitializeComponent.complete");

        AppWindow.SetIcon(Path.Combine(AppContext.BaseDirectory, "Assets", "AppIcon.ico"));
        StartupDiagnostics.Stage("MainWindow.SetIcon.complete");
        AppWindow.Resize(new SizeInt32(1180, 760));
        StartupDiagnostics.Stage("MainWindow.Resize.complete");
        WindowPlacementStore.Restore(AppWindow);
        Closed += (_, _) => WindowPlacementStore.Save(AppWindow);

        Navigate(initialMode);
        StartupDiagnostics.Stage("MainWindow.Navigate.complete");
    }

    private void Navigation_SelectionChanged(Microsoft.UI.Xaml.Controls.NavigationView sender, Microsoft.UI.Xaml.Controls.NavigationViewSelectionChangedEventArgs args)
    {
        if (args.SelectedItemContainer?.Tag is string mode) Navigate(mode);
    }

    private void Navigate(string? mode)
    {
        if (_changingPage) return;
        _changingPage = true;
        var batch = mode == "batch";
        var pageType = batch ? typeof(BatchPage) : typeof(MainPage);
        try
        {
            Navigation.SelectedItem = batch ? BatchItem : ManualItem;
            if (RootFrame.CurrentSourcePageType != pageType) RootFrame.Navigate(pageType);
        }
        finally { _changingPage = false; }
    }

    public void ActivateForInput(string? mode = null)
    {
        if (!string.IsNullOrWhiteSpace(mode)) Navigate(mode);
        if (AppWindow.Presenter is OverlappedPresenter presenter && presenter.State == OverlappedPresenterState.Minimized)
        {
            presenter.Restore(activateWindow: true);
        }
        Activate();
        AppWindow.Show(activateWindow: true);
        DispatcherQueue.TryEnqueue(() => RootFrame.Focus(Microsoft.UI.Xaml.FocusState.Programmatic));
    }
}
