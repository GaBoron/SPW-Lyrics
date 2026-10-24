package dev.gaboron.spwlyrics.integration

import com.xuncorp.spw.workshop.api.Channel
import dev.gaboron.spwlyrics.domain.TimingAudioDependency
import dev.gaboron.spwmedia.sdk.MediaRuntime

/** Distribution comes from the host's public PluginContext, never an installation-path guess. */
internal class TimingRuntimeInstall(private val channel: Channel) {
    fun dependency() = if (channel == Channel.Steam) TimingAudioDependency(MediaRuntime.isInstalled(),
        "https://steamcommunity.com/sharedfiles/filedetails/?id=3824060316", "在 Steam 创意工坊安装")
    else TimingAudioDependency(MediaRuntime.isInstalled(), "https://github.com/GaBoron/SPW-Media-Runtime/releases/latest", "从 GitHub 安装")
}
