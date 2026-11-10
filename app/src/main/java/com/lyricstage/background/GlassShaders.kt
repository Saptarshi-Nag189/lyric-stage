package com.lyricstage.background

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.text.TextLayoutResult
import com.lyricstage.core.palette.StagePalette

/**
 * The field the glass lyrics are cut from. The lyrics ask for a brush per word each frame
 * and paint their letters with it, the way the glass panels are cut from the same field.
 */
interface StageGlassField {
    /** Paints the liquid field at full resolution, unbent; null until the field has drawn
     *  once, and nothing is cut from it before that. The fallback for [word]. */
    val brush: ShaderBrush?

    /**
     * The brush for one pass of a glass word drawn at [topLeft]: the field bent through
     * the word's own letters and lit along their rim. [slot] names the pass, so two passes
     * in a frame never share a shader. Null when the glass cannot be made; the caller
     * falls back to [brush].
     */
    fun word(slot: Int, tl: TextLayoutResult, topLeft: Offset, fontPx: Float, look: GlassLook, tint: Color?, frost: Float): Brush?
}

/** [StageGlassField] over the shared [LiquidGlassField], for the glass lyrics. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class LiquidGlassBody(field: LiquidGlassField) : StageGlassField {
    private val f = field
    private val b = ShaderBrush(f.backdrop)
    private val words = GlassWords(field)
    override val brush get() = if (f.ready) b else null
    override fun word(slot: Int, tl: TextLayoutResult, topLeft: Offset, fontPx: Float, look: GlassLook, tint: Color?, frost: Float): Brush? =
        words.brush(slot, tl, topLeft, fontPx, look, tint, frost)
}

/**
 * A glass panel - the player card, the queue, a button, a pill - over the liquid field,
 * in two passes. This one is what is seen through the glass: the field behind the panel
 * ([uField], aligned by [LiquidGlassField.syncPanel]), bent where the panel's edge is a
 * bevel. The bevel pulls the field in from outside the panel, each colour a little
 * differently, the way a thick glass edge lenses and splits what crosses it; the middle
 * of the pane shows the field as it is. Frost is a blur laid over this pass.
 */
internal const val PANEL_BEND_AGSL = """
uniform shader uField;
uniform float2 uRes;
uniform float uRadius;
uniform float uEdge;
uniform float uBend;
uniform float uDisp;

float sdBox(float2 p, float2 hs, float r) {
    float2 q = abs(p) - hs + float2(r);
    return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - r;
}

half4 main(float2 fragCoord) {
    float2 hs = uRes * 0.5;
    float2 p = fragCoord - hs;
    float inside = max(-sdBox(p, hs, uRadius), 0.0);
    float e = 1.0 - clamp(inside / uEdge, 0.0, 1.0);
    float rim = e * e;
    half3 col;
    if (rim > 0.002) {
        // which way is out, from the distance field itself
        float2 g = float2(
            sdBox(p + float2(1.0, 0.0), hs, uRadius) - sdBox(p - float2(1.0, 0.0), hs, uRadius),
            sdBox(p + float2(0.0, 1.0), hs, uRadius) - sdBox(p - float2(0.0, 1.0), hs, uRadius));
        float2 n = length(g) > 0.0001 ? normalize(g) : float2(0.0, -1.0);
        float2 off = n * (rim * uBend);
        col.r = uField.eval(fragCoord + off * (1.0 - uDisp)).r;
        col.g = uField.eval(fragCoord + off).g;
        col.b = uField.eval(fragCoord + off * (1.0 + uDisp)).b;
    } else {
        col = uField.eval(fragCoord).rgb;
    }
    half lum = dot(col, half3(0.299, 0.587, 0.114));
    col = mix(half3(lum), col, 1.15);
    return half4(clamp(col, half3(0.0), half3(1.0)), 1.0);
}
"""

/**
 * The second pass of a glass panel: the light on the glass itself, laid over what is seen
 * through it and never blurred. The bevel is lit from the top left with a weaker light
 * coming back from the far edge and a little shade under it; the pane carries a faint
 * lift, and a dim and a milk that rise with frost so what sits on it stays readable.
 * Premultiplied: white for the light, alpha for how much of the pane below it covers.
 */
