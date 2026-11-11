# 当前歌词与快捷操作 API

面向需要读取当前歌词或打开歌词搜索的 SPW 插件。接口使用同 JVM 的 JMX 和 JDK 类型，不依赖 SPW Lyrics 的内部类或共享插件类加载器。音源插件发布播放状态请使用[外部播放 API](external-playback-api.md)。

## 最小接入

将 [CurrentLyrics.java](../api/CurrentLyrics.java) 复制到调用插件的 `src/main/java/dev/gaboron/spw/api/`，需要 JDK 17。保留版权声明，并随插件附带本仓库的 [MIT 许可证](../LICENSE)。

```java
import dev.gaboron.spw.api.CurrentLyrics;

var snapshot = CurrentLyrics.read();
if (snapshot == null || !snapshot.lyrics().available()) {
    // 撤下之前显示的歌词；不能沿用上一首的内容。
} else {
    var identity = snapshot.identity();
    var lyrics = snapshot.lyrics();
    // 使用 lyrics.ttml() 或 lyrics.spl()，渲染时仅应用一次 lyrics.offsetMs()。
}

// 用户点击相关按钮时调用，传入按钮所对应的快照。
boolean queued = CurrentLyrics.openSearch(snapshot);
// 或：CurrentLyrics.rematch(snapshot);
```

helper 不创建线程、不缓存结果。调用方可在现有歌曲／歌词刷新任务中读取，并比较 `sessionId`、`trackRevision` 和 `revision`。收到 `null` 或不可用状态后，应清除之前的歌词。插件停用后端点注销，重新启用会生成不同的 `sessionId`。

## 快照与来源

读取的是已选中并交付播放的结果，不是搜索预览或预缓存。覆盖在线匹配、本地 TTML、已保存的内嵌歌词，以及 External Playback 外部音源。

交还 SPW 本地歌词时，仅提供本插件在当前歌曲加载回调中已经读到的本地内容；宿主管理、未经过本插件的歌词不会被猜测成当前结果。此时可能返回 `following_host`，正文为空。外部音源交还后读取该音源的原始歌词；租约失效、注销或交还播放后，立即以当前宿主歌曲重新判断，不保留旧外部歌词。

| `status` | 含义 |
| --- | --- |
| `ready` | 已有可用歌词 |
| `empty` | 明确的空歌词或纯音乐；正文为空，应清除旧歌词 |
| `loading` | 正在匹配，尚无可用正文 |
| `following_host` | 已交还原始来源，但没有可确认的正文 |
| `unavailable` | 当前结果已撤回或尚未建立 |

`updating=true` 表示仍有匹配或升级任务。它可以与 `ready` 同时出现，当前正文在升级完成前仍然有效。TTML 与 SPL 未叠加本 API 的单曲／外部来源偏移；正 `offsetMs` 表示延后显示。原文件自身的时间戳与 LRC offset 已在解析时处理。

## JMX v1 契约

端点为 `dev.gaboron.spw:type=Lyrics,version=1`。仅在同 JVM 的平台 MBeanServer 内注册，不启动网络 JMX 服务。

| 属性 | 类型 |
| --- | --- |
| `ProtocolVersion` | `Integer`，固定为 1 |
| `Active` | `Boolean`，停用时为 false |
| `SessionId` | `String`，本次启用的唯一身份 |
| `Snapshot` | `CompositeData` 或 `null`，一次读取包含身份、正文与状态 |

`Snapshot` 字段如下，字符串缺失值为空，未知时长为 0。

| 字段 | JDK 类型与含义 |
| --- | --- |
| `sessionId` | `String`，端点启用身份 |
| `revision` | `Long`，快照内容或状态变化时递增 |
| `trackRevision`、`trackKey` | `Long`、`String`，歌曲身份令牌，操作时原样传回 |
| `trackId`、`path`、`title`、`artist`、`album` | `String`，当前歌曲标识与元数据 |
| `durationMs` | `Long`，时长（毫秒） |
| `playbackProvider`、`playbackUri`、`playbackSessionId`、`playbackObjectName` | `String`，外部音源完整身份；宿主歌曲为空 |
| `playbackTrackRevision` | `Long`，外部选曲序号；宿主歌曲为 0 |
| `origin` | `String`：`plugin`、`host` 或 `external` |
| `source`、`sourceName` | `String`，歌词来源枚举标识与显示名称；无正文时为空 |
| `format` | `String`，`ttml`、`lrc`、`plain` 或 `empty`；无正文时为空 |
| `ttml`、`spl` | `String`，原始 TTML（如有）和普通／SPW 扩展 LRC；`plain` 时 SPL 为普通文本 |
| `offsetMs` | `Long`，合并后的有效偏移，限制在 ±600000 毫秒 |
| `trackOffsetMs`、`sourceOffsetMs` | `Long`，单曲设置与外部原始来源的偏移分量 |
| `status`、`updating` | `String`、`Boolean`，见上表 |
| `updatedAtNanos` | `Long`，这次快照变化的同 JVM 单调时刻，不是租约 |

切歌会改变身份令牌，包括经过加载回调的 A → B → A；元数据变化也可能使旧令牌失效。不要用 `trackKey` 单独判断异步工作的有效性。保存过的 `CompositeData`／Java record 是历史快照，调用方仍需继续读取以获知撤回或停用。

## 快捷操作

```java
boolean openSearch(String sessionId, long trackRevision, String trackKey);
boolean rematch(String sessionId, long trackRevision, String trackKey);
```

- `openSearch`：请求打开当前歌曲的手动搜索并刷新目标。入口或类变换权限不可用时返回 `false`。
- `rematch`：撤回当前选曲，清除该曲手动／本地覆盖并重新自动匹配，与「恢复自动匹配」一致；不修改歌曲文件。

返回 `true` 表示已接受并排队，不保证找到歌词或窗口最终打开。入队及实际执行前都会校验完整令牌；期间切歌、外部租约失效或停用时丢弃请求。调用方无需、也不应反射内部 UI 类。

此端点不修改现有 AMLL TTML 接收端点或 External Playback v1 协议，也不提供第三方 Provider 动态注册。
