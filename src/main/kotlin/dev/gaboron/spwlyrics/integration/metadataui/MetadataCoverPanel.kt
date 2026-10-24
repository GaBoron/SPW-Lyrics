package dev.gaboron.spwlyrics.integration.metadataui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import dev.gaboron.spwlyrics.codec.AudioArtworkImages
import dev.gaboron.spwlyrics.domain.AudioArtwork
import dev.gaboron.spwlyrics.integration.composeui.DesktopButton
import dev.gaboron.spwlyrics.integration.workspace.LyricsStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun MetadataCoverPanel(controller: MetadataEditorController) {
    val state = controller.state
    val enabled = state.loaded != null && !state.busy
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("封面", color = SaltTheme.colors.subText)
        MetadataCoverPreview(state.draft.cover, "歌曲封面")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DesktopButton("选图", controller::importCover,
                Modifier.weight(1f).semantics { contentDescription = "选择封面图片" }, enabled = enabled)
            DesktopButton("移除", controller::removeCover,
                Modifier.weight(1f).semantics { contentDescription = "移除全部内嵌封面" }, enabled = enabled && state.draft.artwork.isNotEmpty())
            DesktopButton("恢复", controller::restoreCover,
                Modifier.weight(1f).semantics { contentDescription = "恢复原封面" },
                enabled = enabled && state.draft.artwork != state.loaded.metadata.artwork)
        }
        LyricsStatus("JPEG / PNG，最多 20 MiB、2500 万像素。替换正面封面时保留其他图片；移除会清除全部内嵌图片。修改先记入草稿。")
    }
}

@Composable
internal fun MetadataCoverPreview(artwork: AudioArtwork?, description: String) {
    val preview by produceState<Result<ImageBitmap>?>(null, artwork) {
        value = null
        if (artwork != null) value = withContext(Dispatchers.IO) {
            runCatching {
                val image = AudioArtworkImages.thumbnail(artwork)
                try { image.toComposeImageBitmap() } finally { image.flush() }
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(160.dp).clip(SaltTheme.shapes.medium)
        .background(SaltTheme.colors.background), contentAlignment = Alignment.Center) {
        val bitmap = preview?.getOrNull()
        if (bitmap != null) Image(bitmap, description, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        else Text(when {
            artwork == null -> "暂无封面"
            preview == null -> "正在读取封面…"
            else -> "封面无法预览，可选择其他图片替换"
        }, color = SaltTheme.colors.subText)
    }
}
