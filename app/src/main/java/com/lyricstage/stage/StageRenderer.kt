package com.lyricstage.stage

import android.os.Build
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import com.lyricstage.core.lyrics.LyricLine
import com.lyricstage.core.palette.StagePalette
import com.lyricstage.core.stage.ENTRY_LEAD_MS
import com.lyricstage.core.stage.LineTiming
import com.lyricstage.core.stage.LyricTemplate
import com.lyricstage.core.stage.Pose
import com.lyricstage.core.stage.REF_PX
import com.lyricstage.core.stage.Templates
import com.lyricstage.core.stage.WordPlacement
import com.lyricstage.core.stage.easeOut
import com.lyricstage.core.stage.templates.isSpotlight
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Which words a pass draws: settled and slow ones, or the fast blurred ones. */
enum class StageLayer { SETTLED, MOTION, ALL }

class StageColours(
    palette: StagePalette,
    highlightIntensity: Float,
    /** 0 solid lyrics, 1 clear glass that shows the background through it. */
    val glass: Float = 0f,
    /** For glass: 0 clear, 1 frosted (milky). */
    val frost: Float = 0f,
    /** The whole line reads as glass, the unsung words too (the controls are open). */
    val wholeLine: Boolean = false,
) {
    val text = Color(palette.text)
    // highlighted words glow in the palette's colours, lifted where the ripple rule put
    // them on the background's dark side: a light cover's dark ripple would sink a
    // highlighted word into the mid tones the stage draws
    val accents = palette.accents.map { lerp(text, Color(palette.highlightColour(it)), highlightIntensity.coerceIn(0f, 1f)) }
    val glow = highlightIntensity.coerceIn(0f, 1f)
}

/**
 * Draws one line of a [PreparedStage] for a playback position. Allocation free per frame:
 * poses and the skew matrix are reused. Word state comes from the clock, not from
 * recomposition, so seeking or scrubbing redraws correctly.
 */
class StageRenderer {
    private val pose = Pose()
    private val glyphPose = Pose()
    private val skew = Matrix()
    private val blurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /** Largest motion blur among the line's words right now (drives the motion layer). */
    /** [pos] is the word-timing position (playback position minus any audio sync shift). */
    fun motionBlur(prep: PreparedStage, li: Int, pos: Long, reduceMotion: Boolean): Float {
        if (reduceMotion || !blurSupported || li < 0) return 0f
        val layout = prep.plan.layouts[li]
        val line = prep.plan.lines[li]
        val template = Templates.of(layout.template)
        var b = 0f
        for (p in layout.words) {
            val since = sinceEnter(template, line.words[p.index].startMs, line, pos)
            if (since < 0) continue
            template.pose(p, since, layout.stage, pose)
            b = max(b, pose.blurPx)
        }
        return b
    }

    private fun sinceEnter(t: LyricTemplate, wordStart: Long, line: LyricLine, pos: Long): Long =
        if (t.showsUpcoming) pos - LineTiming.enterAtMs(line) else pos - (wordStart - ENTRY_LEAD_MS)

