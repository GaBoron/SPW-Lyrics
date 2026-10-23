package dev.gaboron.spwlyrics.domain

import java.text.Normalizer
import java.util.Locale

/** Only a document consisting entirely of explicit instrumental notices is instrumental. */
internal object InstrumentalLyricsClassifier {
    private val notices = setOf(
        "纯音乐", "纯音乐请欣赏", "纯音乐敬请欣赏", "此歌曲为纯音乐", "本歌曲为纯音乐",
        "此歌曲为纯音乐请欣赏", "本歌曲为纯音乐请欣赏",
        "此歌曲为没有填词的纯音乐请您欣赏", "此歌曲为没有填词的纯音乐请欣赏",
        "没有填词的纯音乐请欣赏", "instrumental", "instrumentalmusic", "instrumentalpleaseenjoy",
        "instrumentalmusicpleaseenjoy", "thisisaninstrumental", "thisisaninstrumentalpleaseenjoy",
    )

    fun matches(lines: List<LyricLine>): Boolean {
        val text = lines.flatMap { listOfNotNull(it.text, it.translation, it.romanization) }.filter(String::isNotBlank)
        return text.isNotEmpty() && text.all { normalize(it) in notices }
    }

    private fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).replace('純', '纯').replace('樂', '乐').replace('賞', '赏')
        .replace('為', '为').replace('詞', '词').replace('沒', '没').replace('請', '请')
        .filter(Char::isLetterOrDigit)
}
