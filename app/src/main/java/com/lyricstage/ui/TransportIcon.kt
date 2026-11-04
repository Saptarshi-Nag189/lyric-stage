package com.lyricstage.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lyricstage.R

enum class Transport { PLAY, PAUSE, PREVIOUS, NEXT }

/** Simple drawn transport icons, so no icon font or library is needed. */
@Composable
fun TransportIcon(kind: Transport, description: String, tint: Color = Color.White) {
    Canvas(Modifier.size(24.dp).semanticsDescription(description)) {
        val w = size.width
        val h = size.height
        when (kind) {
            Transport.PLAY -> triangle(Offset(w * 0.25f, h * 0.15f), Offset(w * 0.25f, h * 0.85f), Offset(w * 0.85f, h * 0.5f), tint)
            Transport.PAUSE -> {
                val bar = Size(w * 0.22f, h * 0.7f)
                drawRoundRect(tint, Offset(w * 0.22f, h * 0.15f), bar, CornerRadius(2f))
                drawRoundRect(tint, Offset(w * 0.56f, h * 0.15f), bar, CornerRadius(2f))
            }
            Transport.PREVIOUS -> {
                drawRect(tint, Offset(w * 0.18f, h * 0.2f), Size(w * 0.1f, h * 0.6f))
                triangle(Offset(w * 0.82f, h * 0.2f), Offset(w * 0.82f, h * 0.8f), Offset(w * 0.3f, h * 0.5f), tint)
            }
            Transport.NEXT -> {
                drawRect(tint, Offset(w * 0.72f, h * 0.2f), Size(w * 0.1f, h * 0.6f))
                triangle(Offset(w * 0.18f, h * 0.2f), Offset(w * 0.18f, h * 0.8f), Offset(w * 0.7f, h * 0.5f), tint)
            }
        }
    }
}

private fun DrawScope.triangle(a: Offset, b: Offset, c: Offset, colour: Color) {
    val path = Path().apply {
        moveTo(a.x, a.y)
        lineTo(b.x, b.y)
        lineTo(c.x, c.y)
        close()
    }
    drawPath(path, colour)
}

/** Three vertical dots that open Settings. Sits in the top right, clear of a camera cutout. */
@Composable
fun MoreButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .windowInsetsPadding(WindowInsets.displayCutout)
            .padding(8.dp),
    ) {
        val description = stringResource(R.string.settings)
        Canvas(Modifier.size(24.dp).semanticsDescription(description)) {
            val r = size.width * 0.09f
            for (i in 0..2) drawCircle(Color.White, r, Offset(size.width / 2f, size.height * (0.22f + i * 0.28f)))
        }
    }
}
