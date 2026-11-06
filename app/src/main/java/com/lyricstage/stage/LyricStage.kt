package com.lyricstage.stage

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import android.os.SystemClock
import com.lyricstage.audio.AudioOnsets
import com.lyricstage.core.clock.PlaybackClock
import com.lyricstage.core.lyrics.LyricsKind
import com.lyricstage.core.lyrics.Lyrics
import com.lyricstage.core.palette.StagePalette
import com.lyricstage.core.stage.LineTiming
import com.lyricstage.core.stage.Ripple
import com.lyricstage.core.stage.SizeF
import com.lyricstage.core.stage.TemplateId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * The kinetic lyrics stage. One frame loop reads the clock once per frame; drawing happens
 * in two Canvas passes (settled words, and fast-moving words under a blur layer on API 31+).
 * Nothing recomposes per frame.
 */
@Composable
fun LyricStage(
    lyrics: Lyrics,
    clock: PlaybackClock,
    palette: StagePalette,
    playSeed: Int,
    lockedTemplate: TemplateId?,
    fontScale: Float,
    glass: Float = 0f,
    frost: Float = 0f,
    highlightIntensity: Float,
    reduceMotion: Boolean,
    isPlaying: Boolean,
    /** Changes whenever the player reports a new position (seek, track change). */
    anchor: Long,
    /** Battery saver: words fade in whole, and the loop sleeps between changes. */
    simple: Boolean,
    /** Controls are open: the lyrics step back so the overlay stays readable. */
    dimmed: Boolean,
    showFps: Boolean,
    /** Vocal onsets for audio sync, or null when it is off. */
    audio: AudioOnsets?,
    onRipple: (Ripple) -> Unit,
    onVisibleTemplate: (TemplateId?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val resolver = LocalFontFamilyResolver.current
    var size by remember { mutableStateOf(IntSize.Zero) }
    // The size the stage is laid out for. The swipe-up gesture and the system bars briefly
    // change the window size; following each change re-laid every line mid-song (a line
    // could jump to other rows or vanish for a moment). Size changes are taken only while
    // the app is in front and once the size has held still, and the stage being shown
    // stays up until the new layout is ready.
    val resumed = LocalLifecycleOwner.current.lifecycle.currentStateAsState().value.isAtLeast(Lifecycle.State.RESUMED)
    var stageSize by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(size, resumed) {
        if (size == stageSize || size.width == 0 || size.height == 0) return@LaunchedEffect
        if (stageSize != IntSize.Zero) {
            if (!resumed) return@LaunchedEffect
            delay(SIZE_SETTLE_MS)
        }
        stageSize = size
    }

    val prepared by produceState<PreparedStage?>(null, lyrics, stageSize, playSeed, lockedTemplate, reduceMotion, fontScale, density) {
        val size = stageSize
        // another song's lines must never show while this one is prepared
        if (value?.plan?.lyrics !== lyrics) value = null
        if (size.width == 0 || size.height == 0 || lyrics.lines.isEmpty()) {
            value = null
            return@produceState
        }
        value = withContext(Dispatchers.Default) {
            // prepare builds one cacheless measurer per worker itself; the resolver is the
            // only shared thing, and its caches are synchronized
            PreparedStage.prepare(
                lyrics, SizeF(size.width.toFloat(), size.height.toFloat()), resolver, density,
                playSeed, lockedTemplate, reduceMotion, fontScale,
            )
        }
    }

    val frame = remember { StageFrame() }
    val renderer = remember { StageRenderer() }
    val colours = remember(palette, highlightIntensity, glass, frost) { StageColours(palette, highlightIntensity, glass, frost) }
    val ripple by rememberUpdatedState(onRipple)
    val visibleTemplate by rememberUpdatedState(onVisibleTemplate)
    val calm by rememberUpdatedState(reduceMotion)
    val onsets by rememberUpdatedState(audio)
    val prep = prepared

    // Runs while playing; when paused it draws for a moment more (the dim and any entrance
    // finish) and then stops until playback or position changes. In battery saver mode it
    // also sleeps between word changes instead of drawing every frame.
    LaunchedEffect(prep, clock, isPlaying, anchor, simple) {
        frame.tracker.reset()
        var lastTemplate: TemplateId? = null
        var stopAt = -1L
        while (isActive) {
            val nanos = withFrameNanos { nanos ->
                val pos = clock.currentPositionMs()
                frame.positionMs = pos
                if (prep != null) {
                    val li = LineTiming.visibleLine(prep.plan.lyrics, pos)
                    frame.line = li
                    frame.wordShiftMs = alignWords(frame, onsets, prep, li, pos)
                    frame.blur.floatValue = renderer.motionBlur(prep, li, pos - frame.wordShiftMs, calm)
                    frame.ripples.clear()
                    if (!calm) frame.tracker.update(prep.plan, pos, frame.ripples)
                    for (r in frame.ripples) ripple(r)
                    val t = if (li >= 0) prep.plan.templates[li] else null
                    if (t != null && t != lastTemplate) {
                        lastTemplate = t
                        visibleTemplate(t)
                    }
                } else {
                    frame.line = -1
                    frame.blur.floatValue = 0f
                }
                frame.countFrame(nanos)
                frame.tick.longValue = nanos
                nanos
            }
            if (!isPlaying) {
                if (stopAt < 0) stopAt = nanos + PAUSED_TAIL_NS
                if (nanos > stopAt) break
            } else if (simple && prep != null) {
                val idle = renderer.idleMs(prep, frame.line, frame.positionMs - frame.wordShiftMs)
                if (idle > IDLE_MIN_MS) delay(idle.coerceAtMost(IDLE_MAX_MS))
            }
        }
    }

    // the stage dims while paused (holding the current line) and while the controls are open
    val target = when {
        dimmed -> 0.3f
        !isPlaying -> 0.55f
        else -> 1f
    }
    val dim by animateFloatAsState(target, tween(300), label = "stage-dim")

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .graphicsLayer { alpha = dim },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            frame.tick.longValue
            val p = prep ?: return@Canvas
            with(renderer) {
                if (frame.line < 0) drawGapDots(p, frame.positionMs, colours, reduceMotion)
                else drawLine(p, frame.line, frame.positionMs, colours, reduceMotion, StageLayer.SETTLED, frame.wordShiftMs, sweep = !simple)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val b = frame.blur.floatValue
                        renderEffect = if (b > 0.5f) BlurEffect(b, b, TileMode.Decal) else null
                    },
            ) {
                frame.tick.longValue
                val p = prep ?: return@Canvas
                with(renderer) { drawLine(p, frame.line, frame.positionMs, colours, reduceMotion, StageLayer.MOTION, frame.wordShiftMs, sweep = !simple) }
            }
        }
        if (showFps) {
            val fps by frame.fps
            Text(
                "$fps fps",
                color = if (fps >= 55) Color(0xFF30D158) else Color(0xFFFF9F0A),
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp),
            )
        }
    }
}

/**
 * Audio sync: converts fresh vocal onsets to playback time and lets the aligner nudge the
 * current line's estimated words. Lyrics with real word timings are left alone.
 */
private fun alignWords(frame: StageFrame, audio: AudioOnsets?, prep: PreparedStage, li: Int, pos: Long): Long {
    if (audio == null || li < 0 || prep.plan.lyrics.kind != LyricsKind.LINE_SYNCED) {
        frame.aligner.reset()
        audio?.onsets?.clear()
        return 0
    }
    val now = SystemClock.elapsedRealtime()
    frame.onsetScratch.clear()
    while (true) {
        val t = audio.onsets.poll() ?: break
        frame.onsetScratch += pos - (now - t)
    }
    frame.aligner.update(li, prep.plan.lines[li], frame.onsetScratch)
    return frame.aligner.shiftMs
}

/** After pausing, keep drawing this long so the dim and any entrance can finish. */
private const val PAUSED_TAIL_NS = 700_000_000L
/** How long a new stage size must hold before the lines are laid out again. */
private const val SIZE_SETTLE_MS = 350L
private const val IDLE_MIN_MS = 40L
private const val IDLE_MAX_MS = 1_000L
