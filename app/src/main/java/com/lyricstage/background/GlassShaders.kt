package com.lyricstage.background

import android.graphics.RenderEffect
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.asComposeRenderEffect
import com.lyricstage.core.palette.StagePalette

/**
 * Glass text, the way Apple draws a liquid glass material: a glyph's rim is a bevel that
 * bends the light behind it, and its middle is clear, so the letter is read by its edges.
 * The layer's own drawing is the mask: where it has coverage there is glass, and the
 * coverage's neighbourhood says where the rim is. A light from above catches the top of
 * the rim, the underside sits in shade, and near the rim the backdrop is sampled a little
 * displaced and with the channels apart - refraction and dispersion - while the middle
 * looks straight through. Frost milks the body.
 *
 * The backdrop is the liquid background's own shader, evaluated live through [LiquidGlassField],
 * so the glass shows exactly the field that is behind it on screen, ripples and all.
 */
internal const val GLASS_TEXT_AGSL = """
uniform shader uContent;
uniform shader uBackdrop;
uniform float uGlass;
uniform float uFrost;
uniform float uEdge;
uniform float uBend;

half4 main(float2 fragCoord) {
    half4 c = uContent.eval(fragCoord);
    float a = c.a;
    if (a < 0.004) return half4(0.0);

    // the rim: a ring of coverage samples gives a soft edge field, and the direction that
    // points outward - where the bevel bends what is behind it
    float blurA = 0.0;
    float2 grad = float2(0.0);
    for (int i = 0; i < 8; i++) {
        float2 d = float2(cos(0.78539816 * float(i)), sin(0.78539816 * float(i)));
        float s = uContent.eval(fragCoord + d * uEdge).a;
        blurA += s;
        grad += d * (a - s);
    }
    blurA *= 0.125;
    grad *= 0.125;
    // zero in the middle of a stroke, one on its rim: clear middle, active edges
    float edge = clamp((a - blurA) * 2.2, 0.0, 1.0);
    float gl = length(grad);
    float2 n = gl > 0.0001 ? grad / gl : float2(0.0, -1.0);

    // refraction: the rim samples the backdrop from a little beyond itself, blue bent
    // more than red, so a bright edge fringes the way real glass does
    float2 off = n * (edge * uBend);
    half3 back = uBackdrop.eval(fragCoord + off).rgb;
    if (edge > 0.03) {
        half3 bFar = uBackdrop.eval(fragCoord + off * 1.35).rgb;
        half3 bNear = uBackdrop.eval(fragCoord + off * 0.65).rgb;
        back = half3(mix(back.r, bNear.r, half(0.5)), back.g, mix(back.b, bFar.b, half(0.5)));
    }

    // frost: a blurred, whitened take on the backdrop for the body of the glass
    float frost = clamp(uFrost, 0.0, 1.0);
    if (frost > 0.02) {
        float fr = uEdge * (0.8 + frost * 1.8);
        half3 f = half3(0.0);
        f += uBackdrop.eval(fragCoord + float2( fr,  fr)).rgb;
        f += uBackdrop.eval(fragCoord + float2(-fr,  fr)).rgb;
        f += uBackdrop.eval(fragCoord + float2( fr, -fr)).rgb;
        f += uBackdrop.eval(fragCoord + float2(-fr, -fr)).rgb;
        back = mix(back, f * 0.25, half(frost));
        back = mix(back, half3(1.0), half(0.10 * frost));
    }

    // the bevel: light from above on the top of the rim, shade underneath
    float up = clamp(-n.y, 0.0, 1.0);
    float down = clamp(n.y, 0.0, 1.0);
    half3 lit = back;
    lit += half3(edge * up * 0.55 + edge * edge * up * 0.35);
    lit -= half3(edge * down * 0.30);

    // a hint of the word's own colour, so highlights and held notes still read through
    lit = mix(lit, max(lit, half3(c.rgb)), half(0.18));

    float g = clamp(uGlass, 0.0, 1.0);
    // the input coverage is premultiplied and so is what AGSL expects back: the glass
    // colour is scaled by the letter's alpha, the solid colour passes through untouched
    return half4(mix(half3(c.rgb), lit * half(a), half(g)), a);
}
"""

