package dev.gaboron.spwlyrics.codec

import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.domain.LyricsSource
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

/** File decoding and import conversion are independent of both the UI and song tag storage. */
internal object MetadataLyricsImport {
    private const val MAX_BYTES = 4_000_000

    fun read(path: Path): String {
        val extension = path.fileName.toString().substringAfterLast('.', "").lowercase(Locale.ROOT)
        require(extension in setOf("ttml", "lrc", "spl")) { "仅支持 TTML、LRC、增强 LRC（.lrc）和 SPL 文件。" }
        require(Files.size(path) <= MAX_BYTES) { "歌词文件过大，最多支持 4 MB。" }
        val bytes = Files.newInputStream(path).use { it.readNBytes(MAX_BYTES + 1) }
        require(bytes.size <= MAX_BYTES) { "歌词文件过大，最多支持 4 MB。" }
        val raw = decode(bytes).removePrefix("\uFEFF")
        return raw.also { preview(it) }
    }

    fun preview(raw: String): LyricsDocument? {
        require(raw.length <= MAX_BYTES) { "歌词过大，最多支持 400 万字符。" }
        if (raw.isBlank()) return null
        val text = raw.trimStart().removePrefix("\uFEFF").trimStart()
        val document = if (text.startsWith('<')) {
            val root = PROLOG.replaceFirst(text, "")
            require(TT_ROOT.containsMatchIn(root)) { "XML 歌词必须使用 TTML 的 tt 根元素。" }
            TtmlCodec().parse(raw, LyricsSource.LOCAL)
        } else MetadataTimedLyricsReader.parse(raw)
        require(document.lines.isNotEmpty()) { "未找到歌词正文，请检查 TTML、LRC、增强 LRC 或 SPL 格式。" }
        return document
    }

    private fun decode(bytes: ByteArray): String {
        val charset = when {
            bytes.size >= 2 && bytes[0] == 0xff.toByte() && bytes[1] == 0xfe.toByte() -> StandardCharsets.UTF_16LE
            bytes.size >= 2 && bytes[0] == 0xfe.toByte() && bytes[1] == 0xff.toByte() -> StandardCharsets.UTF_16BE
            else -> StandardCharsets.UTF_8
        }
        return try {
            charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (error: Exception) {
            throw IllegalArgumentException("歌词编码无法读取，请另存为 UTF-8 或带 BOM 的 UTF-16 后重试。", error)
        }
    }

    private val PROLOG = Regex("^(?:<\\?xml[\\s\\S]*?\\?>\\s*)?(?:<!--[\\s\\S]*?-->\\s*)*")
    private val TT_ROOT = Regex("^<(?:[A-Za-z_][\\w.-]*:)?tt(?=[\\s>/])")
}
