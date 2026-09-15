using System.ComponentModel;
using System.Runtime.CompilerServices;

namespace SpwLyrics_WinUI;

public sealed class BatchItemViewModel : INotifyPropertyChanged
{
    private string _state = "waiting";
    private string _stateLabel = "等待处理";
    private string _source = "";
    private string _quality = "";
    private string _message = "";
    private bool _isSelected = true;
    private bool _canSelect = true;
    private double _progress;
    private string _stage = "等待处理";

    public BatchItemViewModel(BatchUiItem item)
    {
        Key = item.Key;
        Title = item.Title;
        Artists = item.Artists;
        Album = item.Album;
        _isSelected = item.Selected;
        Update(item);
    }

    public string Key { get; }
    public string Title { get; }
    public string Artists { get; }
    public string Album { get; }
    public string State { get => _state; private set => Set(ref _state, value); }
    public string StateLabel { get => _stateLabel; private set => Set(ref _stateLabel, value); }
    public string Source { get => _source; private set => Set(ref _source, value); }
    public string Quality { get => _quality; private set => Set(ref _quality, value); }
    public string Message { get => _message; private set => Set(ref _message, value); }
    public bool IsSelected { get => _isSelected; set => Set(ref _isSelected, value); }
    public bool CanSelect { get => _canSelect; set => Set(ref _canSelect, value); }
    public double Progress { get => _progress; private set => Set(ref _progress, value); }
    public string Stage { get => _stage; private set => Set(ref _stage, value); }
    public string Detail => string.Join("  ·  ", new[] { Artists, Album }.Where(value => !string.IsNullOrWhiteSpace(value)));
    public string Result => string.Join("  ·  ", new[] { Source, Quality }.Where(value => !string.IsNullOrWhiteSpace(value)));
    public string StatusGlyph => State switch
    {
        "completed" => "\uE930",
        "failed" => "\uEA39",
        "cached" => "\uE73E",
        "excluded" => "\uE739",
        "searching" => "\uE895",
        "cancelled" => "\uE711",
        _ => "\uE823",
    };

    public void Update(BatchUiItem item)
    {
        State = item.State;
        StateLabel = item.StateLabel;
        Source = item.Source;
        Quality = item.Quality;
        Message = item.Message;
        Progress = item.Progress;
        Stage = item.Stage;
        OnPropertyChanged(nameof(Result));
        OnPropertyChanged(nameof(StatusGlyph));
    }

    public event PropertyChangedEventHandler? PropertyChanged;

    private void Set<T>(ref T field, T value, [CallerMemberName] string? propertyName = null)
    {
        if (EqualityComparer<T>.Default.Equals(field, value)) return;
        field = value;
        OnPropertyChanged(propertyName);
    }

    private void OnPropertyChanged([CallerMemberName] string? propertyName = null) =>
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(propertyName));
}
