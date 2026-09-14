using System.Text.Json;
using System.Text.Json.Serialization;
using Microsoft.UI.Windowing;
using Windows.Graphics;

namespace SpwLyrics_WinUI;

internal static class WindowPlacementStore
{
    private static readonly string FilePath = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
        "SPW Lyrics",
        "manual-window-position.json");

    internal static void Restore(AppWindow window)
    {
        try
        {
            if (!File.Exists(FilePath)) return;
            var placement = JsonSerializer.Deserialize<Placement>(File.ReadAllText(FilePath));
            if (placement is null) return;
            var requested = new PointInt32(placement.X, placement.Y);
            var area = DisplayArea.GetFromPoint(requested, DisplayAreaFallback.Nearest);
            var work = area.WorkArea;
            var x = Math.Clamp(requested.X, work.X, Math.Max(work.X, work.X + work.Width - window.Size.Width));
            var y = Math.Clamp(requested.Y, work.Y, Math.Max(work.Y, work.Y + work.Height - window.Size.Height));
            window.Move(new PointInt32(x, y));
        }
        catch
        {
            // Invalid or stale placement must never prevent the manual window from opening.
        }
    }

    internal static void Save(AppWindow window)
    {
        try
        {
            Directory.CreateDirectory(Path.GetDirectoryName(FilePath)!);
            File.WriteAllText(FilePath, JsonSerializer.Serialize(new Placement(window.Position.X, window.Position.Y)));
        }
        catch
        {
            // Window position persistence is optional.
        }
    }

    private sealed record Placement(
        [property: JsonPropertyName("x")] int X,
        [property: JsonPropertyName("y")] int Y);
}
