using System.Collections.ObjectModel;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;

namespace SpwLyrics_WinUI;

public sealed partial class BatchPage : Page
{
    private readonly ObservableCollection<BatchItemViewModel> _items = [];
    private CancellationTokenSource? _polling;
    private string _state = "idle";
    private bool _requestInProgress;
    private int _libraryTotal;

    public BatchPage()
    {
        InitializeComponent();
        NavigationCacheMode = Microsoft.UI.Xaml.Navigation.NavigationCacheMode.Required;
        TrackList.ItemsSource = _items;
    }

    private async void Page_Loaded(object sender, RoutedEventArgs e)
    {
        _polling?.Cancel();
        _polling = new CancellationTokenSource();
        await RefreshAsync(showError: true, _polling.Token);
        _ = PollAsync(_polling.Token);
    }

    private void Page_Unloaded(object sender, RoutedEventArgs e)
    {
        _polling?.Cancel();
        _polling?.Dispose();
        _polling = null;
    }

    private async Task PollAsync(CancellationToken cancellationToken)
    {
        while (!cancellationToken.IsCancellationRequested)
        {
            try
            {
                await Task.Delay(TimeSpan.FromMilliseconds(_state == "running" ? 650 : 1500), cancellationToken);
                if (!_requestInProgress) await RefreshAsync(showError: false, cancellationToken);
            }
            catch (OperationCanceledException) { return; }
        }
    }

    private async Task RefreshAsync(bool showError, CancellationToken cancellationToken)
    {
        try
        {
            var response = await App.Bridge.SendAsync("batch_state", cancellationToken: cancellationToken);
            if (response.Ok && response.Batch is not null) ApplySnapshot(response.Batch);
            else if (showError)
            {
                StateText.Text = "读取失败";
                ShowStatus(false, response.Message);
            }
        }
        catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested) { }
        catch (Exception error)
        {
            if (showError)
            {
                StateText.Text = "连接失败";
                ShowStatus(false, error.Message);
            }
        }
    }

    private async void StartButton_Click(object sender, RoutedEventArgs e)
    {
        var selectedKeys = _items.Where(item => item.IsSelected).Select(item => item.Key).ToArray();
        if (selectedKeys.Length == 0)
        {
            ShowStatus(false, "请至少勾选一首歌曲。");
            return;
        }
        await RunActionAsync("batch_start", IncludeCachedCheckBox.IsChecked == true, selectedKeys);
    }

    private async void PauseButton_Click(object sender, RoutedEventArgs e) =>
        await RunActionAsync(_state == "paused" ? "batch_resume" : "batch_pause");

    private async void RetryButton_Click(object sender, RoutedEventArgs e) => await RunActionAsync("batch_retry");

    private async void CancelButton_Click(object sender, RoutedEventArgs e) => await RunActionAsync("batch_cancel");

    private async Task RunActionAsync(
        string action,
        bool includeCached = false,
        IReadOnlyCollection<string>? selectedKeys = null)
    {
        if (_requestInProgress) return;
        _requestInProgress = true;
        SetCommandsEnabled(false);
        try
        {
            var response = await App.Bridge.SendAsync(action, includeCached: includeCached, selectedKeys: selectedKeys);
            if (response.Ok && response.Batch is not null) ApplySnapshot(response.Batch);
            else ShowStatus(false, response.Message);
        }
        catch (Exception error) { ShowStatus(false, error.Message); }
        finally
        {
            _requestInProgress = false;
            UpdateCommands();
        }
    }

    private void ApplySnapshot(BatchUiSnapshot snapshot)
    {
        _state = snapshot.State;
        _libraryTotal = snapshot.Total;
        var existing = _items.ToDictionary(item => item.Key);
        if (_items.Count != snapshot.Items.Count || snapshot.Items.Any(item => !existing.ContainsKey(item.Key)))
        {
            _items.Clear();
            foreach (var item in snapshot.Items) _items.Add(new BatchItemViewModel(item));
        }
        else
        {
            foreach (var item in snapshot.Items) existing[item.Key].Update(item);
        }

        TotalText.Text = $"{snapshot.Selected} / {snapshot.Total}";
        ProcessedText.Text = snapshot.Processed.ToString();
        CompletedText.Text = snapshot.Completed.ToString();
        CachedText.Text = snapshot.Cached.ToString();
        FailedText.Text = snapshot.Failed.ToString();
        StateText.Text = snapshot.StateLabel;
        OverallProgress.IsIndeterminate = snapshot.State == "running" && snapshot.Selected == 0;
        OverallProgress.Maximum = 1;
        OverallProgress.Value = snapshot.Progress;
        OverallProgressText.Text = $"{snapshot.Progress:P0}";
        ActiveRing.IsActive = snapshot.State == "running";
        EmptyState.Visibility = snapshot.Total == 0 ? Visibility.Visible : Visibility.Collapsed;
        var active = snapshot.State is "running" or "paused";
        foreach (var item in _items) item.CanSelect = !active;
        SelectAllCheckBox.IsEnabled = !active;
        UpdateSelectionIndicator();
        RetryButton.IsEnabled = snapshot.Failed > 0 && snapshot.State is not "running" and not "paused";
        UpdateCommands();
    }

    private void UpdateCommands()
    {
        if (_requestInProgress) return;
        var active = _state is "running" or "paused";
        StartButton.IsEnabled = !active && _items.Any(item => item.IsSelected);
        StartButton.Content = _state is "completed" or "cancelled" ? "重新处理" : "开始处理";
        PauseButton.IsEnabled = active;
        PauseButton.Content = _state == "paused" ? "继续" : "暂停";
        CancelButton.IsEnabled = active;
        IncludeCachedCheckBox.IsEnabled = !active;
        SelectAllCheckBox.IsEnabled = !active;
        foreach (var item in _items) item.CanSelect = !active;
        if (!active) RetryButton.IsEnabled = _items.Any(item => item.State == "failed");
    }

    private void SetCommandsEnabled(bool enabled)
    {
        StartButton.IsEnabled = enabled;
        PauseButton.IsEnabled = enabled;
        RetryButton.IsEnabled = enabled;
        CancelButton.IsEnabled = enabled;
        IncludeCachedCheckBox.IsEnabled = enabled;
        SelectAllCheckBox.IsEnabled = enabled;
        foreach (var item in _items) item.CanSelect = enabled;
    }

    private void SelectAllCheckBox_Click(object sender, RoutedEventArgs e)
    {
        var selected = SelectAllCheckBox.IsChecked != false;
        foreach (var item in _items) item.IsSelected = selected;
        UpdateSelectionIndicator();
        UpdateCommands();
    }

    private void ItemSelection_Click(object sender, RoutedEventArgs e)
    {
        UpdateSelectionIndicator();
        UpdateCommands();
    }

    private void UpdateSelectionIndicator()
    {
        var selected = _items.Count(item => item.IsSelected);
        TotalText.Text = $"{selected} / {_libraryTotal}";
        SelectAllCheckBox.IsChecked = selected switch
        {
            0 => false,
            _ when selected == _items.Count => true,
            _ => null,
        };
    }

    private void ShowStatus(bool ok, string message)
    {
        StatusBar.Severity = ok ? InfoBarSeverity.Success : InfoBarSeverity.Warning;
        StatusBar.Message = string.IsNullOrWhiteSpace(message) ? "操作未完成。" : message;
        StatusBar.IsOpen = true;
    }
}
