package com.lyricstage.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Line icons drawn on a 24-unit grid, so no icon font or library is needed. */
enum class Glyph { BACK, CHEVRON, MORE, OFFLINE, NOTE, MIC_OFF, SEARCHING, QUEUE, SHUFFLE, REPEAT, REPEAT_ONE, CLOSE }

@Composable
fun DrawnIcon(glyph: Glyph, description: String?, tint: Color, sizeDp: Int = 18) {
    val m = if (description != null) Modifier.semanticsDescription(description) else Modifier
    Canvas(m.size(sizeDp.dp)) {
        val u = size.width / 24f
        val stroke = Stroke(width = 2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(x: Float, y: Float) = Offset(x * u, y * u)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(tint, p(x1, y1), p(x2, y2), 2f * u, StrokeCap.Round)
        fun arc(cx: Float, cy: Float, r: Float, start: Float, sweep: Float) =
            drawArc(tint, start, sweep, false, Offset((cx - r) * u, (cy - r) * u), Size(2 * r * u, 2 * r * u), style = stroke)
        when (glyph) {
            Glyph.BACK -> { line(15f, 6f, 9f, 12f); line(9f, 12f, 15f, 18f) }
            Glyph.CHEVRON -> { line(9f, 6f, 15f, 12f); line(15f, 12f, 9f, 18f) }
            Glyph.MORE -> for (y in listOf(5f, 12f, 19f)) drawCircle(tint, 2f * u, p(12f, y))
            Glyph.OFFLINE -> {
                arc(12f, 20f, 15f, 225f, 90f)
                arc(12f, 20f, 10f, 225f, 90f)
                arc(12f, 20f, 5f, 225f, 90f)
                line(3f, 3f, 21f, 21f)
            }
            Glyph.NOTE -> {
                line(9f, 18f, 9f, 5f); line(9f, 5f, 21f, 3f); line(21f, 3f, 21f, 16f)
                drawCircle(tint, 3f * u, p(6f, 18f), style = stroke)
                drawCircle(tint, 3f * u, p(18f, 16f), style = stroke)
            }
            Glyph.MIC_OFF -> {
                drawRoundRect(tint, p(9f, 2f), Size(6f * u, 13f * u), CornerRadius(3f * u), style = stroke)
                arc(12f, 10f, 7f, 0f, 180f)
                line(12f, 17f, 12f, 21f)
                line(3f, 3f, 21f, 21f)
            }
            Glyph.SEARCHING -> for ((i, x) in listOf(6f, 12f, 18f).withIndex()) {
                drawCircle(tint.copy(alpha = 1f - i * 0.3f), 2.4f * u, p(x, 12f))
            }
            Glyph.QUEUE -> {
                line(4f, 6f, 20f, 6f)
                line(4f, 12f, 20f, 12f)
                line(4f, 18f, 14f, 18f)
            }
            Glyph.CLOSE -> {
                line(6f, 6f, 18f, 18f)
                line(18f, 6f, 6f, 18f)
            }
            Glyph.SHUFFLE -> {
                line(3f, 7f, 8f, 7f)
                line(8f, 7f, 15f, 16f)
                line(16f, 16f, 21f, 16f)
                line(3f, 16f, 8f, 16f)
                line(8f, 16f, 13.5f, 9.7f)
                line(16.5f, 7f, 21f, 7f)
                // arrowheads
                line(18f, 4.4f, 21f, 7f); line(18f, 9.6f, 21f, 7f)
                line(18f, 13.4f, 21f, 16f); line(18f, 18.6f, 21f, 16f)
            }
            Glyph.REPEAT -> {
                arc(12f, 12f, 7.5f, 200f, 260f)
                line(12f, 4.5f, 12f, 8f)
                line(9.6f, 5.6f, 12f, 3.2f); line(14.4f, 5.6f, 12f, 3.2f)
                line(12f, 16f, 12f, 19.5f)
                line(9.6f, 18.4f, 12f, 20.8f); line(14.4f, 18.4f, 12f, 20.8f)
            }
            Glyph.REPEAT_ONE -> {
                arc(12f, 12f, 7.5f, 200f, 260f)
                line(12f, 4.5f, 12f, 8f)
                line(9.6f, 5.6f, 12f, 3.2f); line(14.4f, 5.6f, 12f, 3.2f)
                line(12f, 16f, 12f, 19.5f)
                line(9.6f, 18.4f, 12f, 20.8f); line(14.4f, 18.4f, 12f, 20.8f)
                // the "1"
                line(11f, 9.5f, 12f, 8.8f); line(12f, 8.8f, 12f, 14.5f)
            }
        }
    }
}
