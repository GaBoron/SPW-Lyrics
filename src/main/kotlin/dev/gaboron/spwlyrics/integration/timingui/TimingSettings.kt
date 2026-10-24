package dev.gaboron.spwlyrics.integration.timingui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.*
import com.moriafly.salt.ui.popup.*
import dev.gaboron.spwlyrics.domain.*
import dev.gaboron.spwlyrics.integration.composeui.*
import dev.gaboron.spwlyrics.integration.workspace.*

/** Salt item groups keep advanced options out of the transport and capture bars. */
@Composable
@OptIn(UnstableSaltUiApi::class)
internal fun TimingSettings(controller: LyricsTimingController) {
    val state = controller.state
    var offset by rememberSaveable { mutableStateOf(state.options.offsetMs.toString()) }
    LyricsScroll(Modifier.fillMaxWidth().heightIn(max = 230.dp)) {
        RoundedColumn(paddingValues = PaddingValues(0.dp)) {
            ItemSwitcher(state.options.smartFirst, { controller.options(state.options.copy(smartFirst = it)) },
                "智能首词", enabled = !state.busy, sub = "首词的第一次 G 只记录词首")
            ItemSwitcher(state.options.smartLast, { controller.options(state.options.copy(smartLast = it)) },
                "智能尾词", enabled = !state.busy, sub = "句尾 G 不衔接下一句")
            ItemDivider()
            TimingChoiceItem("时间判定", TimingJudge.entries.toList(), state.judge, { it.label }, controller::judge, !state.busy)
            TimingChoiceItem("所选词空拍", (0..16).toList(), state.emptyBeats[state.cursor] ?: 0, { it.toString() },
                controller::emptyBeats, state.cursor != null && !state.busy)
            ItemContainer {
                @OptIn(UnstableSaltUiApi::class)
                JustifiedRow(startContent = { Text("打轴补偿") }, endContent = {
                    DesktopField(offset, { value ->
                        if (value.length <= 5 && value.matches(Regex("-?\\d*"))) {
                            offset = value
                            value.toIntOrNull()?.takeIf { it in -2000..2000 }?.let { controller.options(state.options.copy(offsetMs = it)) }
                        }
                    }, Modifier.width(130.dp), enabled = !state.busy, suffix = "ms",
                        invalid = offset.toIntOrNull()?.let { it !in -2000..2000 } ?: true)
                }, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically)
            }
            ItemTip("正补偿让记录时间延后；空拍用于拖长音。")
        }
        RoundedColumn(paddingValues = PaddingValues(0.dp)) {
            TimingSplit.entries.forEach { mode ->
                ItemButton({ controller.prepare(mode) }, "${mode.label}自动分词",
                    enabled = state.lines.isNotEmpty() && !state.busy && !state.preparing)
            }
            ItemTip("进入打轴时自动准备未分词的纯文本行；复用同一套分词逻辑，保留已有逐字时间和扩展标记。")
        }
        RoundedColumn(paddingValues = PaddingValues(0.dp)) {
            ItemTip("F/G/H 记录词首、连续轴和词尾；A/D 上下词；W/S 上下句；R/Y 定位上下词；Home/End 定位句首尾；空格播放或暂停；←/→ 跳转 1 秒；[/] 调速；' 恢复 1×。输入框内不接管按键。")
        }
    }
}

@OptIn(UnstableSaltUiApi::class)
@Composable
private fun <T> TimingChoiceItem(text: String, choices: List<T>, selected: T, title: (T) -> String,
    select: (T) -> Unit, enabled: Boolean) {
    ItemDropdown(text = text, value = title(selected), enabled = enabled) {
        choices.forEach { choice ->
            PopupMenuItem({ select(choice); state.dismiss() }, text = title(choice), selected = choice == selected)
        }
    }
}
