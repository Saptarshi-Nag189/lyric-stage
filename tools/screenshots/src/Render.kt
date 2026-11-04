package shots

import com.lyricstage.core.lyrics.LyricLine
import com.lyricstage.core.lyrics.LyricsBuilder
import com.lyricstage.core.palette.Colour
import com.lyricstage.core.stage.ENTRY_LEAD_MS
import com.lyricstage.core.stage.Highlights
import com.lyricstage.core.stage.LineTiming
import com.lyricstage.core.stage.MeasuredLine
import com.lyricstage.core.stage.Pose
import com.lyricstage.core.stage.REF_PX
import com.lyricstage.core.stage.SizeF
import com.lyricstage.core.stage.TemplateId
import com.lyricstage.core.stage.Templates
import com.lyricstage.core.stage.TextMeasure
import com.lyricstage.core.stage.WordMetrics
import com.lyricstage.core.text.Graphemes
import com.lyricstage.core.text.Script
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.MultipleGradientPaint
import java.awt.RadialGradientPaint
import java.awt.RenderingHints
import java.awt.font.FontRenderContext
import java.awt.font.TextLayout
import java.awt.geom.AffineTransform
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.Point2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/*
 * Renders README mockups with LyricStage's real layout engine (core module): line fitting,
 * templates, entrance poses, highlight choice and fill timing are the app's own code. Only the
 * drawing is Java2D instead of Compose, and the liquid glass background is approximated.
 */

val FONT_DIR = File(System.getProperty("fonts"))
val OUT = File(System.getProperty("out")).apply { mkdirs() }
val SAMPLES = File(System.getProperty("samples"))

fun loadFont(name: String): Font = Font.createFont(Font.TRUETYPE_FONT, File(FONT_DIR, "$name.ttf"))

val fBlack = mapOf(
    Script.LATIN to loadFont("inter_900"),
    Script.DEVANAGARI to loadFont("noto_sans_devanagari_900"),
    Script.BENGALI to loadFont("noto_sans_bengali_900"),
    Script.JAPANESE to loadFont("noto_sans_jp_900"),
)
val fSemi = loadFont("inter_600")
val fRegular = loadFont("inter_400")

fun stageFont(script: Script, px: Float): Font = (fBlack[script] ?: fBlack.getValue(Script.LATIN)).deriveFont(px)

val FRC = FontRenderContext(null, true, true)

class AwtMeasure : TextMeasure {
    override fun measure(text: String, line: LyricLine): WordMetrics {
        val font = stageFont(line.script, REF_PX)
        val tl = TextLayout(text, font, FRC)
        val lm = font.getLineMetrics(text, FRC)
        val glyphs = Graphemes.split(text)
        val offsets = FloatArray(glyphs.size)
        var prefix = ""
        for ((i, g) in glyphs.withIndex()) {
            offsets[i] = if (prefix.isEmpty()) 0f else TextLayout(prefix, font, FRC).advance
            prefix += g
        }
        return WordMetrics(tl.advance, lm.ascent, lm.descent, glyphs, offsets)
    }
}

fun hex(c: Int, a: Float = 1f) = Color(Colour.red(c), Colour.green(c), Colour.blue(c), (a * 255).toInt().coerceIn(0, 255))

/** Three-pass box blur on ARGB, close to a gaussian. */
fun blur(img: BufferedImage, radius: Int): BufferedImage {
    if (radius < 1) return img
    val w = img.width
    val h = img.height
    var src = img.getRGB(0, 0, w, h, null, 0, w)
    var dst = IntArray(w * h)
    repeat(3) {
        boxH(src, dst, w, h, radius)
        boxV(dst, src, w, h, radius)
    }
    val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    out.setRGB(0, 0, w, h, src, 0, w)
    return out
}

private fun boxH(s: IntArray, d: IntArray, w: Int, h: Int, r: Int) {
    val div = 2 * r + 1
    for (y in 0 until h) {
        var a = 0; var rr = 0; var g = 0; var b = 0
        val row = y * w
        for (i in -r..r) {
            val p = s[row + i.coerceIn(0, w - 1)]
            a += p ushr 24; rr += (p shr 16) and 255; g += (p shr 8) and 255; b += p and 255
        }
        for (x in 0 until w) {
            d[row + x] = ((a / div) shl 24) or ((rr / div) shl 16) or ((g / div) shl 8) or (b / div)
            val pin = s[row + (x + r + 1).coerceAtMost(w - 1)]
            val pout = s[row + (x - r).coerceAtLeast(0)]
            a += (pin ushr 24) - (pout ushr 24)
            rr += ((pin shr 16) and 255) - ((pout shr 16) and 255)
            g += ((pin shr 8) and 255) - ((pout shr 8) and 255)
            b += (pin and 255) - (pout and 255)
        }
    }
}

