package dev.gaboron.spwlyrics.integration

import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.Shell32
import com.sun.jna.platform.win32.WinError
import com.sun.jna.platform.win32.WinUser
import java.awt.Desktop
import java.net.URI

/** Protocol activation must bypass Desktop.browse(), which launches a browser on Windows. */
internal object SteamWorkshopLauncher {
    fun open(url: String) {
        val uri = URI(url)
        require(uri.scheme.equals("steam", ignoreCase = true)) { "Steam 安装入口无效。" }
        if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
            WindowsSteamProtocol.open(uri.toString())
        } else {
            check(Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                "无法唤起 Steam，请确认客户端已安装并重试。"
            }
            Desktop.getDesktop().browse(uri)
        }
    }
}

/** Loaded only on Windows; SPW supplies the JNA runtime. */
private object WindowsSteamProtocol {
    fun open(uri: String) {
        val ole = Ole32.INSTANCE
        val initialized = ole.CoInitializeEx(null, Ole32.COINIT_APARTMENTTHREADED or Ole32.COINIT_DISABLE_OLE1DDE).toInt()
        check(initialized >= 0 || initialized == WinError.RPC_E_CHANGED_MODE) { "无法初始化 Steam 协议启动。" }
        try {
            val result = Shell32.INSTANCE.ShellExecute(null, "open", uri, null, null, WinUser.SW_SHOWNORMAL).toLong()
            check(result > 32) { "无法唤起 Steam（系统错误 $result），请确认客户端已安装并重试。" }
        } finally {
            if (initialized >= 0) ole.CoUninitialize()
        }
    }
}
