package com.lyricstage.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.lyricstage.background.BackgroundClock
import com.lyricstage.background.LiquidGlassField
import com.lyricstage.background.RippleEmitter
import com.lyricstage.background.StageBackground
import com.lyricstage.core.palette.StagePalette
import com.lyricstage.core.stage.Ripple
import com.lyricstage.settings.StageSettings
import com.lyricstage.ui.theme.Ui
import kotlinx.coroutines.delay
import kotlin.random.Random

/** Whether the app's own controls are drawn as glass, and how frosted. One switch and one
 *  slider in Settings drive this for the lyrics and every screen alike. */
class GlassStyle(val on: Boolean, val frost: Float)

val LocalGlass = compositionLocalOf { GlassStyle(false, 0f) }

/** The liquid field of the stage and the stage's size, for glass that floats over it. */
class StageGlass(val field: LiquidGlassField?, val size: IntSize, val frost: Float)

val LocalStageGlass = compositionLocalOf<StageGlass?> { null }

/**
 * A small piece of glass drawn without a shader, for controls that sit on a card or on a
 * dimmed background where there is nothing sharp to bend: a clear body that lets the
 * colour behind through, lit from the top left, with a bright rim there and a weaker one
 * coming back at the far edge. [bright] makes the body lighter (a selected pill, a knob);
 * [tint] colours it (the main button). Frost adds milk.
 */
fun Modifier.glassSurface(shape: Shape, style: GlassStyle, bright: Float = 0f, tint: Color? = null): Modifier =
    clip(shape).drawWithContent {
        val o = shape.createOutline(size, layoutDirection, this)
        val end = Offset(size.width, size.height)
        if (tint != null) drawOutline(o, tint.copy(alpha = 0.82f))
        val a = 0.07f + 0.24f * bright + 0.10f * style.frost
        drawOutline(
            o,
            Brush.linearGradient(
                0f to Color.White.copy(alpha = (a * 2.2f).coerceAtMost(0.9f)),
                0.38f to Color.White.copy(alpha = a),
                0.62f to Color.White.copy(alpha = a * 0.6f),
                1f to Color.White.copy(alpha = (a * 1.5f).coerceAtMost(0.8f)),
                start = Offset.Zero, end = end,
            ),
        )
        drawContent()
        val w = 1.2.dp.toPx()
        drawOutline(
            o,
            Brush.linearGradient(
                0f to Color.White.copy(alpha = 0.78f),
                0.3f to Color.White.copy(alpha = 0.20f),
                0.7f to Color.White.copy(alpha = 0.10f),
                1f to Color.White.copy(alpha = 0.42f),
                start = Offset.Zero, end = end,
            ),
            style = Stroke(w),
        )
    }

/**
 * The switch: a clear glass capsule holding a coloured pill, and a clear lens for a knob,
 * larger than the pill, that magnifies the colour under it. On, the pill is the accent
 * and glows a little under the capsule; off, it is grey.
 */
