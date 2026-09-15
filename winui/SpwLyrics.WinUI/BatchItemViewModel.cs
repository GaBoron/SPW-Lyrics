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

    public BatchItemViewModel(BatchUiItem item)
    {
        Key = item.Key;
        Title = item.Title;
        Artists = item.Artists;
        Album = item.Album;
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
    public string Detail => string.Join("  ·  ", new[] { Artists, Album }.Where(value => !string.IsNullOrWhiteSpace(value)));
    public string Result => string.Join("  ·  ", new[] { Source, Quality, Message }.Where(value => !string.IsNullOrWhiteSpace(value)));
    public string StatusGlyph => State switch
    {
        "completed" => "\uE930",
        "failed" => "\uEA39",
        "cached" => "\uE73E",
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
        OnPropertyChanged(nameof(Result));
        OnPropertyChanged(nameof(StatusGlyph));
    }

    public event PropertyChangedEventHandler? PropertyChanged;

    private void Set(ref string field, string value, [CallerMemberName] string? propertyName = null)
    {
        if (field == value) return;
        field = value;
        OnPropertyChanged(propertyName);
    }

    private void OnPropertyChanged([CallerMemberName] string? propertyName = null) =>
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(propertyName));
}