private fun boxV(s: IntArray, d: IntArray, w: Int, h: Int, r: Int) {
    val div = 2 * r + 1
    for (x in 0 until w) {
        var a = 0; var rr = 0; var g = 0; var b = 0
        for (i in -r..r) {
            val p = s[i.coerceIn(0, h - 1) * w + x]
            a += p ushr 24; rr += (p shr 16) and 255; g += (p shr 8) and 255; b += p and 255
        }
        for (y in 0 until h) {
            d[y * w + x] = ((a / div) shl 24) or ((rr / div) shl 16) or ((g / div) shl 8) or (b / div)
            val pin = s[(y + r + 1).coerceAtMost(h - 1) * w + x]
            val pout = s[(y - r).coerceAtLeast(0) * w + x]
            a += (pin ushr 24) - (pout ushr 24)
            rr += ((pin shr 16) and 255) - ((pout shr 16) and 255)
            g += ((pin shr 8) and 255) - ((pout shr 8) and 255)
            b += (pin and 255) - (pout and 255)
        }
    }
}

fun Graphics2D.hq() {
    setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
    setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
    setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
}

data class Pal(val base: Int, val accents: List<Int>)

/** Body text colour, as PaletteBuilder makes it: near white with a hint of the cover's hue. */
fun textTint(p: Pal): Color {
    val (h, sat, _) = Colour.toHsl(p.accents[0]).let { Triple(it[0], it[1], it[2]) }
    return hex(Colour.fromHsl(h, (sat * 0.45f).coerceAtMost(0.4f), 0.93f))
}

/** Approximation of the AGSL liquid glass: soft pools of accent light, a glass sheen, vignette, grain. */
fun background(w: Int, h: Int, pal: Pal, seed: Int, ripple: Point2D.Float?): BufferedImage {
    val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    val g = img.createGraphics()
    g.hq()
    g.color = hex(pal.base)
    g.fillRect(0, 0, w, h)
    val r = Random(seed)
    val pools = listOf(0, 1, 2, 0, 1)
    for ((i, ai) in pools.withIndex()) {
        val cx = w * (0.15f + r.nextFloat() * 0.7f)
        val cy = h * (0.1f + i * 0.2f + r.nextFloat() * 0.1f)
        val rad = max(w, h) * (0.35f + r.nextFloat() * 0.2f)
        val c = pal.accents[ai]
        g.paint = RadialGradientPaint(
            Point2D.Float(cx, cy), rad, floatArrayOf(0f, 0.55f, 1f),
            arrayOf(hex(c, 0.55f), hex(c, 0.18f), hex(c, 0f)),
        )
        g.fillRect(0, 0, w, h)
    }
    // glass sheen: a soft diagonal band of light
    g.paint = GradientPaint(0f, h * 0.25f, Color(255, 255, 255, 0), w * 0.6f, h * 0.55f, Color(255, 255, 255, 22), true)
    g.fillRect(0, 0, w, h)
    if (ripple != null) {
        for (k in 0 until 3) {
            val rr = w * (0.08f + k * 0.07f)
            g.stroke = BasicStroke(w * 0.006f)
            g.color = Color(255, 255, 255, 34 - k * 10)
            g.draw(Ellipse2D.Float(ripple.x - rr, ripple.y - rr, rr * 2, rr * 2))
        }
    }
    g.dispose()
    val soft = blur(img, (w * 0.01f).toInt())
    val g2 = soft.createGraphics()
    g2.hq()
    // keep the brightest spots well under the white type, then vignette
    g2.color = Color(0, 0, 0, 70)
    g2.fillRect(0, 0, w, h)
    g2.paint = RadialGradientPaint(
        Point2D.Float(w / 2f, h / 2f), max(w, h) * 0.75f, floatArrayOf(0f, 0.6f, 1f),
        arrayOf(Color(0, 0, 0, 0), Color(0, 0, 0, 60), Color(0, 0, 0, 170)),
        MultipleGradientPaint.CycleMethod.NO_CYCLE,
    )
    g2.fillRect(0, 0, w, h)
    g2.dispose()
    val rnd = Random(seed + 7)
    for (y in 0 until h step 1) for (x in 0 until w step 2) {
        if (rnd.nextFloat() < 0.15f) {
            val p = soft.getRGB(x, y)
            val d = (rnd.nextFloat() - 0.5f) * 10
            val rr = ((p shr 16 and 255) + d).toInt().coerceIn(0, 255)
            val gg = ((p shr 8 and 255) + d).toInt().coerceIn(0, 255)
            val bb = ((p and 255) + d).toInt().coerceIn(0, 255)
            soft.setRGB(x, y, (255 shl 24) or (rr shl 16) or (gg shl 8) or bb)
        }
    }
    return soft
}

class Shot(
    val name: String,
    val file: String,
    val prefix: String,
    val template: TemplateId,
    val pal: Pal,
    /** Which word is mid-fill, as a fraction of the line. */
    val at: Float,
    val seed: Int,
    val playSeed: Int,
    /** How far through that word's sung time the frame is. */
    val fill: Float = 0.5f,
)

