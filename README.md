<h1 align="center">SPW Lyrics</h1>

<p align="center">为 Salt Player for Windows 自动搜索、匹配并加载多来源歌词。</p>

<p align="center">
  <a href="https://github.com/GaBoron/SPW-Lyrics/releases/latest"><img src="https://img.shields.io/github/v/release/GaBoron/SPW-Lyrics?label=%E6%9C%80%E6%96%B0%E7%89%88%E6%9C%AC&color=6750A4" alt="最新版本"></a>
  <img src="https://img.shields.io/badge/SPW-1.19.0-3A7AFE" alt="目标 SPW 版本">
  <img src="https://img.shields.io/badge/Windows-x64-0078D4" alt="Windows x64">
  <a href="LICENSE"><img src="https://img.shields.io/github/license/GaBoron/SPW-Lyrics?color=2E8B57" alt="许可证"></a>
</p>

SPW Lyrics 是 [Salt Player for Windows（SPW）](https://github.com/Moriafly/SPW) 的歌词增强插件。播放歌曲时，插件会从多个来源查找歌词，按歌词质量和歌曲信息选择更合适的结果，并缓存到本地。

## 功能

- 支持 AMLL TTML DB、Apple Music、QQ 音乐、酷狗音乐和网易云音乐。
- 优先选择逐字歌词，也支持逐行歌词、普通歌词、翻译、音译、对唱和背景歌词。
- 综合歌名、歌手、专辑和歌曲时长匹配，尽量避开 Live、Remix、伴奏等错误版本。
- 支持手动搜索、来源筛选、歌词预览、恢复自动匹配和切回 SPW 本地歌词。
- 支持批量预缓存音乐库，适合首次使用或一次加入大量歌曲后处理。
- 搜索和缓存都在后台完成，不会因为单个歌词来源失败而阻塞播放。

## 安装

从 [Releases](https://github.com/GaBoron/SPW-Lyrics/releases/latest) 下载与当前 SPW 版本对应的安装包，然后在 SPW 的“设置 → 创意工坊”中使用本地安装或导入入口安装。

公开版 `0.4.0` 使用 ZIP 安装包，下载后不要解压。`dev21` 分支改用 `.spmod`，基于 Workshop API `0.1.0-dev21`，需要 SPW 1.19.0；在 1.19.0 完成实际运行验证前，不建议普通用户使用该分支构建。

安装包已包含插件运行所需组件，普通用户不需要另外安装 Java、.NET 或 Windows App SDK。

## 使用

安装并启用插件后，正常播放歌曲即可。常用入口都在“设置 → 创意工坊 → 模组设置 → SPW Lyrics”。

- “自动替换歌词”用于控制插件何时自动搜索。
- “打开手动搜索”用于修改关键词、指定来源、预览并应用候选歌词。
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

当前 `dev21` 分支面向 SPW 1.19.0 / Workshop API `0.1.0-dev21`，不兼容 SPW 1.19.0 之前的创意工坊接口。公开版 `0.4.0` 已在 SPW 1.18.0（Steam、Windows x64）上测试。

SPW 创意工坊接口仍可能继续变化。如果 SPW 更新后插件无法加载、快捷键失效或歌词不能自动刷新，请先查看 [常见问题](docs/常见问题.md)，再提交 Issue。

本项目采用 [MIT License](LICENSE)。
