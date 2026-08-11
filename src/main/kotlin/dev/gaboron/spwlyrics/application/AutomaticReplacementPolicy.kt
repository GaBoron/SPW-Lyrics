package dev.gaboron.spwlyrics.application

enum class LyricsLoadPhase {
    BEFORE_LOCAL,
    AFTER_LOCAL_MISSING,
}

enum class AutomaticReplacementPolicy(val configValue: String) {
    ALWAYS("always"),
    WHEN_LOCAL_MISSING("when_local_missing"),
    MANUAL_ONLY("manual_only");

    fun allowsAutomaticLoad(phase: LyricsLoadPhase): Boolean = when (this) {
        ALWAYS -> phase == LyricsLoadPhase.BEFORE_LOCAL
        WHEN_LOCAL_MISSING -> phase == LyricsLoadPhase.AFTER_LOCAL_MISSING
        MANUAL_ONLY -> false
    }

    companion object {
        fun fromConfigValue(value: String): AutomaticReplacementPolicy =
            entries.firstOrNull { it.configValue == value } ?: ALWAYS
    }
}
