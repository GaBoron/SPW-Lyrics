package dev.gaboron.spwlyrics.codec

import dev.gaboron.spwlyrics.domain.LyricsCandidate
import dev.gaboron.spwlyrics.domain.LyricsDocument
import dev.gaboron.spwlyrics.domain.TextNormalizer
import java.text.Normalizer
import java.util.Locale

/** Removes source-supplied credits and title cards after timing and secondary tracks are parsed. */
object LyricsContentFilter {
    private val titleSeparator = Regex("""\s+[-–—]\s+""")
    private val chineseCreditPrefixes = listOf(
        "作词", "作詞", "作曲", "词曲", "詞曲", "编曲", "編曲", "改编", "改編",
        "制作", "製作", "监制", "監製", "音乐总监", "音樂總監", "音乐设计", "音樂設計",
        "音乐发行", "音樂發行", "音乐监制", "音樂監製", "音乐制作", "音樂製作",
        "音乐营销", "音樂營銷", "音响", "音響", "舞台总监", "舞台總監",
        "录音", "錄音", "混音", "母带", "母帶", "和声", "和聲", "和音", "人声", "人聲", "配唱",
        "吉他", "电吉他", "電吉他", "贝斯", "貝斯", "电贝司", "電貝司", "键盘", "鍵盤",
        "鼓录音", "鼓錄音", "鼓手", "架子鼓", "打击乐", "打擊樂", "弦乐", "弦樂",
        "第一小提琴", "第二小提琴", "小提琴", "中提琴", "大提琴", "琵琶", "二胡",
        "古筝", "古箏", "乐队", "樂隊",
        "乐团", "樂團", "乐器", "樂器", "制谱", "製譜", "原唱", "原曲", "演唱", "合唱",
        "出品", "发行", "發行", "版权", "版權", "统筹", "統籌", "策划", "策劃",
        "总策划", "總策劃", "企划", "企劃", "企 划", "执行制作", "執行製作",
        "封面", "插画", "插畫", "推广", "推廣", "音频", "音頻", "民乐", "民樂",
        "翻唱", "项目监制", "項目監製", "歌詞",
    )
    private val shortChineseLabels = setOf("词", "詞", "曲", "鼓", "音乐", "音樂")
    private val shortRightsLabels = setOf("词", "詞", "曲")
    private val rightsSuffix = Regex("""(?i)(?:lyricist|composer|op|sp|oa|oc)""")
    private val englishCreditPrefixes = listOf(
        "lyrics", "lyricist", "music", "composer", "composed", "written", "arranged", "arrangement",
        "producer", "produced", "produce", "executive produce", "sound produce", "vocal", "vocals",
        "recording", "recorded", "mixing", "mixed",
        "mastering", "mastered", "guitar", "electric guitar", "bass", "drums", "keyboard", "strings",
        "drum", "piano", "all instruments", "pgm", "program", "programmer", "video",
        "publisher", "copyright", "label", "op", "sp",
    )

    fun clean(document: LyricsDocument, candidate: LyricsCandidate): LyricsDocument? {
        val title = TextNormalizer.compact(candidate.title)
        val artists = candidate.artists.map(TextNormalizer::compact).filter(String::isNotBlank)
        var beforeLyrics = true
        val lines = document.lines.mapNotNull { line ->
            if (isCreditLine(line.text) || beforeLyrics && isTitleCard(line.text, title, artists)) return@mapNotNull null
            if (line.text.isNotBlank()) beforeLyrics = false
            line.copy(
                translation = line.translation?.takeUnless(::isCreditLine),
                romanization = line.romanization?.takeUnless(::isCreditLine),
            )
        }
        return document.copy(lines = lines, metadata = document.metadata - "credits")
            .takeIf { cleaned -> cleaned.lines.any { it.text.isNotBlank() } }
    }

    fun isCreditLine(value: String): Boolean {
        val text = Normalizer.normalize(value.trim(), Normalizer.Form.NFKC)
        val colon = text.indexOf(':')
        if (colon !in 1..48) return false
        val label = text.substring(0, colon).trim().trimStart('【', '[', '(', '（').lowercase(Locale.ROOT)
        return chineseCreditPrefixes.any(label::startsWith) ||
            shortChineseLabels.any { short ->
                label == short || label.startsWith("$short ") || label.startsWith("$short/") ||
                    short in shortRightsLabels && rightsSuffix.matches(label.drop(short.length))
            } ||
            englishCreditPrefixes.any { prefix ->
                label == prefix || label.startsWith("$prefix ") || label.startsWith("$prefix/") ||
                    prefix in setOf("op", "sp") && label.startsWith("$prefix-")
            }
    }

    private fun isTitleCard(text: String, title: String, artists: List<String>): Boolean {
        if (title.isBlank() || artists.isEmpty() || !titleSeparator.containsMatchIn(text)) return false
        val compact = TextNormalizer.compact(text)
        return title in compact && artists.any { it in compact }
    }
}
