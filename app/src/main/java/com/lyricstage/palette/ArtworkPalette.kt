package com.lyricstage.palette

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.palette.graphics.Palette
import com.lyricstage.core.palette.PaletteBuilder
import com.lyricstage.core.palette.StagePalette
import com.lyricstage.core.palette.Swatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

/**
 * Draws [art] — any config, hardware included — into a fresh [width]×[height] ARGB_8888
 * bitmap. Hardware bitmaps cannot be read pixel by pixel, but they can be drawn into a
 * software canvas, so this avoids the old full-resolution `copy(ARGB_8888)`: no
 * multi-megabyte allocation, no long stall, and the same result for software bitmaps.
 */
fun downscale(art: Bitmap, width: Int, height: Int): Bitmap {
    val small = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    Canvas(small).drawBitmap(art, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), SCALE_PAINT)
    return small
}

private val SCALE_PAINT = Paint(Paint.FILTER_BITMAP_FLAG)

/** Base, vivid accents and a contrast-safe text colour from album artwork. */
object ArtworkPalette {
    /** Palette samples this many pixels (≈112×112, aspect kept); enough for dominant colours. */
    private const val SAMPLE_AREA = 112f * 112f

    suspend fun from(art: Bitmap?, seed: Int): StagePalette = withContext(Dispatchers.Default) {
        if (art == null || art.isRecycled || art.width <= 0 || art.height <= 0) return@withContext StagePalette.fallback(seed)
        val scale = sqrt(SAMPLE_AREA / (art.width.toFloat() * art.height)).coerceAtMost(1f)
        val small = downscale(art, (art.width * scale).toInt().coerceAtLeast(1), (art.height * scale).toInt().coerceAtLeast(1))
        val palette = Palette.from(small).maximumColorCount(16).generate()
        PaletteBuilder.build(palette.swatches.map { Swatch(it.rgb, it.population) }, seed)
    }
}