const val W = 1080
const val H = 2340

/** Extra sample files that only the screenshots use. */
val EXTRA_SAMPLES = File(System.getProperty("extraSamples") ?: "samples")

fun findLine(file: String, prefix: String): LyricLine {
    val src = File(EXTRA_SAMPLES, "$file.lrc").takeIf { it.exists() } ?: File(SAMPLES, "$file.lrc")
    val lyrics = LyricsBuilder.fromLrc(src.readText(), 213_000)
    return lyrics.lines.first { it.text.startsWith(prefix) }
}

/** Draws one stage frame and returns it plus the line's centre, for the ripple. */
fun renderStage(s: Shot, dim: Float = 1f): BufferedImage {
    val line = findLine(s.file, s.prefix)
    val measured = MeasuredLine.measure(line, AwtMeasure())
    val stage = SizeF(W.toFloat(), H.toFloat())
    val template = Templates.of(s.template)
    val layout = template.layout(measured, stage, s.seed)
    val k = (line.words.size * s.at).toInt().coerceIn(0, line.words.lastIndex)
    val word = line.words[k]
    val pos = word.startMs + (word.durationMs * s.fill).toLong()
    val highlights = Highlights.pick(line, s.playSeed)
    val accent = s.pal.accents[Highlights.accentIndex(line, s.playSeed)]

    val rippleAt = layout.words.firstOrNull { it.index == k }?.let { Point2D.Float(it.x + it.width / 2, it.y + it.height / 2) }
    val img = background(W, H, s.pal, s.seed, rippleAt)

    // three layers: drop shadows, accent glow, then the type itself
    val shadow = BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB)
    val glow = BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB)
    val type = BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB)
    val gs = shadow.createGraphics().apply { hq() }
    val gg = glow.createGraphics().apply { hq() }
    val gt = type.createGraphics().apply { hq() }

    // Graph connectors
    for (seg in layout.segments) {
        val start = line.words[seg.revealWord].startMs - ENTRY_LEAD_MS
        val reveal = ((pos - start) / 280f).coerceIn(0f, 1f)
        if (reveal <= 0f) continue
        val sw = max(3f, (layout.words.firstOrNull()?.fontPx ?: 40f) * 0.045f)
        gt.stroke = BasicStroke(sw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        gt.color = Color(255, 255, 255, 128)
        val x1 = seg.x0 + (seg.x1 - seg.x0) * reveal
        val y1 = seg.y0 + (seg.y1 - seg.y0) * reveal
        gt.draw(Line2D.Float(seg.x0, seg.y0, x1, y1))
        gt.color = Color(255, 255, 255, 210)
        val d = sw * 1.6f
        gt.fill(Ellipse2D.Float(seg.x0 - d, seg.y0 - d, d * 2, d * 2))
        if (reveal >= 1f) gt.fill(Ellipse2D.Float(x1 - d, y1 - d, d * 2, d * 2))
    }

    val pose = Pose()
    val glyphPose = Pose()
    for (p in layout.words) {
        val w = line.words[p.index]
        val since = if (template.showsUpcoming) pos - LineTiming.enterAtMs(line) else pos - (w.startMs - ENTRY_LEAD_MS)
        if (since < 0) continue
        template.pose(p, since, stage, pose)
        val fill = LineTiming.fill(w, pos)
        val hl = p.index in highlights
        val colour = when {
            w.backing -> Color(255, 255, 255, 160)
            hl -> hex(accent)
            else -> textTint(s.pal)
        }
        val font = stageFont(line.script, p.fontPx)
        val at = AffineTransform()
        at.translate((p.x + pose.dx).toDouble(), (p.y + pose.dy).toDouble())
        val cx = p.width / 2.0
        val cy = p.height / 2.0
        val rot = p.rotationDeg + pose.rotationDeg
        if (rot != 0f) at.rotate(Math.toRadians(rot.toDouble()), cx, cy)
        if (pose.scaleX != 1f || pose.scaleY != 1f) {
            at.translate(cx, cy); at.scale(pose.scaleX.toDouble(), pose.scaleY.toDouble()); at.translate(-cx, -cy)
        }
        if (pose.skewX != 0f) {
            at.translate(cx, cy); at.shear(pose.skewX.toDouble(), 0.0); at.translate(-cx, -cy)
        }
        val alpha = pose.alpha

        fun drawPieces(target: Graphics2D, c: Color, a: Float, clipRight: Float?) {
            val old = target.transform
            val oldClip = target.clip
            target.transform(at)
            if (clipRight != null) target.clipRect(-p.width.toInt(), -p.height.toInt(), (p.width + clipRight).toInt(), (p.height * 3).toInt())
            target.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a.coerceIn(0f, 1f))
            target.color = c
            val glyphs = if (template.perGlyph) measured.words[p.index].glyphs else listOf(w.text)
            val offsets = if (template.perGlyph) measured.words[p.index].glyphOffsets else floatArrayOf(0f)
            val k2 = p.fontPx / REF_PX
            for ((gi, gtext) in glyphs.withIndex()) {
                var gx = offsets.getOrElse(gi) { 0f } * k2
                var gy = 0f
                var sy = 1f
                var ga = 1f
                if (template.perGlyph) {
                    template.glyphPose(p, gi, since, glyphPose)
                    gx += glyphPose.dx; gy = glyphPose.dy; sy = glyphPose.scaleY; ga = glyphPose.alpha
                    if (ga <= 0f) continue
                }
                val gOld = target.transform
                target.translate(gx.toDouble(), gy.toDouble())
                if (sy != 1f) { target.translate(0.0, cy); target.scale(1.0, sy.toDouble()); target.translate(0.0, -cy) }
                if (ga < 1f) target.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (a * ga).coerceIn(0f, 1f))
                TextLayout(gtext, font, FRC).draw(target, 0f, p.ascent)
                target.transform = gOld
            }
            target.composite = AlphaComposite.SrcOver
            target.clip = oldClip
            target.transform = old
        }

        val upcoming = pos < w.startMs
        if (fill >= 1f) {
            drawPieces(gs, Color(0, 0, 0, 115), alpha, null)
            if (hl) drawPieces(gg, colour, alpha * 0.9f, null)
            drawPieces(gt, colour, alpha, null)
        } else {
            drawPieces(gt, Color.WHITE, alpha * 0.32f, null)
            if (!upcoming && fill > 0f) {
                drawPieces(gs, Color(0, 0, 0, 115), alpha, p.width * fill)
                if (hl) drawPieces(gg, colour, alpha * 0.9f, p.width * fill)
                drawPieces(gt, colour, alpha, p.width * fill)
            }
        }
    }

    // romanised subtitle
    val sub = line.sub
    val area = layout.subtitleArea
    if (sub != null && area != null) {
        // same size rule as PreparedStage: 34 % of the strip, capped at 60 px, up to two lines
        val f = fSemi.deriveFont(min(area.height * 0.34f, REF_PX * 0.6f))
        val str = java.text.AttributedString(sub).apply { addAttribute(java.awt.font.TextAttribute.FONT, f) }
        val lbm = java.awt.font.LineBreakMeasurer(str.iterator, FRC)
        var ty = area.top
        gt.color = Color(255, 255, 255, 185)
        var lines = 0
        while (lbm.position < sub.length && lines < 2) {
            val tl = lbm.nextLayout(area.width)
            ty += tl.ascent
            tl.draw(gt, area.centreX - tl.advance / 2, ty)
            ty += tl.descent + tl.leading
            lines++
        }
    }
    gs.dispose(); gg.dispose(); gt.dispose()

    val g = img.createGraphics()
    g.hq()
    val fp = layout.words.firstOrNull()?.fontPx ?: 80f
    val shadowBlur = blur(shadow, (fp * 0.08f).toInt().coerceAtLeast(2))
    // like LyricStage, dimming fades the lyrics layer only, not the background
    g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, dim)
    g.drawImage(shadowBlur, 0, (fp * 0.035f).toInt(), null)
    val glowBlur = blur(glow, (fp * 0.14f).toInt().coerceAtLeast(3))
    g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.75f * dim)
    g.drawImage(glowBlur, 0, 0, null)
    g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, dim)
    g.drawImage(type, 0, 0, null)
    g.composite = AlphaComposite.SrcOver
    g.dispose()
    println("${s.name}: ${s.template} '${line.text}' word ${k + 1}/${line.words.size}, fits=${layout.fits}")
    return img
}

