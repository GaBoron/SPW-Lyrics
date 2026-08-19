package dev.gaboron.spwlyrics.application

import dev.gaboron.spwlyrics.domain.LyricLine
import dev.gaboron.spwlyrics.domain.TextNormalizer

/** Projects secondary text only when an alignment group's segmentation can be represented safely. */
internal object TranslationProjectionPolicy {
    fun project(alignment: CrossSourceAlignment): SecondaryLyricsProjection {
        val translations = linkedMapOf<Int, String>()
        val romanizations = linkedMapOf<Int, String>()
        alignment.groups.forEach { group ->
            projectGroup(group, LyricLine::translation).forEach(translations::putIfAbsent)
            projectGroup(group, LyricLine::romanization).forEach(romanizations::putIfAbsent)
        }
        return SecondaryLyricsProjection(translations, romanizations)
    }

    private fun projectGroup(
        group: CrossSourceAlignmentGroup,
        selector: (LyricLine) -> String?,
    ): Map<Int, String> = when (group.relation) {
        CrossSourceAlignmentRelation.ONE_TO_ONE -> projectOneToOne(group, selector)
        CrossSourceAlignmentRelation.ONE_TO_MANY -> projectOneToMany(group, selector)
        CrossSourceAlignmentRelation.MANY_TO_ONE -> projectManyToOne(group, selector)
        CrossSourceAlignmentRelation.MANY_TO_MANY -> projectManyToMany(group, selector)
    }

    private fun projectOneToOne(
        group: CrossSourceAlignmentGroup,
        selector: (LyricLine) -> String?,
    ): Map<Int, String> {
        val value = selector(group.secondaryLines.single()).cleanSecondaryText() ?: return emptyMap()
        return mapOf(group.primaryIndices.single() to value)
    }

    private fun projectOneToMany(
        group: CrossSourceAlignmentGroup,
        selector: (LyricLine) -> String?,
    ): Map<Int, String> {
        val parts = group.secondaryLines.map { selector(it).cleanSecondaryText() ?: return emptyMap() }
        val primaryText = group.primaryLines.single().text
        return mapOf(group.primaryIndices.single() to joinParts(parts, primaryText))
    }

    private fun projectManyToOne(
        group: CrossSourceAlignmentGroup,
        selector: (LyricLine) -> String?,
    ): Map<Int, String> {
        val combinedPrimary = group.primaryLines.joinToString("") { TextNormalizer.compact(it.text) }
        val secondaryOriginal = TextNormalizer.compact(group.secondaryLines.single().text)
        if (combinedPrimary != secondaryOriginal) return emptyMap()

        val value = selector(group.secondaryLines.single())?.takeIf(String::isNotBlank) ?: return emptyMap()
        val parts = splitExplicitly(value, group.primaryIndices.size) ?: return emptyMap()
        return group.primaryIndices.zip(parts).toMap()
    }

    private fun projectManyToMany(
        group: CrossSourceAlignmentGroup,
        selector: (LyricLine) -> String?,
    ): Map<Int, String> {
        if (group.primaryLines.size != group.secondaryLines.size) return emptyMap()
        val pairs = group.primaryLines.zip(group.secondaryLines)
        if (pairs.any { (primary, secondary) -> !isReliablePair(primary, secondary) }) return emptyMap()
        val values = group.secondaryLines.map { selector(it).cleanSecondaryText() ?: return emptyMap() }
        return group.primaryIndices.zip(values).toMap()
    }

    private fun isReliablePair(primary: LyricLine, secondary: LyricLine): Boolean {
        val primaryText = TextNormalizer.compact(primary.text)
        val secondaryText = TextNormalizer.compact(secondary.text)
        if (primaryText.isEmpty() || secondaryText.isEmpty()) return false
        val lengthBalance = minOf(primaryText.length, secondaryText.length).toDouble() /
            maxOf(primaryText.length, secondaryText.length)
        return TextNormalizer.similarity(primary.text, secondary.text) >= MIN_PAIR_SIMILARITY &&
            lengthBalance >= MIN_PAIR_LENGTH_BALANCE
    }

    private fun splitExplicitly(value: String, expectedParts: Int): List<String>? {
        val parts = value.split(EXPLICIT_BOUNDARY)
            .map(String::trim)
            .filter(String::isNotBlank)
        return parts.takeIf { it.size == expectedParts }
    }

    private fun joinParts(parts: List<String>, primaryText: String): String {
        val separator = when {
            primaryText.any { it == ';' || it == '；' } -> if (parts.containsCjk()) "；" else "; "
            primaryText.any { it == ',' || it == '，' || it == '、' } -> if (parts.containsCjk()) "，" else ", "
            primaryText.any { it == ':' || it == '：' } -> if (parts.containsCjk()) "：" else ": "
            else -> " "
        }
        return parts.joinToString(separator)
    }

    private fun List<String>.containsCjk(): Boolean = any { CJK_TEXT.containsMatchIn(it) }

    private fun String?.cleanSecondaryText(): String? = this
        ?.replace(Regex("[\\r\\n]+"), " ")
        ?.trim()
        ?.takeIf(String::isNotBlank)

    private val EXPLICIT_BOUNDARY = Regex("""\s*(?:[\r\n]+|[,，;；。.!！?？、/／|｜]+)\s*""")
    private val CJK_TEXT = Regex("""[\p{IsHan}\p{IsHiragana}\p{IsKatakana}]""")
    private const val MIN_PAIR_SIMILARITY = 0.88
    private const val MIN_PAIR_LENGTH_BALANCE = 0.65
}

internal data class SecondaryLyricsProjection(
    val translations: Map<Int, String>,
    val romanizations: Map<Int, String>,
)