/**
 * A glass panel - the controls card, the queue - over the liquid field. The layer's own
 * drawing is the field as it is behind the panel (aligned by [LiquidGlassField.syncPanel]);
 * this bends it at the panel's rim, the way a thick glass edge lenses what crosses it, and
 * lights the bevel: bright along the top, shade along the bottom, a soft sheen from above.
 * The darkening and the milk rise with frost, so the controls on top stay readable.
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
    // legibility under what sits on the panel, milkier as frost rises
    col *= half(1.0 - 0.16 - 0.26 * frost);
    col = mix(col, half3(1.0), half(0.05 + 0.08 * frost));

    // the bevel, lit from above
    float up = clamp(-n.y, 0.0, 1.0);
    float down = clamp(n.y, 0.0, 1.0);
    col += half3(rim * up * 0.38 + rim * rim * up * 0.22);
    col -= half3(rim * down * 0.20);
    // a soft sheen across the top of the panel
    col += half3(0.05 * (1.0 - smoothstep(0.0, uRes.y * 0.55, fragCoord.y)));

    return half4(col, 1.0);
}
"""

/**
 * The liquid field, shared by everything that needs to see it: the background canvas's own
 * shader (rendered small and scaled up), the glass lyrics' backdrop (full resolution), and
 * any number of panel-sized copies for the glass UI, each showing exactly the rectangle of
 * the field that is behind it. [sync] is called on the background's draw each frame; the
 * values are kept so the lyrics and the panels can sync their own copies on their draws.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class LiquidGlassField {
    /** The background's own shader, rendered at a third of the resolution. */
    val shader = android.graphics.RuntimeShader(LIQUID_GLASS_AGSL)

    /** The same field at full resolution, for the glass lyrics to look through. */
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

/**
 * Glass lyrics made by a shader: the lyric layer's own drawing is the coverage mask, and
 * the liquid field behind it is evaluated live. Made on Android 13+; callers hold it as
 * a [StageGlassText], which asks for no API level of its own.
 */
interface StageGlassText {
    /** The effect for the lyric layer. */
    val effect: androidx.compose.ui.graphics.RenderEffect

    /** Sets the current glass, frost and rim width (the rim scales with the font). */
    fun uniforms(glass: Float, frost: Float, edgePx: Float)

    /** Bumped whenever the field behind draws; 0 until it ever has, and nothing may look
     *  through the glass before that. */
    val tick: androidx.compose.runtime.LongState

    /** This glass under a motion blur of [blurPx], for the fast-word layer (API 31+). */
    fun chainedUnderBlur(blurPx: Float): androidx.compose.ui.graphics.RenderEffect
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class GlassTextEffect(liquidField: LiquidGlassField) : StageGlassText {
    private val liquid = liquidField
    private val shader = android.graphics.RuntimeShader(GLASS_TEXT_AGSL).also {
        it.setInputShader("uBackdrop", liquid.backdrop)
    }

    /** The raw effect, for chaining under a motion blur on the fast-word layer. */
    val androidEffect = RenderEffect.createRuntimeShaderEffect(shader, "uContent")

    override val effect = androidEffect.asComposeRenderEffect()

    override val tick: androidx.compose.runtime.LongState get() = liquid.frameTick

    override fun chainedUnderBlur(blurPx: Float) = RenderEffect.createChainEffect(
        androidEffect,
        RenderEffect.createBlurEffect(blurPx, blurPx, android.graphics.Shader.TileMode.DECAL),
    ).asComposeRenderEffect()

    override fun uniforms(glass: Float, frost: Float, edgePx: Float) {
        shader.setFloatUniform("uGlass", glass)
        shader.setFloatUniform("uFrost", frost)
        shader.setFloatUniform("uEdge", edgePx)
        shader.setFloatUniform("uBend", edgePx * 1.2f)
    }
}
