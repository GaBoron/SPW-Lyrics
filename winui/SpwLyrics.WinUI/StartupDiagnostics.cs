namespace SpwLyrics_WinUI;

internal static class StartupDiagnostics
{
    private static readonly string DirectoryPath = System.IO.Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
        "SPW Lyrics");

    internal static void Failure(Exception exception) => Append(
        "winui-crash.log",
        $"[{DateTimeOffset.Now:O}]{Environment.NewLine}{exception}{Environment.NewLine}{Environment.NewLine}");

    internal static void Stage(string stage) => Append(
        "winui-startup.log",
        $"[{DateTimeOffset.Now:O}] {stage}{Environment.NewLine}");

    private static void Append(string fileName, string content)
    {
        try
        {
            Directory.CreateDirectory(DirectoryPath);
            File.AppendAllText(System.IO.Path.Combine(DirectoryPath, fileName), content);
        }
        catch
        {
            // Startup diagnostics must never affect the companion process.
        }
    }
}
