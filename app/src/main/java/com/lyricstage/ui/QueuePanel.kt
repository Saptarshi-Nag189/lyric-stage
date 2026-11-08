package com.lyricstage.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lyricstage.R
import com.lyricstage.background.LiquidGlassField
import com.lyricstage.media.QueueState
import com.lyricstage.ui.theme.Ui

/**
 * The player's own queue, as the media session reports it: the song playing is marked,
 * tapping a song jumps the player to it. On a wide screen (tablets, landscape) the list
 * stands at the right of the stage; on a phone it rises from the bottom above the controls.
 * Glass controls make the panel a pane of the same liquid field the stage is showing.
 */
@Composable
fun QueuePanel(
    queue: QueueState,
    accent: Color,
    /** The shared liquid field; null leaves the plain card. */
    field: LiquidGlassField?,
    stageSize: IntSize,
    frost: Float,
    onSkip: (Long) -> Unit,
    onClose: () -> Unit,
) {
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val listState = rememberLazyListState()

    // open with the playing song in view, a little way from the top
    LaunchedEffect(queue.items) {
        val i = queue.items.indexOfFirst { it.id == queue.activeId }
        if (i >= 0) listState.scrollToItem((i - 2).coerceAtLeast(0))
    }

    val base = Modifier
        .then(
            if (wide) Modifier.fillMaxHeight().width(380.dp).padding(vertical = 12.dp, horizontal = 12.dp)
            else Modifier.fillMaxWidth().heightIn(max = 560.dp).padding(horizontal = 12.dp, vertical = 12.dp)
        )

    GlassPanel(
        frost = frost,
        corner = 28.dp,
        stageSize = stageSize,
        field = field,
        fallback = Ui.Card.copy(alpha = 0.97f),
        modifier = base,
    ) {
        Column(
            Modifier
                .then(if (wide) Modifier.statusBarsPadding().navigationBarsPadding() else Modifier)
                .padding(top = 18.dp, bottom = 8.dp),
        ) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.queue), color = Ui.Text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text(
                    pluralStringResource(R.plurals.queue_count, queue.items.size, queue.items.size),
                    color = Ui.Muted, fontSize = 13.sp,
                )
            }
            CircleIconButton(Glyph.CLOSE, stringResource(R.string.close_queue), onClose, background = Color.Transparent)
        }
        Spacer(Modifier.height(6.dp))
        LazyColumn(state = listState, modifier = Modifier.weight(1f, fill = false)) {
            itemsIndexed(queue.items, key = { _, it -> it.id }) { i, item ->
                val active = item.id == queue.activeId
                QueueRow(
                    index = i + 1,
                    title = item.title,
                    artist = item.artist,
                    active = active,
                    accent = accent,
                    enabled = queue.canSkipToQueue && !active,
                    onClick = { onSkip(item.id) },
                )
            }
        }
        if (!wide) Spacer(Modifier.navigationBarsPadding())
        }
    }
}

/** One song of the queue; the one playing is marked and not tappable. */
@Composable
private fun QueueRow(
    index: Int,
    title: String,
    artist: String?,
    active: Boolean,
    accent: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    HorizontalDivider(color = Ui.Raised)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val number = if (active) null else index.toString()
        Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
            when {
                active -> DrawnIcon(Glyph.NOTE, null, accent, 16)
                number != null -> Text(number, color = Ui.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(
                title,
                color = if (active) accent else Ui.Text,
                fontSize = 16.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!artist.isNullOrBlank()) {
                Text(artist, color = Ui.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
