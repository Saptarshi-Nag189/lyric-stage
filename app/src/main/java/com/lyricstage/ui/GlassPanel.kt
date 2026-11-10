package com.lyricstage.ui

import android.graphics.RenderEffect
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.lyricstage.background.LIQUID_GLASS_AGSL
import com.lyricstage.background.PANEL_GLASS_AGSL
import com.lyricstage.background.LiquidGlassField
import com.lyricstage.ui.theme.Ui

/**
 * A panel of liquid glass over the stage - the controls card, the queue. It shows the
 * liquid field exactly as it is behind it (the same field, sampled at the panel's own
 * rectangle), frosted by [frost], its rim bending what is behind it and lit from above,
 * the way Apple's glass material reads. Without a field to look through - no liquid
 * background, battery saver, the glass switch off - it falls back to the quiet dark card.
 *
 * [stageSize] is the stage the panel floats over, so the field inside lines up with the
 * one behind; the panel finds its own place in it.
 */
@Composable
fun GlassPanel(
    frost: Float,
    corner: Dp,
    stageSize: IntSize,
    modifier: Modifier = Modifier,
    /** Null: no liquid field to look through, so the plain card. */
    field: LiquidGlassField? = null,
    fallback: Color = Ui.Card.copy(alpha = 0.86f),
    content: @Composable () -> Unit = {},
) {
    val shape = RoundedCornerShape(corner)
    if (field == null || stageSize.width == 0) {
        Box(modifier.clip(shape).background(fallback)) { content() }
        return
    }
    GlassFieldPanel(frost, corner, stageSize, field, modifier, shape, content)
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun GlassFieldPanel(
    frost: Float,
    corner: Dp,
    stageSize: IntSize,
    field: LiquidGlassField,
    modifier: Modifier,
    shape: RoundedCornerShape,
    content: @Composable () -> Unit,
) {
    // the panel's place in the stage, so the glass shows the field that is behind it
    var pos by remember { mutableStateOf(Offset.Zero) }
    val liquid = remember { android.graphics.RuntimeShader(LIQUID_GLASS_AGSL) }
    val brush = remember(liquid) { ShaderBrush(liquid) }
    val panel = remember { android.graphics.RuntimeShader(PANEL_GLASS_AGSL) }
    val effect = remember(panel) { RenderEffect.createRuntimeShaderEffect(panel, "uContent").asComposeRenderEffect() }
    val density = LocalDensity.current
    val radiusPx = with(density) { corner.toPx() }
    val bendPx = with(density) { 4.dp.toPx() }

    Box(
        modifier
            .clip(shape)
            .onGloballyPositioned { c ->
                val p = c.positionInRoot()
                pos = Offset(p.x, p.y)
            },
    ) {
        // frost is the only blur: at zero the panel is clear glass, showing the field
        // behind it as it is - no fixed blur washing it into a tint of the background
        val frostBlur = if (frost > 0.02f) Modifier.blur(25.dp * frost.coerceIn(0f, 1f)) else Modifier
        Box(
            Modifier
                .matchParentSize()
                .then(frostBlur),
        ) {
            Canvas(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { renderEffect = effect },
            ) {
                // move with the field behind, hold still when it does; nothing to show
                // before the field has ever been drawn
                field.frameTick.longValue
                if (field.ready) {
                    field.syncPanel(liquid, pos.x, pos.y, stageSize.width.toFloat(), stageSize.height.toFloat())
                    panel.setFloatUniform("uRes", size.width, size.height)
                    panel.setFloatUniform("uRadius", radiusPx)
                    panel.setFloatUniform("uFrost", frost)
                    panel.setFloatUniform("uBend", bendPx)
                    drawRect(brush)
                }
            }
        }
        content()
    }
}
