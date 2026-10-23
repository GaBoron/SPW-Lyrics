<h1 align="center">SPW Lyrics</h1>

<p align="center">
  为 <a href="https://github.com/Moriafly/SPW">Salt Player for Windows（SPW）</a> 提供多来源歌词搜索、智能匹配与歌词管理。
</p>

<p align="center">
  <a href="https://github.com/GaBoron/SPW-Lyrics/releases/latest"><img src="https://img.shields.io/github/v/release/GaBoron/SPW-Lyrics?label=Release&color=6750A4" alt="最新版本"></a>
  <img src="https://img.shields.io/badge/SPW-1.19.0-3A7AFE" alt="目标 SPW 版本">
  <img src="https://img.shields.io/badge/Workshop_API-dev23-64748B" alt="Workshop API dev23">
  <img src="https://img.shields.io/badge/Windows%20%2F%20Linux-0078D4" alt="支持 Windows 和 Linux">
  <a href="LICENSE"><img src="https://img.shields.io/github/license/GaBoron/SPW-Lyrics?color=2E8B57" alt="许可证"></a>
</p>

<p align="center">
  <a href="https://github.com/GaBoron/SPW-Lyrics/releases/latest">下载插件</a> ·
  <a href="docs/使用说明.md">使用说明</a> ·
  <a href="https://github.com/GaBoron/SPW-Lyrics/issues">问题反馈</a>
</p>

播放歌曲时，SPW Lyrics 会自动搜索并比较多个来源的歌词，结合歌曲信息选择合适的结果并缓存在本地。除了自动加载，也可以在 SPW 内手动搜索、编辑歌词和元数据，或为整个音乐库提前建立歌词缓存。

## ✨ 功能亮点

| 功能 | 说明 |
| --- | --- |
| 🎵 多来源搜索 | 聚合多个歌词源，结合歌名、歌手、专辑和时长匹配录音版本 |
| 🎤 丰富的歌词内容 | 支持逐字、逐行与普通歌词，以及翻译、音译、对唱和背景人声 |
| 🎯 自定义选择策略 | 可设置歌词类型或来源优先，并调整优先级顺序；支持优先保留本地歌词 |
| 🔎 手动搜索与预览 | 自定义关键词、筛选来源、查看匹配信息与歌词预览，按歌曲保存选择 |
| ✍️ 歌词与元数据编辑 | 逐句、逐字编辑歌词与时间轴，批量调整声部；导入、转换并写入内嵌歌词 |
| ⏱️ 歌词延迟 | 按歌曲保存延迟，同时调整逐行与逐字时间，不修改原始歌词文件 |
| 📚 批量预缓存 | 提前为音乐库搜索歌词，支持选择歌曲、查看进度与重新处理 |
| 🔗 AMLL 互通 | 向 [AMLL into SPW](https://github.com/GaBoron/AMLL-into-SPW) 提供原始 TTML，保留完整声部、翻译、音译和注音信息 |

### 🎼 歌词来源

| 类型 | 支持的来源 |
| --- | --- |
| 歌词 | AMLL TTML DB、QQ 音乐、汽水音乐、酷狗音乐、网易云音乐、Apple Music、LrcShare |
| 歌曲元数据 | MusicBrainz，以及各歌词来源提供的可用元数据 |
| 本地歌词 | 外置或内嵌 TTML；普通 LRC 与内嵌歌词可交由 SPW 原生处理 |

默认优先选择逐字歌词，其次是逐行和普通歌词；也可以在设置中改成**来源优先**并自定义顺序。LrcShare 需要自行填写 API Key；MusicBrainz 不参与自动歌词选择。详细规则见 [使用说明](docs/使用说明.md#自动替换策略)。

## 📦 安装与使用

**适配 SPW 1.19.0 / Workshop API 0.1.0-dev23**，Windows 与 Linux 共用同一个 `.spmod` 安装包，无需单独安装 JVM。

1. 从 [Releases](https://github.com/GaBoron/SPW-Lyrics/releases/latest) 下载 `.spmod` 文件，在 SPW 的“设置 → 创意工坊”中导入并启用。
2. 根据需要授予“读取曲库”和“类变换”权限；歌词管理页与播放器菜单入口需要类变换权限。
3. 正常播放歌曲即可自动加载歌词。需要调整结果时，可在主窗口内打开歌词管理工具。

### 🧭 从哪里进入？

| 入口 | 可以做什么 |
| --- | --- |
| 主窗口顶部「歌词管理」 | 打开整合页面，默认进入批量处理，可切换手动搜索、元数据编辑和批量处理 |
| 播放器「更多 → 手动搜索歌词」 | 搜索、预览、指定来源，应用歌词或恢复自动匹配 |
| 播放器「更多 → 编辑元数据」 | 编辑歌曲标签、歌词内容与时间轴，转换格式并写入文件 |

歌词编辑支持 **TTML、LRC、增强 LRC 和 SPL**。自动搜索和批量缓存不会修改音频文件；只有明确执行“写入歌曲文件”时才会改动内嵌标签。

## 📖 文档与帮助

| 文档 | 内容 |
| --- | --- |
| [使用说明](docs/使用说明.md) | 自动选择策略、手动搜索、编辑、批量处理及缓存 |
| [常见问题](docs/常见问题.md) | 安装权限、匹配问题、歌词刷新、显示与网络排查 |
| [开发与构建](docs/开发说明.md) | 构建环境、模块设计、歌词来源及 SPW 集成 |

本插件依赖 SPW 的创意工坊接口，宿主更新后可能需要适配。遇到问题时，请先查看 [常见问题](docs/常见问题.md)，再通过 [Issues](https://github.com/GaBoron/SPW-Lyrics/issues) 反馈。

## 📄 许可证

本项目采用 [MIT License](LICENSE)。
