<h1 align="center">🎵 SPW Lyrics</h1>

<p align="center">为 Salt Player for Windows 自动搜索、匹配并加载更完整的歌词。</p>

<p align="center">
  <a href="https://github.com/GaBoron/SPW-Lyrics/releases/latest"><img src="https://img.shields.io/github/v/release/GaBoron/SPW-Lyrics?label=%E6%9C%80%E6%96%B0%E7%89%88%E6%9C%AC&color=6750A4" alt="最新版本"></a>
  <img src="https://img.shields.io/badge/SPW-1.16.2-3A7AFE" alt="已测试 SPW 版本">
  <img src="https://img.shields.io/badge/Windows-x64-0078D4" alt="Windows x64">
  <a href="LICENSE"><img src="https://img.shields.io/github/license/GaBoron/SPW-Lyrics?color=2E8B57" alt="许可证"></a>
</p>

SPW Lyrics 是一款面向 [Salt Player for Windows（SPW）](https://github.com/Moriafly/SPW) 的歌词增强插件。安装后正常播放歌曲即可，无需每次手动搜索。

## ✨ 功能亮点

- 🔎 **自动搜索**：支持 AMLL TTML DB、QQ 音乐、酷狗音乐和网易云音乐。
- 🎯 **可靠匹配**：综合歌名、歌手、专辑和歌曲时长，减少同名歌、Live、Remix 等版本误配。
- 🎤 **丰富歌词**：优先逐字歌词，并支持逐行歌词、普通歌词、翻译、音译、对唱和背景歌词。
- ⚡ **自动刷新**：找到可靠结果后尝试立即替换当前歌词，不会阻塞音乐播放。
- 🖱️ **手动选择**：使用 SPW 中的“打开歌词搜索”快捷键（默认 `Ctrl+Shift+M`），即可修改关键词、指定来源、预览候选，或随时切回 SPW 本地歌词。
- 📚 **批量预缓存**：从设置打开批量处理窗口，一次为音乐库准备歌词，并清楚显示每首歌的进度和结果。
- 💾 **本地缓存**：已经找到或手动选择的歌词会保存，之后播放加载更快。

## 📥 下载与安装

以下步骤适用于目前公开的 `0.4.0` Release；本开发分支的 `.spmod` 需等待 SPW 1.19 发布并完成验收。

1. 前往 [最新 Release](https://github.com/GaBoron/SPW-Lyrics/releases/latest)。
2. 下载 `spw-lyrics-0.4.0.zip`，**不要解压**。
3. 打开 SPW 的“**设置 → 创意工坊**”。
4. 使用本地安装或导入模组的入口，选择刚才下载的 ZIP。
5. 如果插件没有立即出现，请重启一次 SPW。

> [!IMPORTANT]
> Release 中的 ZIP 已包含全部运行组件。普通用户不需要另外安装 Java、.NET 或其他运行库。

## 🚀 开始使用

安装完成后直接播放歌曲。没有缓存时，SPW 会先继续显示内嵌歌词或同名 `.lrc`，插件同时在后台搜索；找到可靠歌词后会尝试自动刷新。

可在插件设置的“自动替换歌词”中选择始终优先使用插件歌词、仅在本地无歌词时补充，或仅在手动选择时替换。

需要手动挑选歌词时，可以在插件设置中点击“打开手动搜索”，也可以使用 SPW 中配置的歌词搜索快捷键。首次启用时请授予曲库读取和快捷键权限。

手动窗口首次打开时只把当前歌名填入搜索框，歌手、专辑和时长仍按各自权重参与匹配。选择候选后可以预览并应用；如果跨来源补充的翻译不合适，可以点击“取消补充翻译”，也可以恢复自动匹配或切回本地歌词。窗口会记住上次位置，同一时间只会打开一个。

第一次使用前，可在插件设置点击“批量处理音乐库”。可勾选要处理的歌曲，窗口默认跳过已有缓存，以三个任务并行处理，并复用重复曲目的搜索结果。总体与单曲进度会显示当前正在搜索来源、选择歌词、补充翻译还是写入缓存；同时支持暂停、继续、停止和失败项重试。

自动匹配失败时，插件会提醒你尝试手动搜索；这类失败结果不会被缓存，下次播放仍会重新搜索。

## 📚 更多帮助

- 📖 [详细使用说明](docs/使用说明.md)
- 🛟 [常见问题](docs/常见问题.md)
- 🧰 [开发与构建](docs/开发说明.md)

## ⚠️ 兼容性

最新正式 Release `0.4.0` 已在 SPW `1.16.2`（Steam、Windows x64）上测试。当前开发分支正在适配 Workshop API `0.1.0-dev21` / SPW 1.19；该分支产物为 `.spmod`，不兼容旧版 SPW，尚未完成正式版运行验收。

本项目采用 [MIT License](LICENSE)。
