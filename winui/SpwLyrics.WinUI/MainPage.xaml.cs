using System.Collections.ObjectModel;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Input;
using Windows.System;

// To learn more about WinUI, the WinUI project structure,
// and more about our project templates, see: http://aka.ms/winui-project-info.

namespace SpwLyrics_WinUI;

/// <summary>
/// The main content page displayed inside the application window.
/// Add your UI logic, event handlers, and data binding here.
/// </summary>
public sealed partial class MainPage : Page
{
    private readonly ObservableCollection<ManualUiCandidate> _candidates = [];
    private readonly ObservableCollection<ManualUiPreviewLine> _preview = [];
    private int _previewGeneration;
    private bool _initialized;

    public MainPage()
    {
        InitializeComponent();
        CandidateList.ItemsSource = _candidates;
        PreviewList.ItemsSource = _preview;
    }

    private async void Page_Loaded(object sender, RoutedEventArgs e)
    {
        if (_initialized) return;
        _initialized = true;
        await RunAsync(async () =>
        {
            var response = await App.Bridge.SendAsync("state");
            SourceBox.ItemsSource = response.Sources;
            SourceBox.SelectedIndex = response.Sources.Count > 0 ? 0 : -1;
            if (response.Track is not null)
            {
                SearchBox.Text = response.Track.SuggestedKeywords;
                TrackSummary.Text = string.Join("  ·  ", new[] { response.Track.Title, response.Track.Artists, response.Track.Album }.Where(value => !string.IsNullOrWhiteSpace(value)));
            }
            ShowStatus(response.Ok, response.Message);
            if (response.Ok && response.Track is not null && !string.IsNullOrWhiteSpace(SearchBox.Text))
            {
                await SearchCoreAsync();
            }
        });
    }

    private async void SearchButton_Click(object sender, RoutedEventArgs e) => await SearchAsync();

    private async void SearchBox_KeyDown(object sender, KeyRoutedEventArgs e)
    {
        if (e.Key != VirtualKey.Enter) return;
        e.Handled = true;
        await SearchAsync();
    }

    private async Task SearchAsync() => await RunAsync(SearchCoreAsync);

    private async Task SearchCoreAsync()
    {
        var source = (SourceBox.SelectedItem as ManualUiSource)?.Id;
        var response = await App.Bridge.SendAsync("search", SearchBox.Text.Trim(), source);
        _candidates.Clear();
        foreach (var candidate in response.Candidates) _candidates.Add(candidate);
        _preview.Clear();
        CandidateList.SelectedItem = null;
        ApplyButton.IsEnabled = false;
        EmptyResults.Visibility = _candidates.Count == 0 ? Visibility.Visible : Visibility.Collapsed;
        ShowStatus(response.Ok, response.Message);
    }

    private async void CandidateList_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        var candidate = CandidateList.SelectedItem as ManualUiCandidate;
        ApplyButton.IsEnabled = candidate is not null;
        _preview.Clear();
        if (candidate is null) return;
        var generation = ++_previewGeneration;
        await RunAsync(async () =>
        {
            var response = await App.Bridge.SendAsync("preview", candidateKey: candidate.Key);
            if (generation != _previewGeneration) return;
            foreach (var line in response.Preview) _preview.Add(line);
            ShowStatus(response.Ok, response.Message);
        }, disableSearch: false);
    }

    private async void ApplyButton_Click(object sender, RoutedEventArgs e)
    {
        if (CandidateList.SelectedItem is not ManualUiCandidate candidate) return;
        await RunAsync(async () =>
        {
            var response = await App.Bridge.SendAsync("apply", candidateKey: candidate.Key);
            ShowStatus(response.Ok, response.Message);
        });
    }

    private async void LocalButton_Click(object sender, RoutedEventArgs e) => await RunAsync(async () =>
    {
        var response = await App.Bridge.SendAsync("local");
        ShowStatus(response.Ok, response.Message);
    });

    private async Task RunAsync(Func<Task> action, bool disableSearch = true)
    {
        BusyRing.IsActive = true;
        if (disableSearch) SearchButton.IsEnabled = false;
        try { await action(); }
        catch (Exception error) { ShowStatus(false, error.Message); }
        finally
        {
            BusyRing.IsActive = false;
            SearchButton.IsEnabled = true;
        }
    }

    private void ShowStatus(bool ok, string message)
    {
        if (string.IsNullOrWhiteSpace(message)) return;
        StatusBar.Severity = ok ? InfoBarSeverity.Success : InfoBarSeverity.Warning;
        StatusBar.Message = message;
        StatusBar.IsOpen = true;
    }
}
