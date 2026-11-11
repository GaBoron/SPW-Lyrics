package dev.gaboron.spwlyrics.integration.tutorialui

import androidx.compose.runtime.*

internal interface TutorialTarget

internal interface TutorialStep {
    val title: String
    val detail: String
    val target: TutorialTarget
}

internal enum class TutorialKind(val storageKey: String) { SEARCH("search"), METADATA("metadata"), BATCH("batch") }

internal interface TutorialPreferences {
    fun completed(kind: TutorialKind): Boolean
    fun saveCompleted(kind: TutorialKind, value: Boolean): Boolean
}

/** Each page owns session progress; completion and skipping persist independently. */
internal class TutorialController<S : TutorialStep>(
    val steps: List<S>,
    completed: () -> Boolean,
    private val saveCompleted: (Boolean) -> Boolean,
    private val onFinished: () -> Unit = {},
) {
    init { require(steps.isNotEmpty()) }
    var active by mutableStateOf(!completed())
        private set
    var index by mutableIntStateOf(0)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    val step get() = steps[index]

    fun previous() {
        index = (index - 1).coerceAtLeast(0)
        error = null
    }

    fun next() {
        if (index == steps.lastIndex) finish()
        else {
            index++
            error = null
        }
    }

    fun finish() {
        if (!active) return
        if (saveCompleted(true)) {
            active = false
            error = null
            onFinished()
        } else error = "未能记住教程状态，请检查插件数据目录的写入权限后重试。"
    }

    fun reset() {
        index = 0
        active = true
        error = null
    }
}
