package dev.gaboron.spwlyrics.integration.batchui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moriafly.salt.ui.*
import com.moriafly.salt.ui.icons.Check
import com.moriafly.salt.ui.icons.SaltIcons
import com.moriafly.salt.ui.icons.Uncheck
import dev.gaboron.spwlyrics.application.LyricsBatchItem
import dev.gaboron.spwlyrics.application.LyricsBatchItemState
import dev.gaboron.spwlyrics.integration.workspace.lyricsSelection

/** One interaction surface includes the checkbox, metadata and status within the same rounded bounds. */
@Composable
internal fun BatchTrackRow(item: LyricsBatchItem, checked: Boolean, highlighted: Boolean, enabled: Boolean, select: () -> Unit) {
    Row(Modifier.fillMaxWidth().lyricsSelection(highlighted)
        .toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = { select() })
        .heightIn(min = SaltTheme.dimens.item).alpha(if (enabled) 1f else .5f).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(rememberVectorPainter(if (checked) SaltIcons.Check else SaltIcons.Uncheck), null,
            Modifier.size(SaltTheme.dimens.itemIcon), tint = SaltTheme.colors.highlight)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.query.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.query.artists.joinToString(" / ").ifBlank { item.query.album },
                color = SaltTheme.colors.subText, style = SaltTheme.textStyles.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        BatchItemStatus(item, Modifier.widthIn(max = 180.dp))
    }
}

@Composable
private fun BatchItemStatus(item: LyricsBatchItem, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(when (item.state) {
            LyricsBatchItemState.WAITING -> "等待处理"
            LyricsBatchItemState.SEARCHING -> "搜索中 ${(item.progress * 100).toInt()}%"
            LyricsBatchItemState.COMPLETED -> "已缓存"
            LyricsBatchItemState.FAILED -> "需要重试"
            LyricsBatchItemState.CACHED -> "已有缓存"
            LyricsBatchItemState.EXCLUDED -> "未选择"
            LyricsBatchItemState.CANCELLED -> "已停止"
        }, style = SaltTheme.textStyles.sub, color = when (item.state) {
            LyricsBatchItemState.FAILED -> SaltTheme.colors.error
            LyricsBatchItemState.SEARCHING, LyricsBatchItemState.COMPLETED -> SaltTheme.colors.highlight
            else -> SaltTheme.colors.subText
        })
        Text(listOf(item.source, item.quality).filter(String::isNotBlank).joinToString(" · ").ifBlank { item.stage.ifBlank { item.message } },
            color = SaltTheme.colors.subText, style = SaltTheme.textStyles.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
