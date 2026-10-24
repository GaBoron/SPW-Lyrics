package dev.gaboron.spwlyrics.integration.timingui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.Text
import dev.gaboron.spwlyrics.codec.LyricEditTime
import dev.gaboron.spwlyrics.integration.composeui.DesktopButton
import dev.gaboron.spwlyrics.integration.workspace.LyricsMenu
import java.util.Locale

@Composable
internal fun TimingTransport(controller: LyricsTimingController) {
    val snapshot = controller.playback
    val enabled = controller.state.available && !controller.state.busy
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("宿主播放位置  ${LyricEditTime.format(snapshot?.positionMs ?: 0)} / ${LyricEditTime.format(snapshot?.durationMs ?: 0)}")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DesktopButton(if (snapshot?.playing == true) "暂停 · Space" else "播放 · Space", controller::playPause, enabled = enabled)
            DesktopButton("−1 秒", { controller.seek(-1000) }, enabled = enabled)
            DesktopButton("+1 秒", { controller.seek(1000) }, enabled = enabled)
            DesktopButton("定位当前词", { controller.seekToCursor() }, enabled = enabled && controller.state.cursor != null)
            val rate = snapshot?.rate ?: 1f
            LyricsMenu(String.format(Locale.ROOT, "倍速 %.2f×", rate), listOf(.5f, .6f, .7f, .75f, .8f, .9f, 1f, 1.25f, 1.5f, 2f), rate,
                { String.format(Locale.ROOT, "%.2f×", it) }, controller::rate, enabled = enabled)
            DesktopButton("恢复 1×", { controller.rate(1f) }, enabled = enabled && rate != 1f)
        }
    }
}
