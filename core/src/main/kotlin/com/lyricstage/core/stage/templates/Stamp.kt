package com.lyricstage.core.stage.templates

import com.lyricstage.core.stage.Align
import com.lyricstage.core.stage.Arrangement
import com.lyricstage.core.stage.ENTRY_LEAD_MS
import com.lyricstage.core.stage.LyricTemplate
import com.lyricstage.core.stage.MeasuredLine
import com.lyricstage.core.stage.Pose
import com.lyricstage.core.stage.SizeF
import com.lyricstage.core.stage.Spring
import com.lyricstage.core.stage.TemplateId
import com.lyricstage.core.stage.WordPlacement
import com.lyricstage.core.stage.arrangeRows
import com.lyricstage.core.stage.defaultRows
import kotlin.math.abs

/**
 * Each word lands like a rubber stamp: big and tilted, then snaps down into a tidy centred
 * line with a little overshoot. Emphasised words are stamped slightly larger.
 */
object Stamp : LyricTemplate {
    override val id = TemplateId.STAMP
    private val spring = Spring(0.42f, 520f)

    override fun rowOptions(line: MeasuredLine) = defaultRows(line.words.size, 5)

    override fun arrange(line: MeasuredLine, fontPx: Float, rows: Int, seed: Int): Arrangement {
        val scales = FloatArray(line.words.size) { i ->
            val w = line.line.words[i]
            when {
                w.emphasis -> 1.18f
                Filler.isFiller(w.text) -> 0.82f
                else -> 1f
            }
        }
        return arrangeRows(line, fontPx, rows, Align.CENTRE, scales, leading = 1.04f)
    }

    override fun pose(p: WordPlacement, sinceEnterMs: Long, stage: SizeF, out: Pose) {
        out.reset()
        // the stamp hits as the word is sung, not when it starts entering
        val since = sinceEnterMs - ENTRY_LEAD_MS + LAND_LEAD_MS
        if (since < 0) {
            out.alpha = 0f
            return
        }
        val v = spring.value(since)
        if (v >= 1f) return
        val u = 1f - v
        out.scaleX = 1f + u * 0.9f
        out.scaleY = 1f + u * 0.9f
        // alternate the tilt word to word, a little different each time
        val sign = if (p.index % 2 == 0) -1f else 1f
        out.rotationDeg = u * sign * (7f + (p.index * 3 % 5))
        out.alpha = (since / 50f).coerceIn(0f, 1f)
        out.blurPx = abs(u) * 10f
    }

    /** The stamp starts falling this long before the word's start time. */
    private const val LAND_LEAD_MS = 70L
}