// colours of the screens around the stage, as in ui/theme/Ui.kt
val UI_GROUND = Color(0x0B, 0x0B, 0x0D)
val UI_CARD = Color(0x17, 0x18, 0x1C)
val UI_RAISED = Color(0x26, 0x27, 0x2D)
val UI_LINE = Color(0x3A, 0x3B, 0x42)
val UI_TEXT = Color(0xF3, 0xF1, 0xEC)
val UI_MUTED = Color(0xA7, 0xA6, 0xAE)
val UI_ON_ACCENT = Color(0x16, 0x0C, 0x07)

/** The UI accent, as Ui.accent(): the album's first accent, lifted when too dark for dark text. */
fun uiAccent(pal: Pal): Color {
    val c = pal.accents[0]
    val l = Colour.luminance(c).toFloat()
    if (l >= 0.35f) return hex(c)
    val t = ((0.35f - l) / 0.65f + 0.15f).coerceIn(0f, 0.7f)
    return Color(
        (Colour.red(c) + (255 - Colour.red(c)) * t).toInt(),
        (Colour.green(c) + (255 - Colour.green(c)) * t).toInt(),
        (Colour.blue(c) + (255 - Colour.blue(c)) * t).toInt(),
    )
}

private fun Graphics2D.pill(x: Float, y: Float, w: Float, h: Float, fill: Color?, stroke: Color? = null, dp: Float = 2.625f) {
    val r = RoundRectangle2D.Float(x, y, w, h, h, h)
    if (fill != null) { color = fill; fill(r) }
    if (stroke != null) { color = stroke; this.stroke = BasicStroke(1f * dp); draw(r) }
}

