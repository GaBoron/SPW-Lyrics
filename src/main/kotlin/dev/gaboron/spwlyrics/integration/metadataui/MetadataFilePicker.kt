package dev.gaboron.spwlyrics.integration.metadataui

import java.awt.FileDialog
import java.awt.Frame
import java.awt.KeyboardFocusManager
import java.nio.file.Path

internal object MetadataFilePicker {
    fun audio(): Path? = choose("打开歌曲文件", "*.mp3;*.flac;*.m4a;*.mp4;*.ogg;*.wav;*.aif;*.aiff")
    fun lyrics(): Path? = choose("导入歌词（TTML / LRC / 增强 LRC / SPL）", "*.ttml;*.lrc;*.spl")
    fun cover(): Path? = choose("选择封面（JPEG / PNG）", "*.jpg;*.jpeg;*.png")

    private fun choose(title: String, extensions: String): Path? {
        val owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow as? Frame
        val dialog = FileDialog(owner, title, FileDialog.LOAD)
        return try {
            dialog.isMultipleMode = false
            dialog.file = extensions
            dialog.isVisible = true
            dialog.files.firstOrNull()?.toPath()
        } finally {
            dialog.dispose()
        }
    }
}
