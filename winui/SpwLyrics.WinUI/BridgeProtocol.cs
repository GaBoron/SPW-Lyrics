using System.Text.Json.Serialization;

namespace SpwLyrics_WinUI;

public sealed class ManualUiRequest
{
    public required string Token { get; init; }
    public required string Action { get; init; }
    public string? Keywords { get; init; }
    public string? Source { get; init; }
    public string? CandidateKey { get; init; }
}

public sealed class ManualUiResponse
{
    public bool Ok { get; init; }
    public string Message { get; init; } = "";
    public bool Activate { get; init; }
    public ManualUiTrack? Track { get; init; }
    public List<ManualUiSource> Sources { get; init; } = [];
    public List<ManualUiCandidate> Candidates { get; init; } = [];
    public List<ManualUiPreviewLine> Preview { get; init; } = [];
}

public sealed class ManualUiTrack
{
    public string Title { get; init; } = "";
    public string Artists { get; init; } = "";
    public string Album { get; init; } = "";
    public string SuggestedKeywords { get; init; } = "";
}

public sealed class ManualUiSource
{
    public string? Id { get; init; }
    public string Name { get; init; } = "";
}

public sealed class ManualUiCandidate
{
    public string Key { get; init; } = "";
    public string Source { get; init; } = "";
    public string Title { get; init; } = "";
    public string Artists { get; init; } = "";
    public string Album { get; init; } = "";
    public string Duration { get; init; } = "";
    public string Quality { get; init; } = "";
    public double Score { get; init; }
    [JsonIgnore] public string ScoreText => Score.ToString("0.000");
}

public sealed class ManualUiPreviewLine
{
    public string Main { get; init; } = "";
    public string? Secondary { get; init; }
    [JsonIgnore] public bool HasSecondary => !string.IsNullOrWhiteSpace(Secondary);
}
