package com.lyricstage.background

import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.text.TextLayoutResult
import java.util.IdentityHashMap
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A lyric word as a piece of liquid glass, the same material as the panels. The letters
 * are painted with the field behind them, but not as it is: the rim of every letter is a
 * bevel that bends the field - the background is seen pulled in from outside the letter,
 * blue bent a little further than red - and the bevel is lit from the top left, with a
 * weaker return light on the opposite edge and a little shade under it. The shape of the
 * bevel comes from [uMask], the word's own coverage blurred: its value says how deep
 * inside the letter a pixel is, its slope says which way is out.
 */
internal const val TEXT_GLASS_AGSL = """
uniform shader uField;
uniform shader uMask;
uniform float2 uMaskOrigin;
uniform float uMaskScale;
uniform float uTap;
uniform float uBend;
uniform float uLift;
uniform float uSpec;
uniform float uDisp;
uniform float uFrost;
uniform float uFrostR;
uniform half4 uTint;

float cover(float2 c) {
    return uMask.eval((c - uMaskOrigin) * uMaskScale).a;
}

half4 main(float2 c) {
    float b = cover(c);
    float2 g = float2(cover(c + float2(uTap, 0.0)) - cover(c - float2(uTap, 0.0)),
                      cover(c + float2(0.0, uTap)) - cover(c - float2(0.0, uTap)));
    float gl = length(g);
    float2 n = gl > 0.0004 ? -g / gl : float2(0.0, 0.0);
    float depth = clamp((b - 0.5) * 2.0, 0.0, 1.0);
    float e = 1.0 - depth;
    float rim = e * e;
    float thin = rim * rim * rim * e;

    // the bevel bends the field; each colour a little differently
    float2 off = n * (rim * uBend);
    half3 col;
    col.r = uField.eval(c + off * (1.0 - uDisp)).r;
    col.g = uField.eval(c + off).g;
    col.b = uField.eval(c + off * (1.0 + uDisp)).b;

    // frost: what is seen through the letter goes soft and milky
    if (uFrost > 0.02) {
        half3 soft = uField.eval(c + off + float2(uFrostR, 0.0)).rgb
                   + uField.eval(c + off - float2(uFrostR, 0.0)).rgb
                   + uField.eval(c + off + float2(0.0, uFrostR)).rgb
                   + uField.eval(c + off - float2(0.0, uFrostR)).rgb;
        col = mix(col, soft * 0.25, half(min(1.0, uFrost * 1.4)));
        col = mix(col, half3(1.0), half(0.34 * uFrost));
    }

    // glass deepens the colour it shows a little, and lifts it off the background
    half lum = dot(col, half3(0.299, 0.587, 0.114));
    col = mix(half3(lum), col, 1.18);
    col = col + half(uLift) * (half3(1.0) - col);
    col = mix(col, uTint.rgb, uTint.a);

    // light from the top left on the bevel, a weaker one coming back from the far edge
    float2 L = float2(-0.62, -0.78);
    float d = dot(n, L);
    float d1 = clamp(d, 0.0, 1.0);
    float d2 = clamp(-d, 0.0, 1.0);
    float hi = thin * (pow(d1, 1.4) * 0.95 + d2 * d2 * 0.38) + rim * d1 * 0.10;
    float lo = rim * d2 * 0.16;
    col = col * half(1.0 - lo) + half(hi * uSpec) * (half3(1.0) - col * 0.25);
    return half4(clamp(col, half3(0.0), half3(1.0)), 1.0);
}
"""

/** How one pass of a glass word is drawn. */
class GlassLook(
    /** Rim bend, as a share of the font size. */
    val bend: Float,
    val lift: Float,
    val spec: Float,
    val disp: Float,
)

object GlassLooks {
    /** A sung word: full, clear glass. */
    val SUNG = GlassLook(bend = 0.36f, lift = 0.17f, spec = 1.15f, disp = 0.20f)
    /** A word not sung yet: a thinner, fainter glass. */
    val WAITING = GlassLook(bend = 0.08f, lift = 0.05f, spec = 0.55f, disp = 0.12f)
}