    fun DrawScope.drawLine(
        prep: PreparedStage,
        li: Int,
        pos: Long,
        colours: StageColours,
        reduceMotion: Boolean,
        layer: StageLayer,
        /** Audio sync correction for estimated word times (0 when words are timed by the source). */
        wordShiftMs: Long = 0,
        /** Left-to-right fill while a word is sung; off in battery saver (words light up whole). */
        sweep: Boolean = true,
        /** The layer is made glass by a shader that looks at it from outside: draw the words
         *  solid and shadowless, and let the shader do the glass. */
        plain: Boolean = false,
    ) {
        if (li < 0 || li >= prep.plan.lines.size) return
        glassAmount = if (sweep) colours.glass else 0f
        frostAmount = colours.frost
        wholeLine = colours.wholeLine
        plainDraw = plain
        // the line comes and goes on its own timestamp; only word timing is shifted
        val wpos = pos - wordShiftMs
        val plan = prep.plan
        val line = plan.lines[li]
        val layout = plan.layouts[li]
        val template = Templates.of(layout.template)
        val exit = easeOut(LineTiming.exitProgress(line, pos))
        val lineAlpha = 1f - exit
        if (lineAlpha <= 0f) return
        val highlights = plan.highlights[li]
        val accent = colours.accents[plan.accents[li] % colours.accents.size]

        if (layer != StageLayer.MOTION) {
            if (layout.template.isSpotlight && !reduceMotion) drawSpotlight(layout, line, wpos, colours, lineAlpha)
            drawSegments(prep, li, pos, colours, lineAlpha)
            drawSubtitle(prep, li, pos, colours, lineAlpha, plain)
        }

        for ((k, p) in layout.words.withIndex()) {
            val word = line.words[p.index]
            val since = sinceEnter(template, word.startMs, line, wpos)
            if (since < 0) continue
            if (reduceMotion) {
                pose.reset()
                pose.alpha = (since / 220f).coerceIn(0f, 1f)
            } else {
                template.pose(p, since, layout.stage, pose)
            }
            val moving = blurSupported && pose.blurPx > 0.5f
            when (layer) {
                StageLayer.SETTLED -> if (moving) continue
                StageLayer.MOTION -> if (!moving) continue
                StageLayer.ALL -> Unit
            }
            val fill = if (sweep) LineTiming.fill(word, wpos) else if (wpos >= word.startMs) 1f else 0f
            val held = if (reduceMotion) 0f else heldAmount(word, wpos, prep.heldMs[li])
            val highlighted = p.index in highlights
            val colour = when {
                word.backing -> colours.text.copy(alpha = 0.62f)
                highlighted -> accent
                else -> colours.text
            }
            val alpha = pose.alpha * lineAlpha
            // the line hands over with a plain fade now: it fades away where it is, without
            // lifting or sliding, so nothing moves between one line and the next
            // the text's own shadow blends smoothly from its resting look to a soft coloured
            // halo as a note is held, instead of switching at once. Off in the shader-glass
            // pass: glass is read by its light, and the shadow would fatten the rim the
            // shader measures
            val rest = when {
                plain -> null
                highlighted -> Shadow(accent.copy(alpha = 0.55f * colours.glow), Offset.Zero, p.fontPx * 0.28f)
                else -> Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, p.fontPx * 0.035f), p.fontPx * 0.18f)
            }
            val shadow = if (held > 0.01f && rest != null) {
                Shadow(
                    lerp(rest.color, colour.copy(alpha = 0.5f), held),
                    Offset(rest.offset.x * (1f - held), rest.offset.y * (1f - held)),
                    rest.blurRadius + (p.fontPx * 0.3f - rest.blurRadius) * held,
                )
            } else {
                rest
            }
            val growth = if (held > 0f) heldGrowth(word, wpos) else 0f

