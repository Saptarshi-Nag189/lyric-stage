package com.lyricstage.background

import android.graphics.Bitmap
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.lyricstage.core.palette.Colour
import com.lyricstage.core.palette.StagePalette
import com.lyricstage.palette.downscale
import com.lyricstage.settings.BackgroundMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin

/** Shader time in seconds, shared by the background and the ripple emitter. */
class BackgroundClock {
    private val start = System.nanoTime()
    var speed = 1f

    fun seconds(): Float = ((System.nanoTime() - start) / 1e9).toFloat() * speed
}

@Composable
fun StageBackground(
    mode: BackgroundMode,
    palette: StagePalette,
    art: Bitmap?,
    ripples: RippleEmitter,
    clock: BackgroundClock,
    reduceMotion: Boolean,
    /** False while paused, with no lyrics, or for instrumentals: the background holds still. */
    animate: Boolean,
    /** Battery saver: pure black, so OLED pixels stay off. */
    black: Boolean,
    modifier: Modifier = Modifier,
    /** Pool brightness, 0 to 2 (Settings). */
    pools: Float = 1f,
    /** Ripple strength, 0 to 2 (Settings). */
    rippleStrength: Float = 1f,
) {
    clock.speed = if (reduceMotion) 0.25f else 1f
    if (black) {
        Canvas(modifier.fillMaxSize()) { drawRect(Color.Black) }
        return
    }
    Box(modifier.fillMaxSize()) {
        when {
            mode == BackgroundMode.SOLID -> Canvas(Modifier.fillMaxSize()) { drawRect(Color(palette.base)) }
            mode == BackgroundMode.ARTWORK && art != null -> ArtworkBlur(art, palette)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> LiquidGlass(palette, ripples, clock, animate, pools, rippleStrength)
            else -> GradientFallback(palette, clock, animate, pools)
        }
        // the lyrics' own shadow carries their readability on any background colour, so no
        // scrim is laid over the stage: the artwork's top colour shows as it comes
    }
}

/** Liquid glass on API 33+, rendered at a third of the resolution and scaled up (it is soft). */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun LiquidGlass(palette: StagePalette, ripples: RippleEmitter, clock: BackgroundClock, animate: Boolean, pools: Float, rippleStrength: Float) {
    val shader = remember { RuntimeShader(LIQUID_GLASS_AGSL) }
    val brush = remember(shader) { ShaderBrush(shader) }
    var full by remember { mutableStateOf(IntSize.Zero) }
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(animate) { animateTime(time, clock, animate) }
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize().onSizeChanged { full = it }) {
        if (full.width == 0) return@Box
        val w = (full.width / SCALE).coerceAtLeast(2)
        val h = (full.height / SCALE).coerceAtLeast(2)
        val sizeDp = with(density) { DpSize(w.toDp(), h.toDp()) }
        Canvas(
            Modifier
                .requiredSize(sizeDp)
                .graphicsLayer {
                    // offscreen so the layer rasterises at low resolution, then scales up
                    compositingStrategy = CompositingStrategy.Offscreen
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = full.width / w.toFloat()
                    scaleY = full.height / h.toFloat()
                },
        ) {
            shader.setFloatUniform("uRes", size.width, size.height)
            shader.setFloatUniform("uTime", time.floatValue)
            shader.setColorUniforms(palette)
            shader.setFloatUniform("uRip", ripples.data)
            shader.setFloatUniform("uLineT", ripples.lineAt)
            shader.setFloatUniform("uPools", pools)
            shader.setFloatUniform("uRipple", rippleStrength)
            drawRect(brush)
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun RuntimeShader.setColorUniforms(p: StagePalette) {
    fun set(name: String, c: Int) = setFloatUniform(name, Colour.red(c) / 255f, Colour.green(c) / 255f, Colour.blue(c) / 255f)
    set("uC0", p.accents[0])
    set("uC1", p.accents.getOrElse(1) { p.accents[0] })
    set("uC2", p.accents.getOrElse(2) { p.accents[0] })
    set("uBase", p.base)
}

/** Below API 33: slowly drifting radial pools of the accent colours. */
@Composable
private fun GradientFallback(palette: StagePalette, clock: BackgroundClock, animate: Boolean, pools: Float = 1f) {
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(animate) { animateTime(time, clock, animate) }
    val base = Color(palette.base)
    val accents = palette.accents.map { Color(it) }
    Canvas(Modifier.fillMaxSize()) {
        val t = time.floatValue
        drawRect(base)
        val r = size.maxDimension * 0.65f
        accents.forEachIndexed { i, c ->
            val phase = t * (0.05f + i * 0.013f) + i * 2.1f
            val centre = Offset(
                size.width * (0.5f + 0.35f * cos(phase)),
                size.height * (0.5f + 0.35f * sin(phase * 1.3f)),
            )
            drawCircle(Brush.radialGradient(listOf(c.copy(alpha = (0.42f * pools).coerceIn(0f, 1f)), Color.Transparent), centre, r), r, centre)
        }
    }
}

/** Album art, heavily blurred: downscaled then drawn large with filtering, plus a RenderEffect blur on API 31+. */
@Composable
private fun ArtworkBlur(art: Bitmap, palette: StagePalette) {
    // downscaled off the main thread: the old copy(ARGB_8888) of a full-size cover stalled
    // composition on every artwork change
    val small by produceState<ImageBitmap?>(null, art) {
        value = withContext(Dispatchers.Default) { downscale(art, 24, 24).asImageBitmap() }
    }
    val sb = small
    Canvas(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    renderEffect = BlurEffect(60f, 60f, TileMode.Clamp)
                }
            },
    ) {
        drawRect(Color(palette.base))
        if (sb != null) {
            // cover the stage, keeping the square art centred
            val side = size.maxDimension
            drawImage(
                sb,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(sb.width, sb.height),
                dstOffset = IntOffset(((size.width - side) / 2).toInt(), ((size.height - side) / 2).toInt()),
                dstSize = IntSize(side.toInt(), side.toInt()),
                alpha = 0.85f,
                filterQuality = FilterQuality.Low,
            )
        }
    }
}

private const val SCALE = 3

/**
 * Drives the background's time at 30 fps (every other frame; the motion is slow and soft),
 * or sets it once and stops when the background should hold still.
 */
private suspend fun animateTime(time: MutableFloatState, clock: BackgroundClock, animate: Boolean) {
    time.floatValue = clock.seconds()
    if (!animate) return
    var odd = false
    while (true) withFrameNanos {
        odd = !odd
        if (odd) time.floatValue = clock.seconds()
    }
}
