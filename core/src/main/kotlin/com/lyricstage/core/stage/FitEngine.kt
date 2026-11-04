package com.lyricstage.core.stage

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Guarantees no word is ever clipped. For each line and template:
 *  1. shrink the font (binary search for the largest size that fits),
 *  2. wrap onto the row count that gives the biggest type, never below a comfortable size
 *     while some row count still allows it,
 *  3. rows are always width-balanced (see [Rows.balance]),
 *  4. last resort: scale the tightest arrangement down until it fits.
 */
object FitEngine {
    const val MARGIN_X = 0.06f
    const val MARGIN_TOP = 0.10f
    const val MARGIN_BOTTOM = 0.10f
    /** Share of the stage height kept for the romanised subtitle. */
    const val SUBTITLE_SHARE = 0.11f

    /** Score multiplier per step down the template's row preference list. */
    private const val ROW_PENALTY = 0.93f

    fun safeArea(stage: SizeF, hasSubtitle: Boolean): RectF {
        val bottomMargin = MARGIN_BOTTOM + if (hasSubtitle) SUBTITLE_SHARE else 0f
        return RectF(
            stage.width * MARGIN_X,
            stage.height * MARGIN_TOP,
            stage.width * (1 - MARGIN_X),
            stage.height * (1 - bottomMargin),
        )
    }

    fun maxFont(stage: SizeF): Float = min(stage.height * 0.20f, stage.width * 0.16f)

    /** Smallest size we shrink to before wrapping again; legible at arm's length. */
    fun minFont(stage: SizeF): Float = max(stage.minDimension * 0.05f, 12f)

    fun fit(template: LyricTemplate, line: MeasuredLine, stage: SizeF, seed: Int, fontScale: Float = 1f): TemplateLayout {
        val hasSub = !line.line.sub.isNullOrBlank()
        val area = safeArea(stage, hasSub)
        val subArea = if (hasSub) RectF(area.left, area.bottom + stage.height * 0.01f, area.right, area.bottom + stage.height * SUBTITLE_SHARE) else null
        if (line.words.isEmpty()) {
            return TemplateLayout(template.id, line.line, stage, Arrangement(emptyList()), area, true, seed, subArea)
        }
        val minF = minFont(stage)
        // the user's font scale caps the size; it never pushes text past the safe area
        val maxF = max(minF, maxFont(stage) * fontScale.coerceIn(0.5f, 1.5f))
        val comfortable = max(minF, maxF * 0.42f)
        val options = template.rowOptions(line).ifEmpty { listOf(1) }

        // Try every row count and keep the one that gives the biggest type. Each extra row
        // costs a little, so a line only wraps further when that clearly pays off.
        for (floor in floatArrayOf(comfortable, minF)) {
            var bestRows = -1
            var bestFont = 0f
            var bestScore = 0f
            for ((rank, rows) in options.withIndex()) {
                val f = largestFit(template, line, rows, area, seed, floor, maxF) ?: continue
                val score = f * ROW_PENALTY.pow(rank)
                if (score > bestScore) {
                    bestScore = score
                    bestFont = f
                    bestRows = rows
                }
            }
            if (bestRows > 0) {
                val arr = template.arrange(line, bestFont, bestRows, seed).placed(1f, area.centreX, area.centreY)
                return TemplateLayout(template.id, line.line, stage, arr, area, verify(arr, area), seed, subArea)
            }
        }

        // last resort: the tightest arrangement, scaled down until it fits
        val rows = options.maxOrNull() ?: 1
        val raw = template.arrange(line, minF, rows, seed)
        val bb = raw.bounds()
        val s = min(area.width / bb.width.coerceAtLeast(1f), area.height / bb.height.coerceAtLeast(1f)) * 0.995f
        val arr = raw.placed(min(1f, s), area.centreX, area.centreY)
        return TemplateLayout(template.id, line.line, stage, arr, area, verify(arr, area), seed, subArea)
    }

    private fun fitsAt(template: LyricTemplate, line: MeasuredLine, rows: Int, area: RectF, seed: Int, f: Float): Boolean {
        val bb = template.arrange(line, f, rows, seed).bounds()
        return bb.width <= area.width && bb.height <= area.height
    }

    private fun largestFit(template: LyricTemplate, line: MeasuredLine, rows: Int, area: RectF, seed: Int, floor: Float, ceil: Float): Float? {
        if (!fitsAt(template, line, rows, area, seed, floor)) return null
        if (fitsAt(template, line, rows, area, seed, ceil)) return ceil
        var lo = floor
        var hi = ceil
        repeat(16) {
            val mid = (lo + hi) / 2
            if (fitsAt(template, line, rows, area, seed, mid)) lo = mid else hi = mid
        }
        // A hair smaller so the real (hinted) text at this size still fits. Row balancing can
        // break ties differently at another size, so keep the checked size if that one fails.
        val margin = lo * 0.985f
        return if (fitsAt(template, line, rows, area, seed, margin)) margin else lo
    }

    /** Every word's rotated box inside the safe area. */
    fun verify(arr: Arrangement, area: RectF): Boolean = arr.words.all { area.contains(it.bounds()) }
}
