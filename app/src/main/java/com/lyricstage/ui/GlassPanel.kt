package com.lyricstage.ui

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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.lyricstage.background.LIQUID_GLASS_AGSL
import com.lyricstage.background.LiquidGlassField
import com.lyricstage.background.PANEL_BEND_AGSL
import com.lyricstage.background.PANEL_LIGHT_AGSL
import com.lyricstage.ui.theme.Ui

/**
 * A piece of liquid glass over the stage - the player card, the queue, a round button, a
 * status pill. It shows the same moving field as the background, sampled at its own place,
 * bent at its edge and lit along it, the way Apple's glass material reads. Without a field
 * to look through - no liquid background, battery saver, the glass switch off - it falls
 * back to a quiet dark shape.
 *
 * [stageSize] is the stage the panel floats over, so the field inside lines up with the
 * one behind; the panel finds its own place in it. [edge] is how wide the bevel is and
 * [bend] how far it pulls the field: a big card takes more of both than a small button.
 */
@Composable
fun GlassPanel(
    frost: Float,
    corner: Dp,
    stageSize: IntSize,
    modifier: Modifier = Modifier,
    /** Null: no liquid field to look through, so the plain shape. */
    field: LiquidGlassField? = null,
    fallback: Color = Ui.Card.copy(alpha = 0.86f),
    edge: Dp = 9.dp,
    bend: Dp = 24.dp,
    /** A fixed dim and lift of the pane, before frost adds its own. */
    dim: Float = 0.05f,
    lift: Float = 0.06f,
    content: @Composable () -> Unit = {},
) {
    val shape = RoundedCornerShape(corner)
    if (field == null || stageSize.width == 0 || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Box(modifier.clip(shape).background(fallback)) { content() }
        return
    }
    GlassFieldPanel(frost, corner, stageSize, field, modifier, shape, fallback, edge, bend, dim, lift, content)
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class PanelShaders {
    val liquid = android.graphics.RuntimeShader(LIQUID_GLASS_AGSL)
    val bend = android.graphics.RuntimeShader(PANEL_BEND_AGSL)
    val light = android.graphics.RuntimeShader(PANEL_LIGHT_AGSL)
    val bendBrush = ShaderBrush(bend)
    val lightBrush = ShaderBrush(light)
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
    fallback: Color,
    edge: Dp,
    bend: Dp,
    dim: Float,
    lift: Float,
    content: @Composable () -> Unit,
) {
    // a shader that will not build leaves the plain shape, never a crash
    val shaders = remember { runCatching { PanelShaders() }.getOrNull() }
    if (shaders == null) {
        Box(modifier.clip(shape).background(fallback)) { content() }
        return
    }
    // the panel's place in the stage, so the glass shows the field that is behind it
    var pos by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val radiusPx = with(density) { corner.toPx() }
    val edgePx = with(density) { edge.toPx() }
    val bendPx = with(density) { bend.toPx() }
    val f = frost.coerceIn(0f, 1f)

    Box(
        modifier
            .clip(shape)
            .onGloballyPositioned { c ->
                val p = c.positionInRoot()
                pos = Offset(p.x, p.y)
            },
    ) {
        // what is seen through the glass. Frost is the only blur: at zero the pane is
        // clear, showing the field behind it as it is
        Canvas(Modifier.matchParentSize().then(if (f > 0.02f) Modifier.blur(22.dp * f) else Modifier)) {
            // move with the field behind, hold still when it does; nothing to show
            // before the field has ever been drawn
            field.frameTick.longValue
            if (field.ready) {
                field.syncPanel(shaders.liquid, pos.x, pos.y, stageSize.width.toFloat(), stageSize.height.toFloat())
                val s = shaders.bend
                // set again each frame: the field's own uniforms have moved since the last
                s.setInputShader("uField", shaders.liquid)
                s.setFloatUniform("uRes", size.width, size.height)
                s.setFloatUniform("uRadius", minOf(radiusPx, size.minDimension / 2f))
                s.setFloatUniform("uEdge", minOf(edgePx, size.minDimension / 2f))
                s.setFloatUniform("uBend", bendPx)
                s.setFloatUniform("uDisp", 0.26f)
                drawRect(shaders.bendBrush)
            }
        }
        // the light on the glass itself, sharp over whatever the frost has done below
        Canvas(Modifier.matchParentSize()) {
            if (field.ready) {
                val s = shaders.light
                s.setFloatUniform("uRes", size.width, size.height)
                s.setFloatUniform("uRadius", minOf(radiusPx, size.minDimension / 2f))
                s.setFloatUniform("uEdge", minOf(edgePx, size.minDimension / 2f))
                s.setFloatUniform("uFrost", f)
                s.setFloatUniform("uDim", dim)
                s.setFloatUniform("uLift", lift)
                s.setFloatUniform("uSpec", 1.15f)
                drawRect(shaders.lightBrush)
            }
        }
        content()
    }
}
