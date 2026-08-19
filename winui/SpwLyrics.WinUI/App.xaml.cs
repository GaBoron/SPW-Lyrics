using Windows.ApplicationModel;
using Windows.ApplicationModel.Activation;
using Windows.Foundation;
using Windows.Foundation.Collections;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Controls.Primitives;
using Microsoft.UI.Xaml.Data;
using Microsoft.UI.Xaml.Input;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Navigation;
using Microsoft.UI.Xaml.Shapes;

// To learn more about WinUI, the WinUI project structure,
// and more about our project templates, see: http://aka.ms/winui-project-info.

namespace SpwLyrics_WinUI;

/// <summary>
/// Provides application-specific behavior to supplement the default Application class.
/// </summary>
public partial class App : Application
{
    private Window? _window;
    private BridgeActivationListener? _activationListener;
    internal static BridgeClient Bridge { get; private set; } = BridgeClient.FromCommandLine([]);

    /// <summary>
    /// Initializes the singleton application object.  This is the first line of authored code
    /// executed, and as such is the logical equivalent of main() or WinMain().
    /// </summary>
    public App()
    {
        InitializeComponent();
        UnhandledException += (_, eventArgs) => StartupDiagnostics.Failure(eventArgs.Exception);
    }

    /// <summary>
    /// Invoked when the application is launched.
    /// </summary>
    /// <param name="args">Details about the launch request and process.</param>
    protected override void OnLaunched(Microsoft.UI.Xaml.LaunchActivatedEventArgs args)
    {
        try
        {
            Bridge = BridgeClient.FromCommandLine(Environment.GetCommandLineArgs());
            var window = new MainWindow();
            _window = window;
            window.ActivateForInput();
            _activationListener = new BridgeActivationListener(Bridge, window.DispatcherQueue, window.ActivateForInput);
            window.Closed += (_, _) =>
            {
                _activationListener?.Dispose();
                _activationListener = null;
            };
            _activationListener.Start();
        }
        catch (Exception exception)
        {
            StartupDiagnostics.Failure(exception);
            throw;
        }
    }
}
