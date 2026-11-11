# 外部播放 API

面向开发 SPW 音源与播放器插件的作者。此接口把歌曲身份、播放进度和歌词交给 SPW Lyrics、AMLL into SPW、Dynamic Lyrics Island，支持网络音乐、电台及自定义播放器。

只需读取 SPW Lyrics 当前选中的歌词或调用搜索／重新匹配时，使用[当前歌词 API](current-lyrics-api.md)。

已经通过 SPW 播放接口提供歌曲和进度的插件会沿用宿主链路。持有私有播放器、未向宿主暴露这些信息的插件可通过此 API 补全。音源插件负责播放音频、切换播放所有权及暂停其他播放器。

## 最小接入

将 [ExternalPlayback.java](../api/ExternalPlayback.java) 复制到音源插件的 `src/main/java/dev/gaboron/spw/api/`。该文件仅依赖 JDK 17，保留版权声明并随插件附带本仓库的 [MIT 许可证](../LICENSE)。

```java
import dev.gaboron.spw.api.ExternalPlayback;

// 插件启动时创建，provider ID 使用自己的稳定名称。
var source = new ExternalPlayback("example.player");
var track = new ExternalPlayback.Track(
    "urn:example:track:123", "歌曲", "歌手", "专辑", "", 180_000);
source.publish(track, true, true, 0, 80, 1.0);

// 有来源歌词时可提供。没有时由 SPW Lyrics 按用户选择的策略匹配。
source.setSourceLyrics(ExternalPlayback.Lyrics.lrc("[00:01.00]第一句", 0));

// 在现有状态刷新任务中每秒调用，暂停时也继续保持租约。
source.touch();

// 插件停用、卸载时调用。
source.close();
```

`publish(track, active, playing, positionMs, volumePercent, playbackSpeed)` 在元数据、进度或播放状态变化时调用。`active` 表示本机当前选择这个来源，暂停时保持 `active=true`、`playing=false`。交还播放时发布 `active=false`。音源插件可使用已有 ticker 每秒 `publish` 或 `touch`，两次调用间隔应小于等于 5 秒。helper 自身不创建后台任务。

`touch()` 刷新租约并保留进度采样时间。停止更新超过 15 秒、失活或注销后，歌词插件恢复宿主来源。过期后再次 `publish` 或 `touch` 会作为新的激活，递增 `trackRevision`、清空旧歌词并重设激活时刻。`close()` 可重复调用，注销失败时可再次尝试。

### 歌曲身份与元数据

- provider ID 长度为 1–128，字符范围为英文字母、数字、点、下划线和连字符，首字符为字母或数字。每个 ID 在同一 SPW 进程中注册一个实例。
- `uri` 是每个歌曲条目稳定的绝对标识，最长 2048 字符。可使用 `urn:example:track:123`。它会参与歌词缓存身份，应去除账号凭据、查询参数和片段。带签名的播放 URL 保留在音源自己的播放器中。
- `title`、`artist`、`album` 各最长 4096 字符，未知值使用空字符串。`coverUrl` 可为空，或提供无用户名和密码的 HTTP(S) 图片地址，最长 4096 字符。
- 时长和进度使用毫秒，范围为 0–一年。时长 0 表示未知；已知时长时进度会被限制在时长内。音量范围为 0–100，有限播放倍率范围为 0.1–8。
- helper 每次注册生成新的会话 ID。URI 变化和 `active` 切换会递增 `trackRevision` 并清空该曲歌词。标题、歌手、时长与封面补全只递增普通 `revision`，会话身份保持稳定。

重播同一条目、希望重新建立选曲身份时，可先发布 `active=false` 再激活。同一 URI 的元数据修改代表补全同一录音；不同录音应使用不同的稳定 URI，以保持歌词缓存隔离。

异步加载歌词时捕获 `var expected = source.identity()`，完成后调用 `source.publishLyrics(expected, lyrics)`。条目或会话已经切换时返回 `false`，包括快速 A → B → A 切换。同步代码可直接调用 `setSourceLyrics(lyrics)`，传入 `null` 清除来源歌词。

## 接入控制

控制是可选能力。未提供回调或不支持的动作返回 `false`。`Controls` 在来源锁内执行，应立即完成校验或投递到已有命令队列。

```java
private ExternalPlayback external;

void connectPlayer() {
    external = new ExternalPlayback("example.player", (expected, command, value) -> {
        if (!java.util.Set.of("play", "pause", "seek").contains(command)) return false;
        playerCommands.execute(() -> {
            if (external.isCurrent(expected)) dispatchToPlayer(command, value);
        });
        return true;
    });
}
```

`playerCommands` 和 `dispatchToPlayer` 由音源插件提供。异步后端在实际执行前再次检查 `isCurrent(expected)`，避免排队期间切歌或交接后执行旧命令。返回 `true` 表示接受请求，最终播放状态由后续 `publish` 确认。

| 动作 | `value` |
| --- | --- |
| `play`、`pause`、`previous`、`next` | 0 |
| `seek` | 目标毫秒，限制在有效进度范围内 |
| `volume` | 0–100 |

## 来源歌词与匹配结果

