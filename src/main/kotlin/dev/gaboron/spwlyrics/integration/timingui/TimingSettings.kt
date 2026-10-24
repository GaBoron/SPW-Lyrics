package dev.gaboron.spwlyrics.integration.timingui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gaboron.spwlyrics.domain.*
import dev.gaboron.spwlyrics.integration.composeui.*
import dev.gaboron.spwlyrics.integration.workspace.*

@Composable
internal fun TimingSettings(controller: LyricsTimingController) {
    val state = controller.state
    var expanded by rememberSaveable { mutableStateOf(false) }
    var offset by rememberSaveable { mutableStateOf(state.options.offsetMs.toString()) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DesktopButton(if (expanded) "收起打轴设置" else "打轴设置与分词", { expanded = !expanded })
            DesktopButton("撤销 · Ctrl+Z", { controller.undo() }, enabled = state.canUndo && !state.busy)
            DesktopButton("重做 · Ctrl+Y", { controller.undo(true) }, enabled = state.canRedo && !state.busy)
            DesktopButton("跳过本句", controller::skipLine, enabled = state.cursor != null && !state.busy)
        }
        AnimatedVisibility(expanded, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DesktopCheck(state.options.smartFirst, { controller.options(state.options.copy(smartFirst = !state.options.smartFirst)) }, !state.busy, "智能首词")
                    DesktopCheck(state.options.smartLast, { controller.options(state.options.copy(smartLast = !state.options.smartLast)) }, !state.busy, "智能尾词")
                    LyricsMenu("判定：${state.judge.label}", TimingJudge.entries.toList(), state.judge, { it.label }, controller::judge, enabled = !state.busy)
                    val beats = state.emptyBeats[state.cursor] ?: 0
                    LyricsMenu("当前词空拍：$beats", (0..16).toList(), beats, { it.toString() }, controller::emptyBeats, enabled = state.cursor != null && !state.busy)
                    DesktopField(offset, { value ->
                        if (value.length <= 5 && value.matches(Regex("-?\\d*"))) {
                            offset = value
                            value.toIntOrNull()?.takeIf { it in -2000..2000 }?.let { controller.options(state.options.copy(offsetMs = it)) }
                        }
                    }, Modifier.width(110.dp), enabled = !state.busy, suffix = "ms", placeholder = "打轴补偿",
                        invalid = offset.toIntOrNull()?.let { it !in -2000..2000 } ?: true)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TimingSplit.entries.forEach { mode -> DesktopButton("${mode.label}分词", { controller.prepare(mode) },
                        enabled = state.lines.isNotEmpty() && !state.busy && !state.preparing) }
                }
                LyricsStatus("智能首词：首词的首次 G 只记词首；智能尾词：句尾 G 不衔接下一句。空拍用于拖长音，正补偿让记录时间延后。分词仅处理无逐字时间的纯文本行；已有逐字和扩展标记保留。")
            }
        }
    }
}
