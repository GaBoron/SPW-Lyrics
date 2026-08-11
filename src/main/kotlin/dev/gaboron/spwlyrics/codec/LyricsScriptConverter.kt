package dev.gaboron.spwlyrics.codec

import com.github.houbb.opencc4j.util.ZhConverterUtil
import dev.gaboron.spwlyrics.domain.LyricsDocument

object LyricsScriptConverter {
    fun toSimplifiedChinese(document: LyricsDocument): LyricsDocument = document.copy(
        lines = document.lines.map { line ->
            line.copy(
                text = line.text.toSimplifiedChinese(),
                words = line.words.map { word -> word.copy(text = word.text.toSimplifiedChinese()) },
                translation = line.translation?.toSimplifiedChinese(),
                romanization = line.romanization?.toSimplifiedChinese(),
            )
        },
        metadata = document.metadata.mapValues { (_, values) -> values.map { it.toSimplifiedChinese() } },
    )

    private fun String.toSimplifiedChinese(): String = ZhConverterUtil.toSimple(this)
        .replace('妳', '你')
}
