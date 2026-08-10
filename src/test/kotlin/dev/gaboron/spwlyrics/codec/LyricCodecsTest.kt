package dev.gaboron.spwlyrics.codec

import dev.gaboron.spwlyrics.domain.LyricsQuality
import dev.gaboron.spwlyrics.domain.LyricsSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFails

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