@Composable
fun GlassSwitch(checked: Boolean, accent: Color, enabled: Boolean = true) {
    val t by animateFloatAsState(if (checked) 1f else 0f, label = "switch")
    Canvas(Modifier.size(width = 60.dp, height = 36.dp).graphicsLayer { alpha = if (enabled) 1f else 0.45f }) {
        val h = size.height
        val w = size.width
        val end = Offset(w, h)
        val cap = CornerRadius(h / 2f)
        val pillColour = lerp(Color(0xFF9A9CA6), accent, t)
        val inset = 10.dp.toPx()
        val ph = 15.dp.toPx()
        val pillTop = (h - ph) / 2f
        val pillSize = Size(w - 2 * inset, ph)
        // the glow the colour throws under the glass
        if (t > 0.01f) {
            drawRoundRect(accent.copy(alpha = 0.16f * t), Offset(inset - 3.dp.toPx(), pillTop + 3.dp.toPx()), Size(pillSize.width + 6.dp.toPx(), ph + 5.dp.toPx()), CornerRadius(ph))
        }
        // the capsule
        drawRoundRect(
            Brush.linearGradient(
                0f to Color.White.copy(alpha = 0.26f), 0.4f to Color.White.copy(alpha = 0.09f),
                1f to Color.White.copy(alpha = 0.16f), start = Offset.Zero, end = end,
            ),
            cornerRadius = cap,
        )
        drawRoundRect(pillColour, Offset(inset, pillTop), pillSize, CornerRadius(ph / 2f))
        // the light along the top of the pill
        drawRoundRect(Color.White.copy(alpha = 0.30f), Offset(inset + ph * 0.3f, pillTop + 1.5.dp.toPx()), Size(pillSize.width - ph * 0.6f, 2.dp.toPx()), CornerRadius(2.dp.toPx()))
        val sw = 1.2.dp.toPx()
        drawRoundRect(
            Brush.linearGradient(
                0f to Color.White.copy(alpha = 0.80f), 0.3f to Color.White.copy(alpha = 0.22f),
                0.7f to Color.White.copy(alpha = 0.12f), 1f to Color.White.copy(alpha = 0.46f),
                start = Offset.Zero, end = end,
            ),
            Offset(sw / 2f, sw / 2f), Size(w - sw, h - sw), CornerRadius((h - sw) / 2f), style = Stroke(sw),
        )
        // the knob: a lens over the pill
        val r = 14.dp.toPx()
        val x0 = 4.dp.toPx() + r
        val cx = x0 + (w - 2 * x0) * t
        val c = Offset(cx, h / 2f)
        drawCircle(Color.Black.copy(alpha = 0.20f), r + 1.dp.toPx(), c + Offset(0f, 2.dp.toPx()))
        val lens = Path().apply { addOval(Rect(c, r)) }
        clipPath(lens) {
            drawCircle(Color.White.copy(alpha = 0.20f), r, c)
            scale(1.5f, 1.5f, c) {
                drawRoundRect(pillColour.copy(alpha = 0.92f), Offset(inset, pillTop), pillSize, CornerRadius(ph / 2f))
            }
            drawCircle(
                Brush.linearGradient(
                    0f to Color.White.copy(alpha = 0.42f), 0.5f to Color.White.copy(alpha = 0.06f),
                    1f to Color.White.copy(alpha = 0.18f), start = c - Offset(r, r), end = c + Offset(r, r),
                ),
                r, c,
            )
        }
        drawCircle(
            Brush.linearGradient(
                0f to Color.White.copy(alpha = 0.95f), 0.35f to Color.White.copy(alpha = 0.30f),
                0.7f to Color.White.copy(alpha = 0.14f), 1f to Color.White.copy(alpha = 0.55f),
                start = c - Offset(r, r), end = c + Offset(r, r),
            ),
            r - sw / 2f, c, style = Stroke(sw),
        )
    }
}

/** A slider whose knob is a small glass capsule when the glass is on. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    accent: Color,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true,
    inactive: Color = Ui.Track,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val glass = LocalGlass.current
    val colors = SliderDefaults.colors(
        thumbColor = Ui.Text,
        activeTrackColor = accent,
        inactiveTrackColor = if (glass.on) Color.White.copy(alpha = 0.22f) else inactive,
        activeTickColor = Ui.OnAccent.copy(alpha = 0.4f),
        inactiveTickColor = Ui.Muted.copy(alpha = 0.4f),
    )
    if (!glass.on) {
        Slider(value, onValueChange, modifier, enabled, valueRange, steps, onValueChangeFinished, colors)
        return
    }
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        onValueChangeFinished = onValueChangeFinished,
        colors = colors,
        steps = steps,
        thumb = { Box(Modifier.size(width = 34.dp, height = 24.dp).glassSurface(CircleShape, glass, bright = 1f)) },
        valueRange = valueRange,
    )
}

/**
 * What the screens around the stage sit on when the glass is on: the stage's own
 * background, alive and dimmed, so Settings reads as panes laid over the music and not as
 * a separate dark app. Off, or on battery saver, it is the plain dark ground.
 */
@Composable
fun GlassBackdrop(settings: StageSettings, palette: StagePalette, art: Bitmap?, reduceMotion: Boolean) {
    val glass = LocalGlass.current
    if (!glass.on) {
        Box(Modifier.fillMaxSize().background(Ui.Ground))
        return
    }
    val clock = remember { BackgroundClock() }
    val ripples = remember { RippleEmitter() }
    LaunchedEffect(Unit) {
        val rnd = Random(11)
        while (true) {
            ripples.emit(Ripple(0.1f + rnd.nextFloat() * 0.8f, 0.1f + rnd.nextFloat() * 0.8f, 0.5f + rnd.nextFloat() * 0.3f), clock.seconds())
            delay(2200L + rnd.nextLong(1600L))
        }
    }
    Box(Modifier.fillMaxSize()) {
        StageBackground(
            settings.background, palette, art, ripples, clock, reduceMotion,
            animate = true, black = false, modifier = Modifier.fillMaxSize(),
            pools = settings.pools, rippleStrength = settings.rippleStrength,
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.42f)))
    }
}
