package dev.gaboron.spwlyrics.integration.timingui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.*
import dev.gaboron.spwlyrics.codec.LyricEditTime
import dev.gaboron.spwlyrics.integration.composeui.DesktopButton
import dev.gaboron.spwlyrics.integration.workspace.LyricsLayoutTransition
import dev.gaboron.spwlyrics.integration.workspace.LyricsMenu
import java.util.Locale

@OptIn(UnstableSaltUiApi::class)
@Composable
internal fun TimingTransport(controller: LyricsTimingController) {
    val snapshot = controller.playback
    val enabled = controller.state.available && !controller.state.busy
    ItemContainer {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            LyricsLayoutTransition(maxWidth >= 680.dp, Modifier.fillMaxWidth()) { wide ->
                val commands: @Composable () -> Unit = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        DesktopButton(if (snapshot?.playing == true) "暂停 Space" else "播放 Space", controller::playPause,
                            Modifier.width(104.dp), enabled = enabled, primary = true)
                        DesktopButton("−1 秒", { controller.seek(-1000) }, enabled = enabled)
                        DesktopButton("+1 秒", { controller.seek(1000) }, enabled = enabled)
                    }
                }
                val position: @Composable () -> Unit = {
                    Text("${LyricEditTime.format(snapshot?.positionMs ?: 0)} / ${LyricEditTime.format(snapshot?.durationMs ?: 0)}",
                        style = SaltTheme.textStyles.sub.copy(fontFamily = FontFamily.Monospace), maxLines = 1)
                }
                val speed: @Composable () -> Unit = {
                    val rate = snapshot?.rate ?: 1f
                    LyricsMenu(String.format(Locale.ROOT, "%.2f×", rate), listOf(.5f, .6f, .7f, .75f, .8f, .9f, 1f, 1.25f, 1.5f, 2f),
                        rate, { String.format(Locale.ROOT, "%.2f×", it) }, controller::rate, enabled = enabled)
                }
                if (wide) JustifiedRow(startContent = commands, endContent = {
                    Row(horizontalArrangement = Arrangement.spacedBy(SaltTheme.dimens.padding),
                        verticalAlignment = Alignment.CenterVertically) { position(); speed() }
                }, verticalAlignment = Alignment.CenterVertically)
                else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    JustifiedRow(startContent = commands, endContent = speed, verticalAlignment = Alignment.CenterVertically)
                    position()
                }
            }
        }
    }
}