internal const val PANEL_LIGHT_AGSL = """
uniform float2 uRes;
uniform float uRadius;
uniform float uEdge;
uniform float uFrost;
uniform float uDim;
uniform float uLift;
uniform float uSpec;

float sdBox(float2 p, float2 hs, float r) {
    float2 q = abs(p) - hs + float2(r);
    return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - r;
}

half4 main(float2 fragCoord) {
    float2 hs = uRes * 0.5;
    float2 p = fragCoord - hs;
    float inside = max(-sdBox(p, hs, uRadius), 0.0);
    float e = 1.0 - clamp(inside / uEdge, 0.0, 1.0);
    float rim = e * e;
    float thin = rim * rim * rim * e;
    float2 g = float2(
        sdBox(p + float2(1.0, 0.0), hs, uRadius) - sdBox(p - float2(1.0, 0.0), hs, uRadius),
        sdBox(p + float2(0.0, 1.0), hs, uRadius) - sdBox(p - float2(0.0, 1.0), hs, uRadius));
    float2 n = length(g) > 0.0001 ? normalize(g) : float2(0.0, -1.0);
    float d = dot(n, float2(-0.62, -0.78));
    float d1 = clamp(d, 0.0, 1.0);
    float d2 = clamp(-d, 0.0, 1.0);
    // a hairline exactly at the edge, as a glass border reads
    float hair = 1.0 - smoothstep(0.0, 1.6, inside);
    float hi = (thin * (pow(d1, 1.4) * 0.95 + d2 * d2 * 0.38) + rim * d1 * 0.10 + hair * (0.10 + 0.30 * d1 + 0.12 * d2)) * uSpec;
    float lo = rim * d2 * 0.16;
    float frost = clamp(uFrost, 0.0, 1.0);
    float white = clamp(hi + uLift + 0.13 * frost, 0.0, 1.0);
    float dark = clamp(lo + uDim + 0.30 * frost, 0.0, 0.9);
    float a = 1.0 - (1.0 - dark) * (1.0 - white);
    return half4(half3(white), a);
}
"""

/**
 * The liquid field, shared by everything that needs to see it: the background canvas's own
 * shader (rendered small and scaled up), the brush the glass lyrics are painted with (full
 * resolution), and any number of panel-sized copies for the glass UI, each showing exactly
 * the rectangle of the field that is behind it. [sync] is called on the background's draw
 * each frame; the values are kept so the lyrics and the panels can sync their own copies on
 * their draws.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class LiquidGlassField {
    /** The background's own shader, rendered at a third of the resolution. */
    val shader = android.graphics.RuntimeShader(LIQUID_GLASS_AGSL)

    /** The same field at full resolution, painted through as the glass lyrics' body. */
    val backdrop = android.graphics.RuntimeShader(LIQUID_GLASS_AGSL)

    /** Bumped on every sync, so panels redraw while the field moves and hold while it rests. */
    val frameTick = androidx.compose.runtime.mutableLongStateOf(0L)

    /** False until the first sync: nothing may look through the field before that. */
    val ready: Boolean get() = frameTick.longValue > 0

    private var time = 0f
    private var rip = FloatArray(RippleEmitter.MAX * 4)
    private var pools = 1f
    private var strength = 1f
    private var palette: StagePalette? = null

    fun sync(
        palette: StagePalette,
        ripples: FloatArray,
        time: Float,
        pools: Float,
        rippleStrength: Float,
        canvasW: Float,
        canvasH: Float,
        fullW: Float,
        fullH: Float,
    ) {
        this.time = time
        ripples.copyInto(rip)
        this.pools = pools
        this.strength = rippleStrength
        this.palette = palette
        apply(shader, palette)
        shader.setFloatUniform("uRes", canvasW, canvasH)
        shader.setFloatUniform("uOrigin", 0f, 0f)
        apply(backdrop, palette)
        backdrop.setFloatUniform("uRes", fullW, fullH)
        backdrop.setFloatUniform("uOrigin", 0f, 0f)
        setBoth("uTime", time)
        setBoth("uRip", rip)
        setBoth("uPools", pools)
        setBoth("uRipple", strength)
        frameTick.longValue += 1
    }

    /**
     * Points [s] — a panel's own copy of the field — at the rectangle of the stage behind
     * the panel: [origin] is the panel's top-left in stage pixels, and the field's
     * resolution is the stage's, so what the panel shows is exactly what is behind it.
     * Does nothing before the first [sync]: an unsynced field has no resolution yet.
     */
    fun syncPanel(s: android.graphics.RuntimeShader, originX: Float, originY: Float, stageW: Float, stageH: Float) {
        if (!ready) return
        val p = palette ?: return
        apply(s, p)
        s.setFloatUniform("uTime", time)
        s.setFloatUniform("uRip", rip)
        s.setFloatUniform("uPools", pools)
        s.setFloatUniform("uRipple", strength)
        s.setFloatUniform("uRes", stageW, stageH)
        s.setFloatUniform("uOrigin", originX, originY)
    }

    private fun apply(s: android.graphics.RuntimeShader, p: StagePalette) {
        fun set(name: String, c: Int) = s.setFloatUniform(
            name,
            com.lyricstage.core.palette.Colour.red(c) / 255f,
            com.lyricstage.core.palette.Colour.green(c) / 255f,
            com.lyricstage.core.palette.Colour.blue(c) / 255f,
        )
        set("uC0", p.accents[0])
        set("uC1", p.accents.getOrElse(1) { p.accents[0] })
        set("uC2", p.accents.getOrElse(2) { p.accents[0] })
        set("uBase", p.base)
    }

    private fun setBoth(name: String, v: Float) {
        shader.setFloatUniform(name, v)
        backdrop.setFloatUniform(name, v)
    }

    private fun setBoth(name: String, v: FloatArray) {
        shader.setFloatUniform(name, v)
        backdrop.setFloatUniform(name, v)
    }
}
