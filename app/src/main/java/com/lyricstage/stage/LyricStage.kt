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
import com.lyricstage.background.StageGlassText
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
import kotlin.math.max

/**
 * The kinetic lyrics stage. One frame loop reads the clock once per frame; drawing happens
 * in two Canvas passes (settled words, and fast-moving words under a blur layer on API 31+).
 * Nothing recomposes per frame.
 *
 * The words can be drawn as glass two ways. Without a shader to look through, each word is
 * drawn with the classic glass treatment inside the drawing. With one (the liquid
 * background on Android 13+, through [glassText]) the whole layer is turned into
 * Apple-style glass by a shader outside the drawing: the rim of every glyph refracts the
 * background and the middle is clear. While the controls are open ([dimmed]) the line is
 * glass either way, instead of dimmed.
 */
@Composable
fun LyricStage(
    lyrics: Lyrics,
    clock: PlaybackClock,
    palette: StagePalette,
    playSeed: Int,
    lockedTemplate: TemplateId?,
    /** The layouts Auto picks from, when no template is locked. Empty means all of them. */
    pool: Collection<TemplateId> = TemplateId.entries,
    fontScale: Float,
    lyricFont: LyricFont,
    glass: Float = 0f,
    frost: Float = 0f,
    highlightIntensity: Float,
    reduceMotion: Boolean,
    isPlaying: Boolean,
    /** Changes whenever the player reports a new position (seek, track change). */
    anchor: Long,
    /** Battery saver: words fade in whole, and the loop sleeps between changes. */
    simple: Boolean,
    /** Controls are open: the line reads as glass rather than dimming back. */
    dimmed: Boolean,
    /** Shader-drawn glass for the line, over the liquid field; null when there is none. */
    glassText: StageGlassText? = null,
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

    val prepared by produceState<PreparedStage?>(null, lyrics, stageSize, playSeed, lockedTemplate, pool, reduceMotion, fontScale, density, lyricFont) {
        val size = stageSize
        // another song's lines must never show while this one is prepared
        if (value?.plan?.lyrics !== lyrics) value = null
        if (size.width == 0 || size.height == 0 || lyrics.lines.isEmpty()) {
            value = null
            return@produceState
        }
        StageFonts.lyricFont = lyricFont
        value = withContext(Dispatchers.Default) {
            // prepare builds one cacheless measurer per worker itself; the resolver is the
            // only shared thing, and its caches are synchronized
            PreparedStage.prepare(
                lyrics, SizeF(size.width.toFloat(), size.height.toFloat()), resolver, density,
                playSeed, lockedTemplate, reduceMotion, fontScale, pool,
            )
        }
    }

    val frame = remember { StageFrame() }
    val renderer = remember { StageRenderer() }

    // Glass: the line is glass while the controls are open whatever the setting says, and
    // the shader-drawn glass takes over from the drawn kind whenever there is a field to
    // look through. The crossfade animates, so opening the controls melts the line.
    val glassTarget = if (dimmed) 1f else glass
    val frostNow = if (dimmed) max(frost, OVERLAY_FROST) else frost
    val glassAnim by animateFloatAsState(glassTarget, tween(280), label = "stage-glass")
    val colours = remember(palette, highlightIntensity, glassAnim, frostNow) {
        StageColours(palette, highlightIntensity, glassAnim, frostNow)
    }
    val glassFx = glassText
    // the field must have drawn at least once: before that it has no resolution, and a
    // look through it would divide by zero
    val fieldTick = glassFx?.tick
    val shaderGlass = glassFx != null && (fieldTick?.longValue ?: 0L) > 0L
    val plain = shaderGlass && glassAnim > 0.01f

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
                    if (li >= 0) {
                        prep.plan.layouts[li].words.firstOrNull()?.let {
                            frame.glassEdge = (it.fontPx * 0.04f).coerceIn(2.5f, 5f)
                        }
                    }
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

    // while paused (without the controls) the stage dims back, holding the current line;
    // with the controls open the glass itself is the dimming
    val target = when {
        dimmed -> 1f
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
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    renderEffect = if (shaderGlass && glassAnim > 0.01f) glassFx?.effect else null
                },
        ) {
            frame.tick.longValue
            fieldTick?.longValue
            val p = prep ?: return@Canvas
            if (plain) glassFx?.uniforms(glassAnim, frostNow, frame.glassEdge)
            with(renderer) {
                if (frame.line < 0) drawGapDots(p, frame.positionMs, colours, reduceMotion)
                else drawLine(p, frame.line, frame.positionMs, colours, reduceMotion, StageLayer.SETTLED, frame.wordShiftMs, sweep = !simple, plain = plain)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val b = frame.blur.floatValue
                        val fx = if (shaderGlass && glassAnim > 0.01f) glassFx else null
                        renderEffect = when {
                            b > 0.5f && fx != null ->
                                // the words blur as they fly, and the glass is drawn over
                                // the blurred layer from outside it
                                fx.chainedUnderBlur(b)
                            b > 0.5f -> BlurEffect(b, b, TileMode.Decal)
                            fx != null -> fx.effect
                            else -> null
                        }
                    },
            ) {
                frame.tick.longValue
                fieldTick?.longValue
                val p = prep ?: return@Canvas
                if (plain) glassFx?.uniforms(glassAnim, frostNow, frame.glassEdge)
                with(renderer) { drawLine(p, frame.line, frame.positionMs, colours, reduceMotion, StageLayer.MOTION, frame.wordShiftMs, sweep = !simple, plain = plain) }
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
        val o = audio.onsets.poll() ?: break
        frame.onsetScratch += pos - (now - o.at)
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
/** The least frost on the overlay glass, so the line stays readable over busy colour. */
private const val OVERLAY_FROST = 0.35f
