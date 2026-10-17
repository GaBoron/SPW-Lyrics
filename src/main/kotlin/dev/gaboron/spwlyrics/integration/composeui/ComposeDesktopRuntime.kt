package dev.gaboron.spwlyrics.integration.composeui

import java.nio.file.Path

/** SPW and its plugins may ship different Skiko versions in separate class loaders. */
internal object ComposeDesktopRuntime {
    fun load() = synchronized(System.getProperties()) {
        val resource = checkNotNull(javaClass.getResource("/native/compose/skiko-windows-x64.dll")) {
            "Compose 原生渲染文件缺失，请重新安装完整插件包。"
        }
        check(resource.protocol == "file") { "Compose 原生渲染文件需要由 SPW 解包后加载。" }
        val previous = System.getProperty("skiko.library.path")
        try {
            System.setProperty("skiko.library.path", Path.of(resource.toURI()).parent.toString())
            org.jetbrains.skiko.Library.load()
        } finally {
            if (previous == null) System.clearProperty("skiko.library.path")
            else System.setProperty("skiko.library.path", previous)
        }
    }
}
