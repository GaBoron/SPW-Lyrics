using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Text.Json;

namespace SpwLyrics_WinUI;

public sealed class BridgeClient
{
    private static readonly JsonSerializerOptions JsonOptions = new(JsonSerializerDefaults.Web);
    private readonly int _port;
    private readonly string _token;

    private BridgeClient(int port, string token)
    {
        _port = port;
        _token = token;
    }

    public bool IsConfigured => _port is > 0 and <= 65535 && !string.IsNullOrWhiteSpace(_token);

    public static BridgeClient FromCommandLine(string[] args)
    {
        var port = 0;
        var token = "";
        for (var index = 0; index + 1 < args.Length; index++)
        {
            if (args[index] == "--port") int.TryParse(args[++index], out port);
            else if (args[index] == "--token") token = args[++index];
        }
        return new BridgeClient(port, token);
    }

    public async Task<ManualUiResponse> SendAsync(
        string action,
        string? keywords = null,
        string? source = null,
        string? candidateKey = null,
        CancellationToken cancellationToken = default)
    {
        if (!IsConfigured) return new ManualUiResponse { Message = "此窗口必须由 SPW Lyrics 插件启动。" };
        using var client = new TcpClient(AddressFamily.InterNetwork);
        await client.ConnectAsync(IPAddress.Loopback, _port, cancellationToken);
        await using var stream = client.GetStream();
        await using var writer = new StreamWriter(stream, new UTF8Encoding(false), leaveOpen: true) { AutoFlush = true };
        using var reader = new StreamReader(stream, Encoding.UTF8, leaveOpen: true);
        var request = new ManualUiRequest
        {
            Token = _token,
            Action = action,
            Keywords = keywords,
            Source = source,
            CandidateKey = candidateKey,
        };
        await writer.WriteLineAsync(JsonSerializer.Serialize(request, JsonOptions).AsMemory(), cancellationToken);
        var line = await reader.ReadLineAsync(cancellationToken).AsTask()
            .WaitAsync(TimeSpan.FromSeconds(20), cancellationToken);
        return line is null
            ? new ManualUiResponse { Message = "插件未返回结果。" }
            : JsonSerializer.Deserialize<ManualUiResponse>(line, JsonOptions)
                ?? new ManualUiResponse { Message = "插件返回了无效结果。" };
    }
}
