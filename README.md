<h1 align="center">SPW Lyrics</h1>

<p align="center">为 Salt Player for Windows 自动搜索、匹配并加载多来源歌词。</p>

<p align="center">
  <a href="https://github.com/GaBoron/SPW-Lyrics/releases/latest"><img src="https://img.shields.io/github/v/release/GaBoron/SPW-Lyrics?label=%E6%9C%80%E6%96%B0%E7%89%88%E6%9C%AC&color=6750A4" alt="最新版本"></a>
  <img src="https://img.shields.io/badge/SPW-1.19.0-3A7AFE" alt="目标 SPW 版本">
  <img src="https://img.shields.io/badge/Windows%20%2F%20Linux-0078D4" alt="Windows / Linux">
  <a href="LICENSE"><img src="https://img.shields.io/github/license/GaBoron/SPW-Lyrics?color=2E8B57" alt="许可证"></a>
</p>

SPW Lyrics 是 [Salt Player for Windows（SPW）](https://github.com/Moriafly/SPW) 的歌词增强插件。播放歌曲时，插件会从多个来源查找歌词，按歌词质量和歌曲信息选择更合适的结果，并缓存到本地。

## 功能

- 支持 AMLL TTML DB、QQ 音乐、酷狗音乐和网易云音乐。
- 优先选择逐字歌词，也支持逐行歌词、普通歌词、翻译、音译、对唱和背景歌词。
- 综合歌名、歌手、专辑和歌曲时长匹配，尽量避开 Live、Remix、伴奏等错误版本。
- 支持手动搜索、来源筛选、歌词预览、恢复自动匹配和切回 SPW 本地歌词。
- 支持按歌曲保存歌词延迟，同时调整逐行和逐字时间。
- 支持编辑本地歌曲元数据，导入搜索结果或本地歌词，并转换为 TTML、LRC、增强 LRC、SPL 后写入内嵌歌词。
- 支持增删歌词、逐句和逐字修改，Ctrl / Shift 多选后批量调整延迟、主唱、对唱和背景人声，同时保留原文编辑。
- 与 [AMLL into SPW](https://github.com/GaBoron/AMLL-into-SPW) 同时启用时，直接提供原始 TTML，保留其完整声部、翻译、音译和注音信息。
- 支持批量预缓存音乐库，适合首次使用或一次加入大量歌曲后处理。
- Salt UI 歌词管理页整合手动搜索、元数据编辑和批量处理，从 SPW 主窗口顶部进入。
- 搜索和缓存都在后台完成，不会因为单个歌词来源失败而阻塞播放。

## 安装

从 [Releases](https://github.com/GaBoron/SPW-Lyrics/releases/latest) 下载与当前 SPW 版本对应的安装包，然后在 SPW 的“设置 → 创意工坊”中使用本地安装或导入入口安装。

当前版本 `2.0.0` 需要 SPW 1.19.0。下载 `spw-lyrics-2.0.0.spmod` 后直接导入，不要解压。SPW 1.18 用户请使用 [v0.4.0](https://github.com/GaBoron/SPW-Lyrics/releases/tag/v0.4.0)。

Windows 和 Linux 使用同一个 `.spmod` 安装包。安装包包含 Salt UI 等插件依赖，界面复用 SPW 自带的 Compose/Skiko 和 JVM，普通用户不需要另外安装运行时。

`2.0.0` 已取消独立工具窗口，歌词管理需要“类变换”权限及主窗口 Hook 接入成功。动态发现不能保证适应宿主结构变化；Linux 主窗口嵌入尚未实机验证。

## 使用

安装并启用插件后，正常播放歌曲即可。授予类变换权限后，主窗口顶部的“歌词管理”图标提供手动搜索、元数据编辑和批量处理三个标签页。也可从“设置 → 创意工坊 → 模组设置 → SPW Lyrics”进入相应标签页。

- “自动替换歌词”用于控制插件何时自动搜索。
- “打开手动搜索”用于修改关键词、指定来源、预览并应用候选歌词。
- “歌曲元数据编辑”用于修改本地歌曲信息，或将歌词写入歌曲文件；写入失败时回退，不在歌曲目录留下备份。
- “批量处理音乐库”用于提前搜索并缓存音乐库中的歌词。
- “查看本地缓存文件夹”用于查看或清理插件缓存。

手动搜索也可以通过 SPW 的“打开歌词搜索”快捷键打开，默认按键为 `Ctrl+Shift+M`。快捷键的修改、冲突处理和全局绑定由 SPW 管理。

更完整的操作说明见 [使用说明](docs/使用说明.md)。遇到安装、匹配或刷新问题时先看 [常见问题](docs/常见问题.md)。

## 文档

| 文档 | 内容 |
| --- | --- |
| [使用说明](docs/使用说明.md) | 自动加载、手动搜索、批量处理、缓存与网络说明 |
| [常见问题](docs/常见问题.md) | 安装、匹配、歌词显示和缓存排查 |
| [开发与构建](docs/开发说明.md) | 构建环境、代码结构、歌词来源和 SPW 集成 |

## 兼容性

`2.0.0` 面向 SPW 1.19.0 / Workshop API `0.1.0-dev23`，使用宿主的 JVM 25，不兼容旧版创意工坊接口。`0.4.0` 保留供 SPW 1.18 用户使用。

SPW 创意工坊接口仍可能继续变化。如果 SPW 更新后插件无法加载、快捷键失效或歌词不能自动刷新，请先查看 [常见问题](docs/常见问题.md)，再提交 Issue。

本项目采用 [MIT License](LICENSE)。
