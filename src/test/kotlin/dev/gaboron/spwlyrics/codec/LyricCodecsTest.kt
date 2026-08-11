package dev.gaboron.spwlyrics.codec

import dev.gaboron.spwlyrics.domain.LyricsQuality
import dev.gaboron.spwlyrics.domain.LyricsSource
import dev.gaboron.spwlyrics.domain.LyricLine
import dev.gaboron.spwlyrics.domain.LyricsDocument
import java.nio.charset.StandardCharsets
import java.util.Base64
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
        assertTrue(SpwLyricsEncoder.encode(document).startsWith("[00:00.000]歌词来源：网易云音乐"))
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
    fun `keeps qrc lines after unescaped quotes inside lyric content`() {
        val raw = """
            <?xml version="1.0" encoding="utf-8"?>
            <QrcInfos><LyricInfo LyricCount="1">
            <Lyric_1 LyricType="1" LyricContent="[1000,1000]Like, "(1000,400)Where(1400,300)you?(1700,300)
            [2000,1000]After(2000,1000)"/>
            </LyricInfo></QrcInfos>
        """.trimIndent()

        val document = QrcCodec.parse(raw, LyricsSource.QQ)

        assertEquals(listOf("Like, \"Whereyou?", "After"), document.lines.map(LyricLine::text))
        assertEquals(2_000, document.lines.last().startMs)
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
    fun `parses amll bare seconds agents and nested background as parallel lines`() {
        val raw = """
            <tt xmlns="http://www.w3.org/ns/ttml"
                xmlns:ttm="http://www.w3.org/ns/ttml#metadata"
                xmlns:itunes="http://music.apple.com/lyric-ttml-internal"
                itunes:timing="Word">
              <head><metadata>
                <ttm:agent type="person" xml:id="v1" />
                <ttm:agent type="person" xml:id="v2" />
              </metadata></head>
              <body><div>
                <p begin="8.098" end="9.500" ttm:agent="v1">
                  <span begin="8.098" end="8.407">每</span><span begin="8.407" dur="0.500">个</span>
                  <span ttm:role="x-bg" begin="8.400" end="9.200">
                    <span begin="8.400" end="8.700">(和</span><span begin="8.700" end="9.200">声)</span>
                  </span>
                </p>
                <p begin="8.098" end="9.500" ttm:agent="v2">
                  <span begin="8.098" end="8.500">你</span><span begin="8.500" end="9.500">好</span>
                </p>
              </div></body>
            </tt>
        """.trimIndent()

        val document = TtmlCodec().parse(raw, LyricsSource.AMLL)

        assertEquals(LyricsQuality.WORD_SYNCED, document.quality)
        assertEquals(listOf("每个", "你好", "(和声)"), document.lines.map(LyricLine::text))
        assertEquals(listOf("v1", "v2", "v1"), document.lines.map(LyricLine::agent))
        assertEquals(8_098, document.lines.first().startMs)
        assertEquals(8_907, document.lines.first().words.last().endMs)
        assertTrue(document.lines.last().background)
    }

    @Test
    fun `rejects invalid krc encrypted payload`() {
        assertFails { KrcCodec.decryptBase64("aW52YWxpZA==") }
    }

    @Test
    fun `maps kugou translation and multi-part romanization by lyric row`() {
        val language = """{"content":[{"type":1,"lyricContent":[["你好"],["世界"]]},{"type":0,"lyricContent":[["ni ","hao"],["shi ","jie"]]}]}"""
        val encoded = Base64.getEncoder().encodeToString(language.toByteArray(StandardCharsets.UTF_8))
        val document = KrcCodec.parse(
            """
                [language:$encoded]
                [1000,900]<0,400,0>Hel<400,500,0>lo
                [2000,900]<0,900,0>World
            """.trimIndent(),
            LyricsSource.KUGOU,
        )

        assertEquals("你好", document.lines[0].translation)
        assertEquals("ni hao", document.lines[0].romanization)
        assertEquals("世界", document.lines[1].translation)
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
