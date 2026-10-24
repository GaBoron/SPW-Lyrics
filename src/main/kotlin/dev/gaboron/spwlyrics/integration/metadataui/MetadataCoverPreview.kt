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
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import dev.gaboron.spwlyrics.codec.AudioArtworkImages
import dev.gaboron.spwlyrics.domain.AudioArtwork
import dev.gaboron.spwlyrics.integration.workspace.LyricsTransition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface CoverPreview {
    data object Empty : CoverPreview
    data object Loading : CoverPreview
    data object Failed : CoverPreview
    data class Image(val bitmap: ImageBitmap) : CoverPreview
}

@Composable
internal fun MetadataCoverPreview(artwork: AudioArtwork?, description: String) {
    val preview by produceState<CoverPreview>(CoverPreview.Empty, artwork) {
        value = if (artwork == null) CoverPreview.Empty else {
            value = CoverPreview.Loading
            withContext(Dispatchers.IO) {
                runCatching {
                    val image = AudioArtworkImages.thumbnail(artwork)
                    try { CoverPreview.Image(image.toComposeImageBitmap()) } finally { image.flush() }
                }.getOrDefault(CoverPreview.Failed)
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(160.dp).clip(SaltTheme.shapes.medium)
        .background(SaltTheme.colors.background), contentAlignment = Alignment.Center) {
        LyricsTransition(preview, Modifier.fillMaxSize()) { shown ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (shown is CoverPreview.Image) Image(shown.bitmap, description, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                else Text(when (shown) {
                    CoverPreview.Empty -> "暂无封面"
                    CoverPreview.Loading -> "正在读取封面…"
                    else -> "封面无法预览，可选择其他图片替换"
                }, color = SaltTheme.colors.subText)
            }
        }
    }
}
