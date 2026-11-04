package com.lyricstage.core.stage.templates

import com.lyricstage.core.stage.Align
import com.lyricstage.core.stage.Arrangement
import com.lyricstage.core.stage.LyricTemplate
import com.lyricstage.core.stage.MeasuredLine
import com.lyricstage.core.stage.Pose
import com.lyricstage.core.stage.SizeF
import com.lyricstage.core.stage.TemplateId
import com.lyricstage.core.stage.WordPlacement
import com.lyricstage.core.stage.arrangeRows
import com.lyricstage.core.stage.defaultRows
import com.lyricstage.core.stage.easeOut

/**
 * The whole line waits on the stage, dim; a soft spotlight glides from word to word in time
 * and the sung words light up. The renderer draws the light (see [isSpotlight]).
 */
object Spotlight : LyricTemplate {
    override val id = TemplateId.SPOTLIGHT
    override val showsUpcoming = true

    override fun rowOptions(line: MeasuredLine) = defaultRows(line.words.size, 5)

    override fun arrange(line: MeasuredLine, fontPx: Float, rows: Int, seed: Int): Arrangement =
        arrangeRows(line, fontPx, rows, Align.CENTRE, leading = 1.08f, gapEm = 1.15f)

    /** The line fades up as a whole, rising a touch; words then wait for the light. */
    override fun pose(p: WordPlacement, sinceEnterMs: Long, stage: SizeF, out: Pose) {
        out.reset()
        val t = easeOut(sinceEnterMs / ENTER_MS)
        if (t >= 1f) return
        out.alpha = t
        out.dy = (1f - t) * p.height * 0.18f
    }

    private const val ENTER_MS = 360f
}

/** True for layouts whose renderer draws a moving spotlight behind the sung word. */
val TemplateId.isSpotlight: Boolean get() = this == TemplateId.SPOTLIGHT
