package dev.gaboron.spwlyrics.integration.timingui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalUriHandler
import com.moriafly.salt.ui.*
import dev.gaboron.spwlyrics.domain.TimingAudioDependency
import dev.gaboron.spwlyrics.integration.composeui.DesktopButton
import dev.gaboron.spwlyrics.integration.workspace.LyricsStatus

@OptIn(UnstableSaltUiApi::class)
@Composable
internal fun TimingRuntimeNotice(dependency: TimingAudioDependency) {
    val uri = LocalUriHandler.current
    AnimatedVisibility(!dependency.available, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        JustifiedRow(startContent = { LyricsStatus("频谱需要安装并启用 SPW Media Runtime") }, endContent = {
            if (dependency.installUrl.isNotBlank()) DesktopButton(dependency.installLabel, { uri.openUri(dependency.installUrl) })
        }, verticalAlignment = Alignment.CenterVertically)
    }
}
