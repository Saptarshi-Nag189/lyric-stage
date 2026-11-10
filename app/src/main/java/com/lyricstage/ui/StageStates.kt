package com.lyricstage.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lyricstage.ui.theme.Ui

/**
 * Small status pill near the bottom of the stage: an optional icon or colour dot, the text,
 * and an optional action button in the album's accent.
 */
@Composable
fun StagePill(
    text: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {},
    glyph: Glyph? = null,
    dot: Color? = null,
    accent: Color = Ui.Default,
    /** When set, a border in [accent] runs down around the pill over this time, then [onTimeout]. */
    timerMs: Long = 0L,
    onTimeout: () -> Unit = {},
) {
    val left = remember { Animatable(1f) }
    if (timerMs > 0) {
        LaunchedEffect(timerMs) {
            left.snapTo(1f)
            left.animateTo(0f, tween(timerMs.toInt(), easing = LinearEasing))
            onTimeout()
        }
    }
    // a small capsule of the same glass as the player, a little frosted so its text reads
    val stage = LocalStageGlass.current
    GlassPanel(
        frost = ((stage?.frost ?: 0f) + 0.3f).coerceAtMost(1f),
        corner = 22.dp,
        stageSize = stage?.size ?: IntSize.Zero,
        field = stage?.field,
        fallback = Ui.Scrim,
        edge = 7.dp,
        bend = 10.dp,
        dim = 0.12f,
        modifier = modifier
            .windowInsetsPadding(WindowInsets.mandatorySystemGestures)
            .padding(bottom = 40.dp),
    ) {
    Row(
        Modifier
            .heightIn(min = 44.dp)
            .then(
                if (timerMs > 0) Modifier.drawWithContent {
                    drawContent()
                    val w = 2.dp.toPx()
                    val r = size.height / 2f - w / 2f
                    val outline = Path().apply { addRoundRect(RoundRect(w / 2f, w / 2f, size.width - w / 2f, size.height - w / 2f, CornerRadius(r))) }
                    val measure = PathMeasure().apply { setPath(outline, false) }
                    val part = Path()
                    measure.getSegment(0f, measure.length * left.value, part, true)
                    drawPath(part, accent, style = Stroke(w, cap = StrokeCap.Round))
                } else Modifier,
            )
            .padding(start = 18.dp, end = if (action != null) 6.dp else 18.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (glyph != null) {
            DrawnIcon(glyph, null, Ui.Text, 18)
            Spacer(Modifier.width(10.dp))
        } else if (dot != null) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, color = Ui.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        if (action != null) {
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .heightIn(min = 36.dp)
                    .clip(CircleShape)
                    .background(accent)
                    .clickable(role = Role.Button, onClick = onAction)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(action, color = Ui.OnAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
    }
}