private fun Graphics2D.centred(text: String, cx: Float, baseline: Float) {
    drawString(text, cx - fontMetrics.stringWidth(text) / 2f, baseline)
}

private fun Graphics2D.moreDots(cx: Float, cy: Float, dp: Float, c: Color) {
    color = c
    for (i in -1..1) fill(Ellipse2D.Float(cx - 2.2f * dp, cy + i * 7 * dp - 2.2f * dp, 4.4f * dp, 4.4f * dp))
}

/** Artwork stand-in: the album's accents, as a soft square. */
private fun Graphics2D.art(x: Float, y: Float, size: Float, radius: Float, pal: Pal) {
    paint = GradientPaint(x, y, hex(pal.accents[0]), x + size, y + size, hex(pal.accents[1]))
    fill(RoundRectangle2D.Float(x, y, size, size, radius, radius))
}

/** Controls overlay, drawn to the same proportions as ControlsOverlay.kt (2.625 px per dp). */
fun renderControls(base: BufferedImage, pal: Pal): BufferedImage {
    val dp = 2.625f
    val accent = uiAccent(pal)
    val img = BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB)
    val g = img.createGraphics()
    g.hq()
    g.drawImage(base, 0, 0, null)
    g.paint = GradientPaint(0f, 0f, Color(0, 0, 0, 140), 0f, H * 0.3f, Color(0, 0, 0, 0))
    g.fillRect(0, 0, W, (H * 0.3f).toInt())
    g.paint = GradientPaint(0f, H * 0.7f, Color(0, 0, 0, 0), 0f, H.toFloat(), Color(0, 0, 0, 115))
    g.fillRect(0, (H * 0.7f).toInt(), W, H - (H * 0.7f).toInt())

    // top: artwork, title, artist, settings
    val pad = 16 * dp
    var y = 44 * dp
    g.art(pad, y, 56 * dp, 28 * dp, pal)
    g.color = UI_TEXT
    g.font = fSemi.deriveFont(17 * dp)
    g.drawString("Never Gonna Give You Up", pad + 68 * dp, y + 24 * dp)
    g.color = Color(0xF3, 0xF1, 0xEC, 184)
    g.font = fRegular.deriveFont(14 * dp)
    g.drawString("Rick Astley", pad + 68 * dp, y + 44 * dp)
    g.color = Color(0, 0, 0, 90)
    g.fill(Ellipse2D.Float(W - pad - 48 * dp, y + 4 * dp, 48 * dp, 48 * dp))
    g.moreDots(W - pad - 24 * dp, y + 28 * dp, dp, UI_TEXT)
    y += 56 * dp + 14 * dp

    // layout pills
    var x = pad
    g.font = fSemi.deriveFont(14 * dp)
    for ((i, label) in listOf("Auto", "Stack", "Slide", "Zoom", "Letters", "Scatter", "Graph").withIndex()) {
        val tw = g.fontMetrics.stringWidth(label) + 32 * dp
        if (i == 0) g.pill(x, y, tw, 44 * dp, UI_TEXT) else g.pill(x, y, tw, 44 * dp, Color(0, 0, 0, 90))
        g.color = if (i == 0) UI_GROUND else UI_TEXT
        g.drawString(label, x + 16 * dp, y + 27.5f * dp)
        x += tw + 8 * dp
    }

    // bottom card: seek bar, times, transport
    val cardX = 12 * dp
    val cardW = W - cardX * 2
    val cardH = 186 * dp
    val cardY = H - 36 * dp - 20 * dp - cardH
    g.color = Color(0x17, 0x18, 0x1C, 220)
    g.fill(RoundRectangle2D.Float(cardX, cardY, cardW, cardH, 64 * dp, 64 * dp))
    val inX = cardX + 20 * dp
    val trackW = cardW - 40 * dp
    var cy = cardY + 34 * dp
    val prog = 0.094f
    g.color = Color(0xF3, 0xF1, 0xEC, 64)
    g.fill(RoundRectangle2D.Float(inX, cy - 2 * dp, trackW, 4 * dp, 4 * dp, 4 * dp))
    g.color = accent
    g.fill(RoundRectangle2D.Float(inX, cy - 2 * dp, trackW * prog, 4 * dp, 4 * dp, 4 * dp))
    g.color = UI_TEXT
    g.fill(Ellipse2D.Float(inX + trackW * prog - 10 * dp, cy - 10 * dp, 20 * dp, 20 * dp))
    cy += 30 * dp
    g.color = Color(0xF3, 0xF1, 0xEC, 184)
    g.font = fRegular.deriveFont(13 * dp)
    g.drawString("0:20", inX, cy)
    val tot = "3:33"
    g.drawString(tot, inX + trackW - g.fontMetrics.stringWidth(tot), cy)
    cy += 52 * dp

    val mid = W / 2f
    g.color = accent
    g.fill(Ellipse2D.Float(mid - 38 * dp, cy - 38 * dp, 76 * dp, 76 * dp))
    g.color = UI_ON_ACCENT
    val bar = 7f * dp
    g.fill(RoundRectangle2D.Float(mid - 10 * dp, cy - 11 * dp, bar, 22 * dp, 3f, 3f))
    g.fill(RoundRectangle2D.Float(mid + 3 * dp, cy - 11 * dp, bar, 22 * dp, 3f, 3f))
    fun tri(x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float) {
        val path = java.awt.geom.Path2D.Float()
        path.moveTo(x0, y0); path.lineTo(x1, y1); path.lineTo(x2, y2); path.closePath()
        g.fill(path)
    }
    g.color = UI_TEXT
    val px = mid - 38 * dp - 28 * dp - 28 * dp
    g.fillRect((px - 9 * dp).toInt(), (cy - 9 * dp).toInt(), (3f * dp).toInt(), (18f * dp).toInt())
    tri(px + 9 * dp, cy - 9 * dp, px + 9 * dp, cy + 9 * dp, px - 5 * dp, cy)
    val nx = mid + 38 * dp + 28 * dp + 28 * dp
    g.fillRect((nx + 6 * dp).toInt(), (cy - 9 * dp).toInt(), (3f * dp).toInt(), (18f * dp).toInt())
    tri(nx - 9 * dp, cy - 9 * dp, nx - 9 * dp, cy + 9 * dp, nx + 5 * dp, cy)
    g.dispose()
    return img
}

