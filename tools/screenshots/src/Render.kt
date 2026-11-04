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
            else -> Color.WHITE
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
    g.drawImage(shadowBlur, 0, (fp * 0.035f).toInt(), null)
    val glowBlur = blur(glow, (fp * 0.14f).toInt().coerceAtLeast(3))
    g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.75f)
    g.drawImage(glowBlur, 0, 0, null)
    g.composite = AlphaComposite.SrcOver
    g.drawImage(type, 0, 0, null)
    if (dim < 1f) {
        g.color = Color(0, 0, 0, ((1 - dim) * 255).toInt())
        g.fillRect(0, 0, W, H)
    }
    g.dispose()
    println("${s.name}: ${s.template} '${line.text}' word ${k + 1}/${line.words.size}, fits=${layout.fits}")
    return img
}

/** Controls overlay, drawn to the same proportions as ControlsOverlay.kt (2.625 px per dp). */
fun renderControls(base: BufferedImage, pal: Pal): BufferedImage {
    val dp = 2.625f
    val img = BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB)
    val g = img.createGraphics()
    g.hq()
    g.drawImage(base, 0, 0, null)
    g.paint = GradientPaint(0f, H * 0.45f, Color(0, 0, 0, 0), 0f, H.toFloat(), Color(0, 0, 0, 217))
    g.fillRect(0, 0, W, H)
    val pad = 20 * dp
    var y = H - 48 * dp - 300 * dp

    // artwork thumb, title, artist, settings
    val thumb = 56 * dp
    g.paint = GradientPaint(pad, y, hex(pal.accents[0]), pad + thumb, y + thumb, hex(pal.accents[1]))
    g.fill(RoundRectangle2D.Float(pad, y, thumb, thumb, 16 * dp, 16 * dp))
    g.color = Color.WHITE
    g.font = fSemi.deriveFont(18 * dp)
    g.drawString("Never Gonna Give You Up", pad + thumb + 14 * dp, y + 24 * dp)
    g.color = Color(255, 255, 255, 178)
    g.font = fRegular.deriveFont(14 * dp)
    g.drawString("Rick Astley", pad + thumb + 14 * dp, y + 46 * dp)
    g.color = Color.WHITE
    g.font = fSemi.deriveFont(14 * dp)
    val st = "Settings"
    g.drawString(st, W - pad - g.fontMetrics.stringWidth(st) - 8 * dp, y + 34 * dp)
    y += thumb + 26 * dp

    // seek bar
    val trackW = W - pad * 2
    val prog = 0.094f
    g.color = Color(255, 255, 255, 60)
    g.fill(RoundRectangle2D.Float(pad, y - 2 * dp, trackW, 4 * dp, 4 * dp, 4 * dp))
    g.color = Color.WHITE
    g.fill(RoundRectangle2D.Float(pad, y - 2 * dp, trackW * prog, 4 * dp, 4 * dp, 4 * dp))
    g.fill(Ellipse2D.Float(pad + trackW * prog - 10 * dp, y - 10 * dp, 20 * dp, 20 * dp))
    y += 26 * dp
    g.color = Color(255, 255, 255, 178)
    g.font = fRegular.deriveFont(12 * dp)
    g.drawString("0:20", pad, y)
    val tot = "3:33"
    g.drawString(tot, W - pad - g.fontMetrics.stringWidth(tot), y)
    y += 40 * dp

    // transport: previous, play/pause, next
    val mid = W / 2f
    g.color = hex(0xFFFF375F.toInt())
    g.fill(Ellipse2D.Float(mid - 30 * dp, y - 30 * dp, 60 * dp, 60 * dp))
    g.color = Color.WHITE
    val bar = 5.3f * dp
    g.fill(RoundRectangle2D.Float(mid - 8 * dp, y - 8.4f * dp, bar, 16.8f * dp, 2f, 2f))
    g.fill(RoundRectangle2D.Float(mid + 2.7f * dp, y - 8.4f * dp, bar, 16.8f * dp, 2f, 2f))
    fun tri(x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float) {
        val path = java.awt.geom.Path2D.Float()
        path.moveTo(x0, y0); path.lineTo(x1, y1); path.lineTo(x2, y2); path.closePath()
        g.fill(path)
    }
    val px = mid - 74 * dp
    g.fillRect((px - 7 * dp).toInt(), (y - 7 * dp).toInt(), (2.4f * dp).toInt(), (14.4f * dp).toInt())
    tri(px + 7 * dp, y - 7 * dp, px + 7 * dp, y + 7 * dp, px - 5 * dp, y)
    val nx = mid + 74 * dp
    g.fillRect((nx + 5 * dp).toInt(), (y - 7 * dp).toInt(), (2.4f * dp).toInt(), (14.4f * dp).toInt())
    tri(nx - 7 * dp, y - 7 * dp, nx - 7 * dp, y + 7 * dp, nx + 5 * dp, y)
    y += 52 * dp

    // template chips
    var x = pad
    g.font = fSemi.deriveFont(14 * dp)
    for ((i, label) in listOf("Auto", "Stack", "Slide", "Zoom", "Letters", "Scatter").withIndex()) {
        val tw = g.fontMetrics.stringWidth(label) + 32 * dp
        val chip = RoundRectangle2D.Float(x, y - 16 * dp, tw, 32 * dp, 16 * dp, 16 * dp)
        if (i == 0) {
            g.color = Color.WHITE; g.fill(chip); g.color = Color.BLACK
        } else {
            g.color = Color(255, 255, 255, 120); g.stroke = BasicStroke(1.2f * dp); g.draw(chip); g.color = Color.WHITE
        }
        g.drawString(label, x + 16 * dp, y + 5 * dp)
        x += tw + 8 * dp
    }
    y += 44 * dp

    // sync nudge
    g.color = Color.WHITE
    g.font = fSemi.deriveFont(14 * dp)
    g.drawString("−250 ms", pad + 8 * dp, y)
    g.font = fRegular.deriveFont(13 * dp)
    g.drawString("Sync +250 ms", pad + 92 * dp, y)
    g.font = fSemi.deriveFont(14 * dp)
    g.drawString("+250 ms", pad + 196 * dp, y)
    val imp = "Import .lrc"
    g.drawString(imp, W - pad - g.fontMetrics.stringWidth(imp) - 8 * dp, y)
    y += 22 * dp
    g.color = Color(255, 255, 255, 128)
    g.font = fRegular.deriveFont(11 * dp)
    g.drawString("Lyrics late? Tap +. Early? Tap −.", pad, y)
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
        Shot("graph", "hindi", "तेरी यादों", TemplateId.GRAPH, ember, 0.7f, 21, 2),
        Shot("flow", "japanese", "夜空に", TemplateId.FLOW, ocean, 0.55f, 5, 6),
        Shot("letters", "bengali", "তোমার গানে", TemplateId.LETTERS, rose, 0.6f, 8, 3),
        Shot("scatter", "rap", "Count the bars", TemplateId.SCATTER, forest, 0.62f, 13, 5),
    )
    val phones = HashMap<String, BufferedImage>()
    for (s in shots) {
        val screen = renderStage(s)
        val ph = phone(screen)
        phones[s.name] = ph
        save(scale(ph, 360), "stage-${s.name}.png")
    }
    val ctrlBase = renderStage(Shot("controls", "rickroll", "Never gonna", TemplateId.STACK, sunset, 0.85f, 11, 4))
    val controls = phone(renderControls(ctrlBase, sunset))
    phones["controls"] = controls
    save(scale(controls, 360), "controls.png")

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