/** A word's blurred coverage, at half size: the height map its glass is shaped from. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class WordMask private constructor(val shader: BitmapShader, val pad: Float) {
    companion object {
        const val SCALE = 0.5f

        fun of(tl: TextLayoutResult, fontPx: Float): WordMask {
            val pad = ceil(fontPx * 0.16f)
            val w = max(1, ceil((tl.size.width + 2 * pad) * SCALE).toInt())
            val h = max(1, ceil((tl.size.height + 2 * pad) * SCALE).toInt())
            val image = ImageBitmap(w, h)
            val canvas = Canvas(image)
            canvas.scale(SCALE, SCALE)
            canvas.translate(pad, pad)
            tl.multiParagraph.paint(canvas, Color.White)
            val bmp = image.asAndroidBitmap()
            val px = IntArray(w * h)
            bmp.getPixels(px, 0, w, 0, 0, w, h)
            val a = IntArray(w * h) { px[it] ushr 24 }
            val tmp = IntArray(w * h)
            // three box passes come close to a Gaussian of the bevel's width
            val r = max(1, (fontPx * 0.053f * SCALE / 1.2f).roundToInt())
            repeat(3) {
                box(a, tmp, w, h, r, horizontal = true)
                box(tmp, a, w, h, r, horizontal = false)
            }
            for (i in px.indices) {
                val v = a[i]
                px[i] = (v shl 24) or (v shl 16) or (v shl 8) or v
            }
            bmp.setPixels(px, 0, w, 0, 0, w, h)
            return WordMask(BitmapShader(bmp, Shader.TileMode.DECAL, Shader.TileMode.DECAL).apply { filterMode = BitmapShader.FILTER_MODE_LINEAR }, pad)
        }

        private fun box(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
            val n = 2 * r + 1
            val lines = if (horizontal) h else w
            val len = if (horizontal) w else h
            val step = if (horizontal) 1 else w
            for (l in 0 until lines) {
                val base = if (horizontal) l * w else l
                var sum = 0
                for (i in -r..r) if (i in 0 until len) sum += src[base + i * step]
                for (i in 0 until len) {
                    dst[base + i * step] = sum / n
                    val out = i - r
                    val inn = i + r + 1
                    if (out >= 0) sum -= src[base + out * step]
                    if (inn < len) sum += src[base + inn * step]
                }
            }
        }
    }
}

/**
 * The glass lyric words over one [LiquidGlassField]: a shader per word and pass, so no
 * pass depends on another's uniforms, and a height map per word layout, made once.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class GlassWords(private val field: LiquidGlassField) {
    private class Slot(val shader: RuntimeShader) {
        val brush = ShaderBrush(shader)
    }

    private val slots = HashMap<Int, Slot>()
    private val masks = IdentityHashMap<TextLayoutResult, WordMask>()

    /** True once a shader failed to build: the caller keeps to the plain field brush. */
    private var broken = false

    fun brush(slot: Int, tl: TextLayoutResult, topLeft: Offset, fontPx: Float, look: GlassLook, tint: Color?, frost: Float): Brush? {
        if (broken || !field.ready) return null
        val s = slots[slot] ?: runCatching { Slot(RuntimeShader(TEXT_GLASS_AGSL)) }.getOrElse {
            broken = true
            return null
        }.also { slots[slot] = it }
        val m = masks[tl] ?: runCatching { WordMask.of(tl, fontPx) }.getOrElse {
            broken = true
            return null
        }.also {
            if (masks.size > 160) masks.clear()
            masks[tl] = it
        }
        val sh = s.shader
        // set again each frame: the field's own uniforms have moved since the last one
        sh.setInputShader("uField", field.backdrop)
        sh.setInputShader("uMask", m.shader)
        sh.setFloatUniform("uMaskOrigin", topLeft.x - m.pad, topLeft.y - m.pad)
        sh.setFloatUniform("uMaskScale", WordMask.SCALE)
        sh.setFloatUniform("uTap", 2f)
        sh.setFloatUniform("uBend", fontPx * look.bend)
        sh.setFloatUniform("uLift", look.lift)
        sh.setFloatUniform("uSpec", look.spec)
        sh.setFloatUniform("uDisp", look.disp)
        sh.setFloatUniform("uFrost", frost.coerceIn(0f, 1f))
        sh.setFloatUniform("uFrostR", fontPx * 0.14f)
        if (tint != null) sh.setFloatUniform("uTint", tint.red, tint.green, tint.blue, 0.46f)
        else sh.setFloatUniform("uTint", 1f, 1f, 1f, 0f)
        return s.brush
    }
}
