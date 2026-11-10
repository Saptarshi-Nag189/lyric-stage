package com.lyricstage.background

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.ShaderBrush
import com.lyricstage.core.palette.StagePalette

/**
 * The field the glass lyrics are cut from. The lyrics ask for [brush] each frame and paint
 * their letters with it, the way the glass panels are cut from the same field.
 */
interface StageGlassField {
    /** Paints the liquid field at full resolution; null until the field has drawn once,
     *  and nothing is cut from it before that. */
    val brush: ShaderBrush?
}

/** [StageGlassField] over the shared [LiquidGlassField], for the glass lyrics. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class LiquidGlassBody(field: LiquidGlassField) : StageGlassField {
    private val f = field
    private val b = ShaderBrush(f.backdrop)
    override val brush get() = if (f.ready) b else null
}

/**
 * A glass panel - the controls card, the queue - over the liquid field. The layer's own
 * drawing is the field as it is behind the panel (aligned by [LiquidGlassField.syncPanel]);
 * this bends it at the panel's rim, the way a thick glass edge lenses what crosses it, and
 * lights the bevel: bright along the top, shade along the bottom, a soft sheen from above.
 * At zero frost the panel is clear glass - the field exactly as the background draws it,
 * no dim, no milk, no pushed colour of its own; both rise only with frost, so what sits
 * on top stays readable as it frosts.
 */
internal const val PANEL_GLASS_AGSL = """
uniform shader uContent;
uniform float2 uRes;
uniform float uRadius;
uniform float uFrost;
uniform float uBend;

float sdBox(float2 p, float2 hs, float r) {
    float2 q = abs(p) - hs + float2(r);
    return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - r;
}

half4 main(float2 fragCoord) {
    float2 hs = uRes * 0.5;
    float2 p = fragCoord - hs;
    float inside = clamp(-sdBox(p, hs, uRadius), 0.0, 100000.0);
    // one at the rim, gone a little way in: the middle of the panel stays clear
    float rim = 1.0 - smoothstep(0.0, uBend * 2.5, inside);

    // which way is out, from the distance field itself
    float2 g = float2(
        sdBox(p + float2(1.0, 0.0), hs, uRadius) - sdBox(p - float2(1.0, 0.0), hs, uRadius),
        sdBox(p + float2(0.0, 1.0), hs, uRadius) - sdBox(p - float2(0.0, 1.0), hs, uRadius));
    float2 n = length(g) > 0.0001 ? normalize(g) : float2(0.0, -1.0);

    // the rim lenses the field outward
    half3 col = uContent.eval(fragCoord + n * (rim * uBend)).rgb;

    float frost = clamp(uFrost, 0.0, 1.0);
    // frost alone stands between the eye and the field. At zero the panel is clear glass -
    // the field as it is, no fixed dim, no milk, no saturation push toward some colour of
    // its own - and the darkening and the milk rise only as frost does, for what sits on
    // top of it
    col *= half(1.0 - 0.42 * frost);
    half pl = dot(col, half3(0.299, 0.587, 0.114));
    col = mix(half3(pl), col, half(1.0 + 0.12 * frost));
    col = mix(col, half3(1.0), half(0.13 * frost));

    // the bevel is lit from the top left and shaded opposite, the way the reference
    // material is, and a bright hairline sits exactly at the edge, as a glass border reads
    half lit = half(clamp(dot(n, float2(-0.7071, -0.7071)), 0.0, 1.0));
    half shade = half(clamp(dot(n, float2(0.7071, 0.7071)), 0.0, 1.0));
    col += half3(rim * lit * 0.36 + rim * rim * lit * 0.20);
    col -= half3(rim * shade * 0.16);
    float hair = 1.0 - smoothstep(0.0, 2.0, inside);
    col += half3(hair * (0.08 + 0.26 * lit));
    col -= half3(hair * 0.07 * shade);
    // a soft sheen across the top of the panel
    col += half3(0.05 * (1.0 - smoothstep(0.0, uRes.y * 0.55, fragCoord.y)));

    return half4(col, 1.0);
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