            withTransform({
                translate(p.x + pose.dx, p.y + pose.dy - held * growth * p.height * HELD_LIFT)
                val pivot = Offset(p.width / 2f, p.height / 2f)
                val rot = p.rotationDeg + pose.rotationDeg
                if (rot != 0f) rotate(rot, pivot)
                val grow = 1f + held * growth * HELD_GROW
                if (pose.scaleX != 1f || pose.scaleY != 1f || grow != 1f) scale(pose.scaleX * grow, pose.scaleY * grow, pivot)
                if (pose.skewX != 0f) {
                    skew.reset()
                    skew.values[Matrix.SkewX] = pose.skewX
                    translate(pivot.x, pivot.y)
                    transform(skew)
                    translate(-pivot.x, -pivot.y)
                }
            }) {
                if (held > 0.01f) drawBloom(p, fill, if (highlighted) accent else colour, alpha * held * (0.65f + 0.35f * colours.glow))
                val glyphs = prep.glyphs[li]?.get(k)
                if (glyphs != null && !reduceMotion) {
                    drawGlyphs(template, p, glyphs, prep.plan.measured[li].words[p.index].glyphOffsets, since, fill, colour, colours.text, alpha, shadow)
                } else {
                    drawWord(prep.words[li][k], p, fill, colour, colours.text, alpha, shadow)
                }
            }
        }
    }

    /**
     * Light behind a held word: a broad soft glow over the whole word, and a brighter one
     * that travels with the sweep, so the light follows the voice through the note. Radial
     * gradients drawn in screen blend: no hard edges, nothing clipped.
     */
    private fun DrawScope.drawBloom(p: WordPlacement, fill: Float, colour: Color, strength: Float) {
        if (strength <= 0.01f) return
        val cy = p.height / 2f
        val r = p.height * 1.05f
        val centre = Offset(p.width / 2f, cy)
        val stretch = max(1f, p.width * 0.62f / r)
        scale(stretch, 0.8f, centre) {
            drawCircle(
                Brush.radialGradient(
                    0f to colour.copy(alpha = 0.30f * strength),
                    0.55f to colour.copy(alpha = 0.12f * strength),
                    1f to Color.Transparent,
                    center = centre, radius = r,
                ),
                r, centre, blendMode = BlendMode.Screen,
            )
        }
        val head = Offset(p.width * fill.coerceIn(0.05f, 0.95f), cy)
        val hr = r * 0.85f
        drawCircle(
            Brush.radialGradient(
                0f to colour.copy(alpha = 0.40f * strength),
                0.5f to colour.copy(alpha = 0.14f * strength),
                1f to Color.Transparent,
                center = head, radius = hr,
            ),
            hr, head, blendMode = BlendMode.Screen,
        )
    }

    /**
     * Spotlight layout: a soft warm light behind the sung word that glides from the previous
     * word to the current one as it starts. Drawn from playback position only, so seeking
     * shows the right frame.
     */
    private fun DrawScope.drawSpotlight(layout: com.lyricstage.core.stage.TemplateLayout, line: LyricLine, pos: Long, colours: StageColours, lineAlpha: Float) {
        val placed = layout.words
        if (placed.isEmpty()) return
        // the word being sung, or the last one sung; before the first word, the light waits on it
        var cur = 0
        for ((k, p) in placed.withIndex()) if (line.words[p.index].startMs <= pos) cur = k
        val now = placed[cur]
        val prev = placed[(cur - 1).coerceAtLeast(0)]
        val start = line.words[now.index].startMs
        val t = smooth((pos - start) / SPOT_GLIDE_MS.toFloat())
        val cx = prev.x + prev.width / 2f + ((now.x + now.width / 2f) - (prev.x + prev.width / 2f)) * t
        val cy = prev.y + prev.height / 2f + ((now.y + now.height / 2f) - (prev.y + prev.height / 2f)) * t
        val first = line.words[placed[0].index].startMs
        // fades up as the first word comes, and out when the last word has been sung
        val last = line.words[placed.last().index].endMs
        val on = smooth((pos - (first - SPOT_GLIDE_MS)) / SPOT_GLIDE_MS.toFloat()) * (1f - smooth((pos - last) / 600f))
        if (on <= 0.01f) return
        val r = now.height * 1.7f
        val centre = Offset(cx, cy)
        drawCircle(
            Brush.radialGradient(
                0f to lerp(colours.text, Color.White, 0.5f).copy(alpha = 0.30f * on * lineAlpha),
                0.55f to colours.text.copy(alpha = 0.10f * on * lineAlpha),
                1f to Color.Transparent,
                center = centre, radius = r,
            ),
            r, centre, blendMode = BlendMode.Screen,
        )
    }

    /** Dim base, then the sung part in full colour, swept left to right. */
    private fun DrawScope.drawWord(
        tl: TextLayoutResult, p: WordPlacement, fill: Float, colour: Color, dimColour: Color, alpha: Float, shadow: Shadow?,
        clipLeft: Float = 0f,
    ) {
        val topLeft = Offset(0f, p.ascent - tl.firstBaseline)
        if (fill >= 1f) {
            drawSung(tl, p, colour, topLeft, alpha, shadow)
            return
        }
        if (wholeLine && glassAmount > 0.01f) {
            // the controls are open and the whole line is glass: the unsung part is etched
            // glass at the dim opacity, not a flat word
            drawGlassText(tl, dimColour, topLeft, alpha * DIM, shadow, glassAmount, frostAmount, p.fontPx)
        } else {
            drawText(tl, dimColour, topLeft, alpha * DIM)
        }
        if (fill > 0f) {
            val right = p.width * fill - clipLeft
            if (right > 0f) clipRect(left = -p.width, top = -p.height, right = right, bottom = p.height * 2f) {
                drawSung(tl, p, colour, topLeft, alpha, shadow)
            }
        }
    }

    /**
     * A sung word: solid, or as glass. Glass lets the background show through the letters,
     * with a light catching their top and a bright rim; frost turns the body milky, like
     * frosted glass, so it stays readable over busy colour. When the layer is turned into
     * glass wholesale by a shader outside the drawing, the word is drawn solid here and the
     * shader does the glass.
     */
    private fun DrawScope.drawSung(tl: TextLayoutResult, p: WordPlacement, colour: Color, topLeft: Offset, alpha: Float, shadow: Shadow?) {
        if (plainDraw) {
            drawText(tl, colour, topLeft, alpha)
        } else {
            drawGlassText(tl, colour, topLeft, alpha, shadow, glassAmount, frostAmount, p.fontPx)
        }
    }

    /** Set from [StageColours] at the start of each line draw. */
    private var glassAmount = 0f
    private var frostAmount = 0f
    private var wholeLine = false
    private var plainDraw = false

    private fun DrawScope.drawGlyphs(
        template: LyricTemplate, p: WordPlacement, glyphs: List<TextLayoutResult>, offsetsAtRef: FloatArray,
        since: Long, fill: Float, colour: Color, dimColour: Color, alpha: Float, shadow: Shadow?,
    ) {
        val k = p.fontPx / REF_PX
        for ((g, tl) in glyphs.withIndex()) {
            template.glyphPose(p, g, since, glyphPose)
            val a = alpha * glyphPose.alpha
            if (a <= 0f) continue
            val gx = (offsetsAtRef.getOrNull(g) ?: 0f) * k
            withTransform({
                translate(gx + glyphPose.dx, glyphPose.dy)
                if (glyphPose.scaleX != 1f || glyphPose.scaleY != 1f) {
                    scale(glyphPose.scaleX, glyphPose.scaleY, Offset(tl.advance / 2f, p.height / 2f))
                }
            }) {
                drawWord(tl, p, fill, colour, dimColour, a, shadow, clipLeft = gx)
            }
        }
    }

    /** Graph connectors draw in as the word they lead to starts. */
    private fun DrawScope.drawSegments(prep: PreparedStage, li: Int, pos: Long, colours: StageColours, lineAlpha: Float) {
        val layout = prep.plan.layouts[li]
        if (layout.segments.isEmpty()) return
        val line = prep.plan.lines[li]
        val width = max(2f, (layout.words.firstOrNull()?.fontPx ?: 40f) * 0.045f)
        for (s in layout.segments) {
            val start = line.words[s.revealWord].startMs - ENTRY_LEAD_MS
            val reveal = easeOut((pos - start) / 280f)
            if (reveal <= 0f) continue
            val a = Offset(s.x0, s.y0)
            val b = Offset(s.x0 + (s.x1 - s.x0) * reveal, s.y0 + (s.y1 - s.y0) * reveal)
            drawLine(colours.text.copy(alpha = 0.5f * lineAlpha), a, b, width, StrokeCap.Round)
            drawCircle(colours.text.copy(alpha = 0.8f * lineAlpha), width * 1.6f, a)
            if (reveal >= 1f) drawCircle(colours.text.copy(alpha = 0.8f * lineAlpha), width * 1.6f, b)
        }
    }

    private fun DrawScope.drawSubtitle(prep: PreparedStage, li: Int, pos: Long, colours: StageColours, lineAlpha: Float, plain: Boolean = false) {
        val tl = prep.subtitles[li] ?: return
        val area = prep.plan.layouts[li].subtitleArea ?: return
        val fade = ((pos - LineTiming.enterAtMs(prep.plan.lines[li])) / 300f).coerceIn(0f, 1f)
        drawText(
            tl,
            colours.text,
            Offset(area.centreX - tl.size.width / 2f, area.top),
            alpha = 0.72f * lineAlpha * fade,
            shadow = if (plain) null else Shadow(Color.Black.copy(alpha = 0.5f), Offset(0f, 2f), 8f),
        )
    }

    /**
     * Milliseconds until the drawing next changes, for the battery saver loop: 0 while a word
     * is fading in or the line is leaving, otherwise the time to the next word or line event.
     * Assumes battery saver drawing (fades only, no sweep).
     */
    fun idleMs(prep: PreparedStage, li: Int, pos: Long): Long {
        var next = Long.MAX_VALUE
        if (li >= 0) {
            val line = prep.plan.lines[li]
            for (w in line.words) {
                val enter = w.startMs - ENTRY_LEAD_MS
                if (pos in enter until enter + FADE_MS) return 0
                if (enter > pos) next = minOf(next, enter)
                if (w.startMs > pos) next = minOf(next, w.startMs) // word turns from dim to full
            }
            val exitAt = line.endMs - LineTiming.EXIT_MS
            if (pos in exitAt until line.endMs) return 0
            if (exitAt > pos) next = minOf(next, exitAt)
        }
        prep.plan.lines.firstOrNull { LineTiming.enterAtMs(it) > pos }?.let { next = minOf(next, LineTiming.enterAtMs(it)) }
        return if (next == Long.MAX_VALUE) 1_000 else (next - pos).coerceAtLeast(0)
    }

    /**
     * Instrumental passages (and the intro): three slowly pulsing dots until the next line
     * is about to enter. Driven by playback position, so they freeze when paused.
     */
    fun DrawScope.drawGapDots(prep: PreparedStage, pos: Long, colours: StageColours, reduceMotion: Boolean) {
        val next = prep.plan.lines.firstOrNull { it.startMs > pos } ?: return
        if (next.startMs - pos < GAP_MIN_MS) return
        val r = size.minDimension * 0.012f
        val cy = size.height / 2f
        for (i in 0..2) {
            val phase = pos / 900.0 - i * 0.22
            val a = if (reduceMotion) 0.6f else (0.3 + 0.7 * (0.5 + 0.5 * sin(2 * PI * phase))).toFloat()
            drawCircle(colours.text.copy(alpha = a * 0.85f), r, Offset(size.width / 2f + (i - 1) * r * 3.6f, cy))
        }
    }

    /**
     * How much a long-held word is lifted and grown, 0 to 1: rises while the note starts,
     * holds, and eases back once it ends. Exactly 0 outside the note, so settled text stays
     * at scale 1.
     */
    private fun heldAmount(word: com.lyricstage.core.lyrics.LyricWord, pos: Long, thresholdMs: Long): Float {
        if (word.durationMs < thresholdMs || pos < word.startMs) return 0f
        if (pos >= word.endMs + HELD_RELEASE_MS) return 0f
        val rise = smooth((pos - word.startMs) / HELD_RISE_MS.toFloat())
        val fall = 1f - smooth((pos - word.endMs) / HELD_RELEASE_MS.toFloat())
        return min(rise, fall).coerceIn(0f, 1f)
    }

    /**
     * Lift and growth build through the note rather than jumping at its start: 55 % on
     * arrival, the rest as the note goes on, so a long note swells the way it is sung.
     */
    private fun heldGrowth(word: com.lyricstage.core.lyrics.LyricWord, pos: Long): Float {
        val q = ((pos - word.startMs) / max(1L, word.durationMs).toFloat()).coerceIn(0f, 1f)
        return 0.55f + 0.45f * smooth(q)
    }

    /** Smoothstep: eases in and out, unlike [easeOut], so nothing starts with a jolt. */
    private fun smooth(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    companion object {
        private const val SPOT_GLIDE_MS = 240L
        private const val HELD_GROW = 0.08f
        private const val HELD_LIFT = 0.05f
        private const val HELD_RISE_MS = 380L
        private const val HELD_RELEASE_MS = 420L

        /** Gaps shorter than this show nothing rather than dots. */
        const val GAP_MIN_MS = 2_500L

        /** Battery saver fade-in length; matches the reduce-motion fade. */
        private const val FADE_MS = 240L

        /** Opacity of words not yet sung. */
        const val DIM = 0.32f
    }
}

