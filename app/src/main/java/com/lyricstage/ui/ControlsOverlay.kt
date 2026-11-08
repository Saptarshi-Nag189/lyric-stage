package com.lyricstage.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lyricstage.R
import com.lyricstage.background.LiquidGlassField
import com.lyricstage.core.clock.PlaybackClock
import com.lyricstage.core.stage.TemplateId
import com.lyricstage.media.NowPlaying
import com.lyricstage.media.QueueState
import com.lyricstage.settings.StageSettings
import com.lyricstage.ui.theme.Ui
import kotlinx.coroutines.delay

/**
 * Tap-to-show controls. Top: artwork, title, the queue, settings and the layout pills.
 * Bottom, one glass card above the gesture area: seek bar, times, and the transport with
 * shuffle and repeat flanking it, the way a music player lays them out. The card is cut
 * from the same liquid field as the background behind it when glass controls are on, so
 * it reads as glass lying over the stage; otherwise it is the quiet dark card. On wide
 * screens the card and the header sit in a centred column, not stretched edge to edge.
 */
@Composable
fun ControlsOverlay(
    nowPlaying: NowPlaying,
    clock: PlaybackClock,
    settings: StageSettings,
    accent: Color,
    queue: QueueState?,
    /** The shared liquid field; null leaves the plain card. */
    field: LiquidGlassField?,
    stageSize: IntSize,
    shuffleOn: Boolean,
    /** 0 off, 1 repeat one, 2 repeat all. */
    repeatMode: Int,
    onInteract: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onTemplate: (TemplateId?) -> Unit,
    onSettings: () -> Unit,
    onQueue: () -> Unit,
    onShuffle: (Boolean) -> Unit,
    onRepeat: (Int) -> Unit,
) {
    val position by produceState(clock.smoothedPositionMs()) {
        while (true) {
            value = clock.smoothedPositionMs()
            delay(200)
        }
    }
    var dragging by remember { mutableStateOf<Float?>(null) }
    val duration = nowPlaying.durationMs ?: 0L
    val art = remember(nowPlaying.art) { nowPlaying.art?.asImageBitmap() }
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val column = if (wide) Modifier.fillMaxWidth().widthIn(max = 620.dp) else Modifier.fillMaxWidth()

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.55f), 0.3f to Color.Transparent, 0.7f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.45f))),
    ) {
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .then(column)
                .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(accent), contentAlignment = Alignment.Center) {
                    if (art != null) {
                        Image(art, stringResource(R.string.artwork), contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp))
                    }
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(nowPlaying.title, color = Ui.Text, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(nowPlaying.artist, color = Ui.Text.copy(alpha = 0.72f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (queue != null) {
                    CircleIconButton(Glyph.QUEUE, stringResource(R.string.queue), { onQueue(); onInteract() }, background = Color.Black.copy(alpha = 0.35f))
                    Spacer(Modifier.width(8.dp))
                }
                CircleIconButton(Glyph.MORE, stringResource(R.string.settings), onSettings, background = Color.Black.copy(alpha = 0.35f))
            }
            Spacer(Modifier.height(14.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    ChoicePill(stringResource(R.string.template_auto), settings.template == null, accent, { onTemplate(null); onInteract() }, onStage = true)
                }
                items(TemplateId.entries) { id ->
                    ChoicePill(id.label, settings.template == id, accent, { onTemplate(id); onInteract() }, onStage = true)
                }
            }
        }

        GlassPanel(
            frost = settings.frost,
            corner = 32.dp,
            stageSize = stageSize,
            field = field,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .then(column)
                // in immersive mode the nav bar inset is 0; keep clear of the swipe-up gesture zone
                .windowInsetsPadding(WindowInsets.mandatorySystemGestures)
                .padding(start = 12.dp, end = 12.dp, bottom = 20.dp),
        ) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 16.dp)) {
            if (duration > 0) {
                Slider(
                    value = dragging ?: (position.toFloat() / duration).coerceIn(0f, 1f),
                    onValueChange = {
                        dragging = it
                        onInteract()
                    },
                    onValueChangeFinished = {
                        dragging?.let { onSeek((it * duration).toLong()) }
                        dragging = null
                    },
                    enabled = nowPlaying.canSeek,
                    colors = SliderDefaults.colors(
                        thumbColor = Ui.Text,
                        activeTrackColor = accent,
                        inactiveTrackColor = Ui.Text.copy(alpha = 0.25f),
                    ),
                )
                Row {
                    Text(formatTime(dragging?.let { (it * duration).toLong() } ?: position), color = Ui.Text.copy(alpha = 0.72f), fontSize = 13.sp)
                    Spacer(Modifier.weight(1f))
                    Text(formatTime(duration), color = Ui.Text.copy(alpha = 0.72f), fontSize = 13.sp)
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val sideTint = Ui.Text.copy(alpha = 0.8f)
                GlyphButton(
                    Glyph.SHUFFLE,
                    stringResource(if (shuffleOn) R.string.shuffle_on else R.string.shuffle_off),
                    44, if (shuffleOn) accent else sideTint,
                ) { onShuffle(!shuffleOn); onInteract() }
                TransportButton(Transport.PREVIOUS, stringResource(R.string.previous), 52, Color.Transparent, Ui.Text) { onPrevious(); onInteract() }
                TransportButton(
                    if (nowPlaying.isPlaying) Transport.PAUSE else Transport.PLAY,
                    stringResource(if (nowPlaying.isPlaying) R.string.pause else R.string.play),
                    72, accent, Ui.OnAccent,
                ) { onPlayPause(); onInteract() }
                TransportButton(Transport.NEXT, stringResource(R.string.next), 52, Color.Transparent, Ui.Text) { onNext(); onInteract() }
                GlyphButton(
                    if (repeatMode == 1) Glyph.REPEAT_ONE else Glyph.REPEAT,
                    stringResource(when (repeatMode) { 1 -> R.string.repeat_one; 2 -> R.string.repeat_all; else -> R.string.repeat_off }),
                    44, if (repeatMode != 0) accent else sideTint,
                ) { onRepeat(if (repeatMode == 0) 2 else if (repeatMode == 2) 1 else 0); onInteract() }
            }
            }
        }
    }
}

@Composable
private fun TransportButton(kind: Transport, description: String, sizeDp: Int, background: Color, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(sizeDp.dp).clip(CircleShape).background(background).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        TransportIcon(kind, description, tint, if (sizeDp > 60) 32 else 28)
    }
}

/** A round button showing one of the line glyphs: shuffle, repeat and their kin. */
@Composable
private fun GlyphButton(glyph: Glyph, description: String, sizeDp: Int, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(sizeDp.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        DrawnIcon(glyph, description, tint, if (sizeDp > 60) 24 else 20)
    }
}

private fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
