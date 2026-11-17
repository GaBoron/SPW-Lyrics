# 回归检查

安装 JDK 25 后，在仓库根目录运行：

```powershell
.\gradlew.bat regressionTest --no-daemon
```

`test` 和 `check` 也会运行这些检查。首次运行需要下载构建依赖；检查本身不联网、不连接 SPW、不写真实配置。使用 Kotlin 标准库断言，无额外测试框架；标准 `test` 任务只委托 `regressionTest`，自身的 JUnit 发现阶段显示 `SKIPPED`。构建输出留在忽略的 `build/`。

覆盖三种自动替换策略的加载时机、配置回读、后台重试边界、强制重试、逐曲清理与并发回调去重。在线歌词可用性、匹配质量和宿主歌词交接需单独验收。
