using Microsoft.UI.Dispatching;

namespace SpwLyrics_WinUI;

internal sealed class BridgeActivationListener : IDisposable
{
    private readonly BridgeClient _bridge;
    private readonly DispatcherQueue _dispatcher;
    private readonly Action _activate;
    private readonly CancellationTokenSource _cancellation = new();

    public BridgeActivationListener(BridgeClient bridge, DispatcherQueue dispatcher, Action activate)
    {
        _bridge = bridge;
        _dispatcher = dispatcher;
        _activate = activate;
    }

    public void Start() => _ = ListenAsync(_cancellation.Token);

    private async Task ListenAsync(CancellationToken cancellationToken)
    {
        while (!cancellationToken.IsCancellationRequested && _bridge.IsConfigured)
        {
            try
            {
                var response = await _bridge.SendAsync("wait_activation", cancellationToken: cancellationToken);
                if (response.Activate) _dispatcher.TryEnqueue(() => _activate());
            }
            catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
            {
                return;
            }
            catch
            {
                try { await Task.Delay(TimeSpan.FromSeconds(1), cancellationToken); }
                catch (OperationCanceledException) { return; }
            }
        }
    }

    public void Dispose() => _cancellation.Cancel();
}