/** Settings, drawn to the proportions of SettingsScreen.kt: the song card, Look and switches. */
fun renderSettings(pal: Pal): BufferedImage {
    val dp = 2.625f
    val accent = uiAccent(pal)
    val img = BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB)
    val g = img.createGraphics()
    g.hq()
    g.color = UI_GROUND
    g.fillRect(0, 0, W, H)
    val pad = 16 * dp
    val inner = W - pad * 2
    var y = 44 * dp

    // header
    g.color = UI_CARD
    g.fill(Ellipse2D.Float(pad, y, 48 * dp, 48 * dp))
    g.color = UI_TEXT
    g.stroke = BasicStroke(2.2f * dp, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    g.draw(Line2D.Float(pad + 27 * dp, y + 17 * dp, pad + 20 * dp, y + 24 * dp))
    g.draw(Line2D.Float(pad + 20 * dp, y + 24 * dp, pad + 27 * dp, y + 31 * dp))
    g.font = fBlack.getValue(Script.LATIN).deriveFont(30 * dp)
    g.drawString("Settings", pad + 64 * dp, y + 35 * dp)
    y += 48 * dp + 16 * dp

    // this song
    val cardH = 270 * dp
    g.color = UI_CARD
    g.fill(RoundRectangle2D.Float(pad, y, inner, cardH, 48 * dp, 48 * dp))
    val cx = pad + 16 * dp
    var cy = y + 16 * dp
    g.art(cx, cy, 64 * dp, 32 * dp, pal)
    g.color = UI_TEXT
    g.font = fSemi.deriveFont(18 * dp)
    g.drawString("Never Gonna Give You Up", cx + 78 * dp, cy + 20 * dp)
    g.color = UI_MUTED
    g.font = fRegular.deriveFont(14 * dp)
    g.drawString("Rick Astley", cx + 78 * dp, cy + 40 * dp)
    g.font = fSemi.deriveFont(12 * dp)
    var tx = cx + 78 * dp
    for (tag in listOf("Musixmatch", "Word timed")) {
        val tw = g.fontMetrics.stringWidth(tag) + 20 * dp
        g.pill(tx, cy + 48 * dp, tw, 22 * dp, UI_RAISED)
        g.color = UI_TEXT
        g.drawString(tag, tx + 10 * dp, cy + 63.5f * dp)
        tx += tw + 6 * dp
    }
    cy += 64 * dp + 18 * dp + 16 * dp
    g.font = fSemi.deriveFont(15 * dp)
    g.color = UI_TEXT
    g.drawString("Sync", cx, cy)
    g.color = accent
    val off = "+0.00 s"
    g.drawString(off, cx + inner - 32 * dp - g.fontMetrics.stringWidth(off), cy)
    cy += 12 * dp
    val bw = (inner - 32 * dp - 16 * dp) / 3f
    for ((i, label) in listOf("Earlier", "Reset", "Later").withIndex()) {
        val bx = cx + i * (bw + 8 * dp)
        if (i == 1) g.pill(bx, cy, bw, 48 * dp, null, UI_LINE) else g.pill(bx, cy, bw, 48 * dp, UI_RAISED)
        g.color = if (i == 1) UI_MUTED else UI_TEXT
        g.centred(label, bx + bw / 2f, cy + 29.5f * dp)
    }
    cy += 48 * dp + 10 * dp
    g.pill(cx, cy, inner - 32 * dp, 48 * dp, accent)
    g.color = UI_ON_ACCENT
    g.centred("Import .lrc file", cx + (inner - 32 * dp) / 2f, cy + 29.5f * dp)
    y += cardH

    fun header(text: String) {
        y += 24 * dp + 13 * dp
        g.color = UI_MUTED
        g.font = fSemi.deriveFont(13 * dp)
        g.drawString(text.uppercase(), pad + 4 * dp, y)
        y += 10 * dp
    }

    // look
    header("Look")
    val lookH = 212 * dp
    g.color = UI_CARD
    g.fill(RoundRectangle2D.Float(pad, y, inner, lookH, 48 * dp, 48 * dp))
    cy = y + 16 * dp + 15 * dp
    g.color = UI_TEXT
    g.font = fSemi.deriveFont(15 * dp)
    g.drawString("Layout", cx, cy)
    cy += 10 * dp
    var px = cx
    g.font = fSemi.deriveFont(14 * dp)
    for ((i, label) in listOf("Auto", "Stack", "Slide", "Zoom").withIndex()) {
        val tw = g.fontMetrics.stringWidth(label) + 32 * dp
        if (i == 0) g.pill(px, cy, tw, 44 * dp, accent) else g.pill(px, cy, tw, 44 * dp, null, UI_LINE)
        g.color = if (i == 0) UI_ON_ACCENT else UI_TEXT
        g.drawString(label, px + 16 * dp, cy + 27.5f * dp)
        px += tw + 8 * dp
    }
    cy += 44 * dp + 20 * dp + 15 * dp
    g.color = UI_TEXT
    g.font = fSemi.deriveFont(15 * dp)
    g.drawString("Background", cx, cy)
    cy += 10 * dp
    val segW = inner - 32 * dp
    g.pill(cx, cy, segW, 52 * dp, UI_GROUND)
    val sw = (segW - 8 * dp - 8 * dp) / 3f
    g.font = fSemi.deriveFont(14 * dp)
    for ((i, label) in listOf("Liquid", "Artwork", "Solid").withIndex()) {
        val sx = cx + 4 * dp + i * (sw + 4 * dp)
        if (i == 0) g.pill(sx, cy + 4 * dp, sw, 44 * dp, UI_TEXT)
        g.color = if (i == 0) UI_GROUND else UI_MUTED
        g.centred(label, sx + sw / 2f, cy + 31.5f * dp)
    }
    y += lookH

    // motion and battery
    header("Motion and battery")
    val rows = listOf(
        Triple("Battery saver", "Black background, simple fades", false),
        Triple("Audio sync", "Listens through the microphone", true),
    )
    val rowsH = 72 * dp * rows.size
    g.color = UI_CARD
    g.fill(RoundRectangle2D.Float(pad, y, inner, rowsH, 48 * dp, 48 * dp))
    for ((i, r) in rows.withIndex()) {
        val ry = y + i * 72 * dp
        if (i > 0) { g.color = UI_RAISED; g.fillRect(cx.toInt(), ry.toInt(), (inner - 32 * dp).toInt(), (1 * dp).toInt()) }
        g.color = UI_TEXT
        g.font = fSemi.deriveFont(16 * dp)
        g.drawString(r.first, cx, ry + 32 * dp)
        g.color = UI_MUTED
        g.font = fRegular.deriveFont(13 * dp)
        g.drawString(r.second, cx, ry + 52 * dp)
        val sx = pad + inner - 16 * dp - 52 * dp
        val sy = ry + 20 * dp
        g.pill(sx, sy, 52 * dp, 32 * dp, if (r.third) accent else UI_LINE)
        g.color = if (r.third) UI_ON_ACCENT else UI_TEXT
        g.fill(Ellipse2D.Float(sx + (if (r.third) 24 else 4) * dp, sy + 4 * dp, 24 * dp, 24 * dp))
    }
    g.dispose()
    return img
}

