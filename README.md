<p align="center">
  <img src="winui/SpwLyrics.WinUI/Assets/Square150x150Logo.scale-200.png" width="120" alt="SPW Lyrics 图标">
</p>

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

- 🔎 **自动搜索**：依次尝试 AMLL TTML DB、QQ 音乐、酷狗音乐和网易云音乐。
- 🎯 **可靠匹配**：综合歌名、歌手、专辑和歌曲时长，减少同名歌、Live、Remix 等版本误配。
- 🎤 **丰富歌词**：优先逐字歌词，并支持逐行歌词、普通歌词、翻译、音译、对唱和背景歌词。
- ⚡ **自动刷新**：找到可靠结果后尝试立即替换当前歌词，不会阻塞音乐播放。
- 🖱️ **手动选择**：可修改关键词、指定来源、预览候选，或随时切回 SPW 本地歌词。
- 💾 **本地缓存**：已经找到或手动选择的歌词会保存，之后播放加载更快。

## 📥 下载与安装

1. 前往 [最新 Release](https://github.com/GaBoron/SPW-Lyrics/releases/latest)。
2. 下载 `spw-lyrics-0.1.0.zip`，**不要解压**。
3. 打开 SPW 的“**设置 → 创意工坊**”。
4. 使用本地安装或导入模组的入口，选择刚才下载的 ZIP。
5. 如果插件没有立即出现，请重启一次 SPW。

> [!IMPORTANT]
> Release 中的 ZIP 已包含全部运行组件。普通用户不需要另外安装 Java、.NET 或其他运行库。

## 🚀 开始使用

安装完成后直接播放歌曲。没有缓存时，SPW 会先继续显示内嵌歌词或同名 `.lrc`，插件同时在后台搜索；找到可靠歌词后会尝试自动刷新。

需要手动挑选歌词时，进入：

> **设置 → 创意工坊 → 模组设置 → SPW Lyrics → 手动搜索歌词**

手动窗口会自动搜索当前歌曲。选择候选后可以预览并应用，也可以点击“切回本地歌词”。同一时间只会打开一个手动窗口。

自动匹配失败时，插件会提醒你尝试手动搜索；这类失败结果不会被缓存，下次播放仍会重新搜索。

## 📚 更多帮助

- 📖 [详细使用说明](docs/使用说明.md)
- 🛟 [常见问题](docs/常见问题.md)
- 🧰 [开发与构建](docs/开发说明.md)

## ⚠️ 兼容性

当前版本为 `0.1.0`，已在 SPW `1.16.2`（Steam、Windows x64）上测试。SPW 创意工坊接口仍处于试验阶段，后续 SPW 更新可能影响插件兼容性。

本项目采用 [MIT License](LICENSE)。
