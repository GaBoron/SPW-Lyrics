package dev.gaboron.spwlyrics.integration

import java.awt.Desktop
import java.net.URI

internal object SponsorLink {
    const val URL = "https://afdian.com/a/gaboron"

    fun open() {
        check(Desktop.isDesktopSupported()) { "当前环境无法打开网页。" }
        val desktop = Desktop.getDesktop()
        check(desktop.isSupported(Desktop.Action.BROWSE)) { "当前环境不支持打开网页。" }
        desktop.browse(URI(URL))
    }
}