/** Wraps a screen in a simple phone body. */
fun phone(screen: BufferedImage): BufferedImage {
    val bez = 30
    val out = BufferedImage(W + bez * 2, H + bez * 2, BufferedImage.TYPE_INT_ARGB)
    val g = out.createGraphics()
    g.hq()
    g.color = Color(0x16, 0x16, 0x1c)
    g.fill(RoundRectangle2D.Float(0f, 0f, out.width.toFloat(), out.height.toFloat(), 150f, 150f))
    g.color = Color(0x2a, 0x2a, 0x33)
    g.stroke = BasicStroke(4f)
    g.draw(RoundRectangle2D.Float(2f, 2f, out.width - 4f, out.height - 4f, 148f, 148f))
    g.clip = RoundRectangle2D.Float(bez.toFloat(), bez.toFloat(), W.toFloat(), H.toFloat(), 110f, 110f)
    g.drawImage(screen, bez, bez, null)
    g.clip = null
    // punch-hole camera
    g.color = Color.BLACK
    g.fill(Ellipse2D.Float(out.width / 2f - 18, bez + 30f, 36f, 36f))
    g.dispose()
    return out
}

fun scale(img: BufferedImage, w: Int): BufferedImage {
    var cur = img
    while (cur.width / 2 >= w) {
        val n = BufferedImage(cur.width / 2, cur.height / 2, BufferedImage.TYPE_INT_ARGB)
        val g = n.createGraphics(); g.hq(); g.drawImage(cur, 0, 0, n.width, n.height, null); g.dispose()
        cur = n
    }
    val h = (cur.height * w.toFloat() / cur.width).toInt()
    val n = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    val g = n.createGraphics(); g.hq(); g.drawImage(cur, 0, 0, w, h, null); g.dispose()
    return n
}