/**
 * Text drawn solid, or as glass when [glass] is above zero. Real glass is read by its light,
 * not by an outline: a bright edge along the top of the letters where the light catches them,
 * a dark edge underneath, a translucent body the background shows through, and a soft sheen
 * over the upper face. [frost] turns the body milky, like frosted glass, so it stays readable
 * over busy colour. Shared by the stage and the Settings preview.
 */
fun DrawScope.drawGlassText(
    tl: TextLayoutResult,
    colour: Color,
    topLeft: Offset,
    alpha: Float,
    shadow: Shadow?,
    glass: Float,
    frost: Float,
    fontPx: Float,
) {
    val g = glass
    if (g <= 0.01f) {
        drawText(tl, colour, topLeft, alpha, shadow)
        return
    }
    // how far the lit edges sit off the glyph: thick letters get a deeper bevel
    val lift = (fontPx * 0.018f).coerceIn(1.5f, 4f)
    val bodyShadow = shadow?.let { Shadow(it.color.copy(alpha = it.color.alpha * (1f - 0.6f * g)), it.offset, it.blurRadius) }
    // the underside of the glass, in shade
    drawText(tl, Color.Black.copy(alpha = 0.30f * g), topLeft + Offset(0f, lift), alpha)
    // the light catching the top of the letters
    drawText(tl, Color.White.copy(alpha = 0.45f * g), topLeft - Offset(0f, lift), alpha)
    // the body: translucent, milky with frost, the background showing through it
    val body = lerp(colour, Color.White, 0.55f * frost)
    val bodyAlpha = (1f - 0.75f * g + 0.45f * g * frost).coerceIn(0.15f, 1f)
    drawText(tl, body, topLeft, alpha * bodyAlpha, bodyShadow)
    // a soft sheen on the upper face of the letters
    drawText(
        tl,
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.38f * g),
            0.5f to Color.White.copy(alpha = 0.05f * g),
            1f to Color.Transparent,
            startY = topLeft.y, endY = topLeft.y + tl.size.height,
        ),
        topLeft,
        alpha = alpha,
    )
}
