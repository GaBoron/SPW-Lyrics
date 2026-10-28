package dev.gaboron.spwlyrics.domain

enum class ProviderSearchPhase(val label: String) {
    NOT_CHECKED("未检查"), NOT_CONFIGURED("未配置"), SEARCHING("处理中"), AVAILABLE("可用"), FAILED("失败"), TIMED_OUT("超时")
}

/** Describes the actual search/index operation, never an independent connectivity probe. */
data class ProviderSearchStatus(
    val source: LyricsSource,
    val phase: ProviderSearchPhase = ProviderSearchPhase.NOT_CHECKED,
    val detail: String = "本次尚未请求此词源。",
    /** A shared background update completed after the search snapshot was taken. */
    val resultsChanged: Boolean = false,
) {
    companion object {
        fun unchecked(): Map<LyricsSource, ProviderSearchStatus> = LyricsSource.entries
            .filter { it != LyricsSource.LOCAL }.associateWith { ProviderSearchStatus(it) }
    }
}