fun save(img: BufferedImage, name: String) {
    ImageIO.write(img, "png", File(OUT, name))
}

fun main() {
    val sunset = Pal(0xFF120A14.toInt(), listOf(0xFFFF375F.toInt(), 0xFFFF9F0A.toInt(), 0xFFBF5AF2.toInt()))
    val ocean = Pal(0xFF06101C.toInt(), listOf(0xFF0A84FF.toInt(), 0xFF64D2FF.toInt(), 0xFF30D158.toInt()))
    val dusk = Pal(0xFF0E0A1C.toInt(), listOf(0xFF5E5CE6.toInt(), 0xFFFF375F.toInt(), 0xFF64D2FF.toInt()))
    val ember = Pal(0xFF140C06.toInt(), listOf(0xFFFF9F0A.toInt(), 0xFFFFD60A.toInt(), 0xFFFF375F.toInt()))
    val forest = Pal(0xFF06140C.toInt(), listOf(0xFF30D158.toInt(), 0xFFFFD60A.toInt(), 0xFF64D2FF.toInt()))
    val rose = Pal(0xFF160812.toInt(), listOf(0xFFBF5AF2.toInt(), 0xFFFF375F.toInt(), 0xFFFF9F0A.toInt()))

    val shots = listOf(
        Shot("stack", "rickroll", "Never gonna", TemplateId.STACK, sunset, 0.85f, 11, 4),
        Shot("zoom", "rickroll", "Never gonna let", TemplateId.ZOOM, dusk, 0.85f, 3, 9, fill = -0.04f),
        Shot("graph", "tumhiho", "हम तेरे", TemplateId.GRAPH, ember, 0.99f, 21, 2),
        Shot("flow", "lemon", "夢ならば", TemplateId.FLOW, ocean, 0.99f, 5, 6),
        Shot("letters", "eklacholo", "যদি", TemplateId.LETTERS, rose, 0.99f, 8, 3),
        Shot("scatter", "loseyourself", "Look,", TemplateId.SCATTER, forest, 0.99f, 13, 5),
    )
    val phones = HashMap<String, BufferedImage>()
    for (s in shots) {
        val screen = renderStage(s)
        val ph = phone(screen)
        phones[s.name] = ph
        save(scale(ph, 360), "stage-${s.name}.png")
    }
    val ctrlBase = renderStage(Shot("controls", "rickroll", "Never gonna", TemplateId.STACK, sunset, 0.85f, 11, 4), dim = 0.3f)
    val controls = phone(renderControls(ctrlBase, sunset))
    phones["controls"] = controls
    save(scale(controls, 360), "controls.png")
    val settings = phone(renderSettings(sunset))
    phones["settings"] = settings
    save(scale(settings, 360), "settings.png")

    // hero: four phones on a dark band
    val hero = listOf("stack", "graph", "flow", "zoom").map { scale(phones.getValue(it), 300) }
    val gap = 36
    val hw = hero.sumOf { it.width } + gap * (hero.size + 1)
    val hh = hero[0].height + gap * 2
    val band = BufferedImage(hw, hh, BufferedImage.TYPE_INT_ARGB)
    val g = band.createGraphics()
    g.hq()
    g.paint = GradientPaint(0f, 0f, Color(0x0b, 0x0b, 0x12), hw.toFloat(), hh.toFloat(), Color(0x1a, 0x10, 0x24))
    g.fill(RoundRectangle2D.Float(0f, 0f, hw.toFloat(), hh.toFloat(), 48f, 48f))
    var x = gap
    for ((i, p) in hero.withIndex()) {
        val dy = if (i % 2 == 0) gap else gap
        g.drawImage(p, x, dy, null)
        x += p.width + gap
    }
    g.dispose()
    save(band, "hero.png")
}
