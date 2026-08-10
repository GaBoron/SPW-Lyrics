package dev.gaboron.spwlyrics.codec

import dev.gaboron.spwlyrics.domain.LyricsQuality
import dev.gaboron.spwlyrics.domain.LyricsSource
import dev.gaboron.spwlyrics.domain.LyricLine
import dev.gaboron.spwlyrics.domain.LyricsDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFails
import kotlin.test.assertNull

class LyricCodecsTest {
    @Test
    fun `parses line synced lrc and translation`() {
        val original = LrcCodec.parse("[00:01.00]Hello\n[00:02.00]world", LyricsSource.NETEASE)
        val translated = LrcCodec.parse("[00:01.00]你好\n[00:02.00]世界", LyricsSource.NETEASE)
        val merged = LyricsTrackMerger.merge(original, translated.lines)

        assertEquals(LyricsQuality.LINE_SYNCED, merged.quality)
        assertEquals("你好", merged.lines.first().translation)
    }

    @Test
    fun `parses yrc word timings`() {
        val document = YrcCodec.parse(
            "[1000,900](1000,400,0)你(1400,500,0)好",
            LyricsSource.NETEASE,
        )

        assertEquals(LyricsQuality.WORD_SYNCED, document.quality)
        assertEquals("你好", document.lines.single().text)
        assertEquals(1900, document.lines.single().endMs)
    }

    @Test
    fun `removes netease structured credits from instrumental yrc`() {
        val document = YrcCodec.parse(
            """
                [0,1000]{"t":0,"c":[{"tx":"作词："},{"tx":"Wisp X"}]}
                [1000,2000](1000,2000,0)纯音乐，请欣赏
            """.trimIndent(),
            LyricsSource.NETEASE,
        )

        assertEquals(listOf("纯音乐，请欣赏"), document.lines.map { it.text })
        assertEquals(listOf("作词：Wisp X"), document.metadata["credits"])
    }

    @Test
    fun `removes untimed netease structured credits without degrading lrc`() {
        val document = LrcCodec.parse(
            """
                [00:18.480]他 他下落不明地出发
                [00:24.730]在阳光灿烂的世界
                {"t":0,"c":[{"tx":"作曲 Composer: "},{"tx":"华晨宇","li":"https://example.invalid"}]}
                {"t":1000,"c":[{"tx":"作词 Lyricist: "},{"tx":"唐恬"}]}
            """.trimIndent(),
            LyricsSource.NETEASE,
        )

        assertEquals(LyricsQuality.LINE_SYNCED, document.quality)
        assertEquals(listOf("他 他下落不明地出发", "在阳光灿烂的世界"), document.lines.map { it.text })
        assertEquals(listOf("作曲 Composer: 华晨宇", "作词 Lyricist: 唐恬"), document.metadata["credits"])
        assertTrue(SpwLyricsEncoder.encode(document).startsWith("[00:18.480]"))
    }

    @Test
    fun `aligns timed translation monotonically without index fallback`() {
        val original = LrcCodec.parse(
            "[00:10.00]First\n[00:20.00]Second\n[00:30.00]Third",
            LyricsSource.NETEASE,
        )
        val translation = LrcCodec.parse(
            "[00:00.00]翻译信息\n[00:10.20]第一句\n[00:30.10]第三句",
            LyricsSource.NETEASE,
        )

        val merged = LyricsTrackMerger.merge(original, translation.lines)

        assertEquals("第一句", merged.lines[0].translation)
        assertNull(merged.lines[1].translation)
        assertEquals("第三句", merged.lines[2].translation)
    }

    @Test
    fun `does not index align incomplete untimed secondary text`() {
        val original = LyricsDocument(
            LyricsSource.NETEASE,
            dev.gaboron.spwlyrics.domain.LyricsFormat.PLAIN,
            listOf(LyricLine(text = "First"), LyricLine(text = "Second")),
        )

        val merged = LyricsTrackMerger.merge(original, listOf(LyricLine(text = "只有一句")))

        assertTrue(merged.lines.all { it.translation == null })
    }

    @Test
    fun `parses raw qrc word timings`() {
        val document = QrcCodec.parse(
            "[1000,900]你(1000,400)好(1400,500)",
            LyricsSource.QQ,
        )

        assertEquals(LyricsQuality.WORD_SYNCED, document.quality)
        assertEquals(2, document.lines.single().words.size)
    }

    @Test
    fun `parses ttml words and aligned translation`() {
        val raw = """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <body><div>
                <p begin="1s" end="2s"><span begin="1s" end="1.4s">Hel</span><span begin="1.4s" end="2s">lo</span></p>
                <p begin="1s" end="2s" ttm:role="translation">你好</p>
              </div></body>
            </tt>
        """.trimIndent()
        val document = TtmlCodec().parse(raw, LyricsSource.AMLL)

        assertEquals(LyricsQuality.WORD_SYNCED, document.quality)
        assertEquals("Hello", document.lines.single().text)
        assertEquals("你好", document.lines.single().translation)
    }

    @Test
    fun `parses amll minute second clock times`() {
        val raw = """
            <tt xmlns="http://www.w3.org/ns/ttml">
              <body><div begin="00:14.051" end="03:37.527">
                <p begin="00:14.051" end="00:17.414">
                  <span begin="00:14.051" end="00:14.318">你</span><span begin="00:14.318" end="00:17.414">好</span>
                </p>
              </div></body>
            </tt>
        """.trimIndent()

        val document = TtmlCodec().parse(raw, LyricsSource.AMLL)

        assertEquals(LyricsQuality.WORD_SYNCED, document.quality)
        assertEquals(14_051, document.lines.single().startMs)
        assertEquals(2, document.lines.single().words.size)
        assertTrue(SpwLyricsEncoder.encode(document).contains("<00:14.318>"))
    }

    @Test
    fun `keeps nested amll translation and romanization out of primary text`() {
        val raw = """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <body><div><p begin="1s" end="2s">
                <span begin="1s" end="1.4s">你</span><span begin="1.4s" end="2s">好</span>
                <span ttm:role="x-translation">Hello</span><span ttm:role="x-roman">Ni hao</span>
              </p></div></body>
            </tt>
        """.trimIndent()
        val line = TtmlCodec().parse(raw, LyricsSource.AMLL).lines.single()

        assertEquals("你好", line.text)
        assertEquals("Hello", line.translation)
        assertEquals("Ni hao", line.romanization)
    }

    @Test
    fun `marks ttml background vocals`() {
        val raw = """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <body><div><p begin="1s" end="2s" ttm:role="x-bg"><span begin="1s" end="2s">echo</span></p></div></body>
            </tt>
        """.trimIndent()

        assertTrue(TtmlCodec().parse(raw, LyricsSource.AMLL).lines.single().background)
    }

    @Test
    fun `rejects invalid krc encrypted payload`() {
        assertFails { KrcCodec.decryptBase64("aW52YWxpZA==") }
    }

    @Test
    fun `malformed word timing degrades instead of claiming word sync`() {
        val document = LrcCodec.parse(
            "[00:01.00]<00:01.500>A<00:01.200>B\n[00:02.00]C",
            LyricsSource.QQ,
        )

        assertTrue(document.quality.rank <= LyricsQuality.LINE_SYNCED.rank)
    }
}
