# SPW Lyrics

`spw-lyrics` 是 Salt Player for Windows 的多来源歌词增强插件，当前 MVP 版本为 `0.1.0`。

插件在 SPW 请求歌词时立即返回已有缓存；缓存未命中则先交还 SPW 的内嵌歌词/同名 `.lrc` 默认流程，并在后台按以下顺序搜索：

`AMLL TTML DB → QQ 音乐 → 酷狗音乐 → 网易云音乐 → SPW 本地歌词`

自动替换采用固定的标题、艺术家、综合分和候选间距门槛。Live、Remix、伴奏、翻唱等版本冲突或候选明显歧义时不会替换。设置页中的“手动搜索歌词”可修改关键词、限定来源、预览并应用任意候选，也可永久切回当前歌曲的本地歌词。

## 设计

- `domain`：统一曲目查询、候选、歌词文档和匹配评分。
- `codec`：独立解析 TTML、QRC、KRC、YRC、LRC，并编码为 SPW 格式。
- `provider`：AMLL、QQ、酷狗、网易云和本地回退的独立适配器。
- `application`：来源优先决策、缓存、8 秒总时限、后台任务与切歌竞态控制。
- `integration`：SPW 扩展点、设置按钮、Swing 手动搜索窗口和隔离的刷新桥。

AMLL 只下载并持久化 `am-lyrics/index.jsonl`，建立元数据倒排索引和平台 ID 索引，命中后仅下载一个 TTML 文件，不遍历仓库。成功歌词缓存 30 天，搜索缓存 6 小时，确定失败缓存 24 小时，手动选择永久保存；数据位于 `%LOCALAPPDATA%\SPW Lyrics`。

当前 SPW 公开 API 没有歌词重载方法，因此即时刷新通过隔离的兼容桥尝试完成。若当前 SPW 版本不兼容，歌词仍会保存，重新选曲即可加载。

## Build

```powershell
.\gradlew.bat test plugin
```

安装包生成到 `build/plugin/spw-lyrics-0.1.0.zip`。项目要求 JDK 21，直接依赖 `spw-workshop-api`，没有维护本地 API stub。

测试覆盖匹配门槛与歧义、简繁元数据、五种歌词格式、翻译/音译、缓存、来源回退、平台响应变化和切歌竞态。