来源原始歌词通过 `setSourceLyrics` 或身份防护的 `publishLyrics` 发布。SPW Lyrics 根据用户的自动替换策略使用、补全或替换它们，`clearLyrics` 撤回其提供的替换并恢复原始歌词。

`Lyrics(format, content, spl, offsetMs)` 的格式范围为 `ttml`、`lrc`、`empty`。`content` 与 `spl` 各最多 200 万字符，偏移范围为 ±600000 毫秒。正偏移使歌词出现时间后移。

- `lrc` 的 `content` 保存原始歌词，`spl` 保存可供渲染的 LRC / SPW 扩展 LRC。普通 LRC 可直接使用相同内容，`Lyrics.lrc` 会自动填写两个字段。
- `ttml` 的 `content` 保存 TTML，`spl` 提供相同歌词的 LRC / SPW 扩展 LRC。AMLL 优先读取 TTML，灵动岛读取 `spl`。仅提供 TTML 时，灵动岛等待 SPW Lyrics 补全 `spl` 后显示歌词。
- `empty` 使用空的 `content` 和 `spl`，表示明确选择空歌词或纯音乐。`null` 表示来源暂未提供歌词，可触发缺失歌词匹配。

逐字 SPW 扩展 LRC 示例。相同起始时间的后续行表示翻译，行末方括号表示结束时间。

```text
[00:01.000]<00:01.000>第<00:01.500>一句[00:03.000]
[00:01.000]First line[00:03.000]
```

## JMX v1 契约

跨插件调用通过同一 JVM 的 `ManagementFactory.getPlatformMBeanServer()` 完成。数据仅使用 JDK 基础类型与 `CompositeData`，各插件可独立打包 helper，无需共享插件类加载器。

### 发现与选择

新来源注册名称为 `dev.gaboron.spw:type=ExternalPlayback,version=1,provider=<quoted ID>`，其中 ID 使用 `ObjectName.quote`。消费者查询该类型与版本的所有 MBean，并兼容没有 `provider` 属性的旧单例名称。

消费者验证 `ProtocolVersion=1`，读取 `active=true`、身份有效且租约有效的快照。当多个来源同时激活时，按 `activatedAtNanos` 最大者选择，值相同则按 ObjectName 的 canonical name 排序选最大者。缺少该字段的旧来源按 0 处理。

完整身份由端点名称、provider、sessionId、trackRevision、URI 组成。取得快照后，歌词与控制调用必须继续使用捕获的端点与身份。旧单例缺少 `updatedAtNanos` 时沿用原有生命周期处理。

### 属性

| 属性 | 类型与含义 |
| --- | --- |
| `ProtocolVersion` | `int`，固定为 1 |
| `Snapshot` | `CompositeData`，字段见下表 |
| `SourceLyrics` | 原始来源歌词，缺失时为 `null`；旧来源可省略此属性 |
| `Lyrics` | 当前选中结果，存在 SPW Lyrics 替换时返回替换歌词，否则返回原始来源歌词 |

| Snapshot 字段 | JDK 类型 | 含义 |
| --- | --- | --- |
| `provider`、`sessionId`、`uri` | `String` | 来源、会话及条目标识 |
| `revision`、`trackRevision` | `Long` | 状态更新序号与选曲序号 |
| `active`、`playing` | `Boolean` | 本机来源所有权与正在播放状态 |
| `title`、`artist`、`album`、`coverUrl` | `String` | 元数据，未知时为空 |
| `durationMs`、`positionMs` | `Long` | 时长及采样时刻的进度 |
| `sampledAtNanos` | `Long` | 同 JVM `System.nanoTime()` 的进度采样时刻 |
| `volumePercent` | `Integer` | 音量 0–100 |
| `playbackSpeed` | `Double` | 播放倍率；旧来源缺失时按 1 处理 |
| `activatedAtNanos` | `Long` | 最近一次 inactive → active 的单调时刻 |
| `updatedAtNanos` | `Long` | 最近发布或心跳时刻，超过 15 秒失效 |

播放中按 `positionMs + elapsedMs × playbackSpeed` 外推进度，并限制在已知时长内。暂停时使用采样进度。

两个歌词属性均使用 `CompositeData`，包含 `sessionId: String`、`trackRevision: Long`、`uri: String`、`format: String`、`content: String`、`spl: String`、`offsetMs: Long`、`revision: Long`。原始歌词和选中歌词的 revision 分别单调递增。

### 操作

```java
boolean control(String sessionId, long trackRevision, String uri, String command, long value);
boolean submitLyrics(String sessionId, long trackRevision, String uri,
    String format, String content, String spl, long offsetMs);
boolean clearLyrics(String sessionId, long trackRevision, String uri);
```

每个操作在来源锁内核对活跃租约与完整曲目身份，过期或不匹配时返回 `false`。`submitLyrics` 设置歌词替换，`clearLyrics` 清除替换并恢复来源原始歌词。消费者通过操作签名兼容旧来源已有的播放控制及身份跳转方法。

来源卸载时关闭 publisher。注销前 helper 核对会话身份，仅释放自己注册的端点。主动切换到其他来源时，原来源应及时发布 `active=false`，并停止自己的音频。
