<h1 align="center">SPW Lyrics</h1>

<p align="center">
  为 <a href="https://github.com/Moriafly/SPW">Salt Player for Windows（SPW）</a> 提供多来源歌词搜索、匹配与管理。
</p>

<p align="center">
  <a href="https://github.com/GaBoron/SPW-Lyrics/releases/latest"><img src="https://img.shields.io/github/v/release/GaBoron/SPW-Lyrics?label=Release&color=6750A4" alt="最新版本"></a>
  <img src="https://img.shields.io/badge/SPW-1.19.0-3A7AFE" alt="目标 SPW 版本">
  <img src="https://img.shields.io/badge/Windows%20%2F%20Linux-0078D4" alt="Windows 和 Linux">
  <a href="LICENSE"><img src="https://img.shields.io/github/license/GaBoron/SPW-Lyrics?color=2E8B57" alt="MIT 许可证"></a>
</p>

<p align="center">
  <a href="https://github.com/GaBoron/SPW-Lyrics/releases/latest">下载插件</a> ·
  <a href="docs/使用说明.md">使用说明</a> ·
  <a href="docs/常见问题.md">常见问题</a>
</p>

播放歌曲即可自动获取歌词；结果不合适时，可以在 SPW 内搜索、预览并手动选择。也可编辑歌曲标签、封面和内嵌歌词，或批量处理音乐库。

## ✨ 功能

| 需求 | 支持的操作 |
| --- | --- |
| 自动获取歌词 | 比较歌名、歌手、专辑和时长，按歌词类型或来源排序，支持保留本地歌词 |
| 选择与校正 | 手动搜索、预览、保存单曲选择及歌词延迟 |
| 编辑歌词与歌曲信息 | 导入在线信息和封面，编辑逐句、逐字歌词，写入 TTML、LRC、增强 LRC 或 SPL |
| 逐字打轴 | 跟随 SPW 播放记录时间，支持自动分词、输入延迟校准及频谱、波形辅助 |
| 批量管理 | 提前缓存歌词，按所选字段补充或覆盖音频标签 |
| AMLL 互通 | 向 [AMLL into SPW](https://github.com/GaBoron/AMLL-into-SPW) 提供原始 TTML，保留来源中的声部、背景人声、翻译、音译和注音 |

歌词来源包括 AMLL TTML DB、QQ 音乐、汽水音乐、酷狗音乐、网易云音乐、Apple Music 和 LrcShare；MusicBrainz 仅提供元数据。LrcShare 需要自己的 API Key，Apple Music 歌词依赖第三方服务。

## 📦 安装与开始使用

面向 **SPW 1.19.0 / Workshop API dev23 / JVM 25**。Windows 与 Linux 共用 `.spmod`，直接导入 SPW，无需解压或另装 JVM。SPW 1.18 请按 [v0.4.0](https://github.com/GaBoron/SPW-Lyrics/releases/tag/v0.4.0) 的说明安装旧版。

1. 从 [Releases](https://github.com/GaBoron/SPW-Lyrics/releases/latest) 下载 `.spmod`，在 SPW「设置 → 创意工坊」中导入并启用。
2. 授予「读取曲库」权限；使用管理页面和播放器菜单时，还需「类变换」权限。
3. 播放歌曲即可自动加载。顶部「歌词管理」进入批量管理；播放器「更多」菜单可进入手动搜索或元数据编辑。

频谱分析另需安装并启用 **SPW Media Runtime**，安装入口见[频谱与波形辅助](docs/使用说明.md#频谱与波形辅助)。自动加载和「缓存歌词」只写插件缓存；编辑页确认保存或批量「开始写入」会修改音频文件。

文档对应当前源码，安装包功能请以相应 [Release 说明](https://github.com/GaBoron/SPW-Lyrics/releases)为准。宿主更新后，界面接入可能需要适配。

## 📖 文档与帮助

- [使用说明](docs/使用说明.md)：选择歌词、编辑与打轴、批量管理、缓存和隐私。
- [常见问题](docs/常见问题.md)：入口缺失、匹配错误、显示与写入问题的恢复步骤。
- [开发与构建](docs/开发说明.md)：构建、架构边界、Hook 缓存、TTML 互通和诊断。
- [问题反馈](https://github.com/GaBoron/SPW-Lyrics/issues)：附 SPW / 插件版本、复现步骤及相关异常。

## 📄 许可证

[MIT License](LICENSE)。第三方资源声明随安装包保留，见[开发说明](docs/开发说明.md#参考与第三方资源)。
