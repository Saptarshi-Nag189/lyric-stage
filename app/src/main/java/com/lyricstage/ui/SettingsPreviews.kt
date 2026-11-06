package com.lyricstage.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.lyricstage.background.BackgroundClock
import com.lyricstage.background.RippleEmitter
import com.lyricstage.background.StageBackground
import com.lyricstage.core.palette.StagePalette
import com.lyricstage.core.stage.Ripple
import com.lyricstage.settings.BackgroundMode
import com.lyricstage.stage.StageFonts
import com.lyricstage.stage.drawGlassText
import com.lyricstage.ui.theme.Ui
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * A small live stage: the background with its own ripples, and a sung word drawn with the
 * current glass and frost, so the sliders show their effect straight away.
 */
@Composable
fun BackgroundPreview(
    mode: BackgroundMode,
    palette: StagePalette,
    art: Bitmap?,
    pools: Float,
    rippleStrength: Float,
    glass: Float,
    frost: Float,
    reduceMotion: Boolean,
) {
    val clock = remember { BackgroundClock() }
    val ripples = remember { RippleEmitter() }
    LaunchedEffect(Unit) {
        val rnd = Random(7)
        var n = 0
        while (true) {
            ripples.emit(Ripple(0.15f + rnd.nextFloat() * 0.7f, 0.2f + rnd.nextFloat() * 0.6f, 0.7f + rnd.nextFloat() * 0.3f, n % 4 == 0), clock.seconds())
            n++
            delay(900L + rnd.nextLong(700L))
        }
    }
    val measurer = rememberTextMeasurer()
    val family = StageFonts.bricolage
    val text = Color(palette.text)
    val hl = lerp(text, Color(palette.accents.first()), 0.85f)

    Box(Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(24.dp))) {
        StageBackground(
            mode, palette, art, ripples, clock, reduceMotion,
            animate = true, black = false, modifier = Modifier.fillMaxSize(),
            pools = pools, rippleStrength = rippleStrength,
        )
        Canvas(Modifier.fillMaxSize()) {
            fun style(sp: Float) = TextStyle(fontFamily = family, fontWeight = FontWeight.Black, fontSize = sp.sp, letterSpacing = (-0.01).em)
            var sp = 46f
            var sung = measurer.measure("Never gonna", style(sp))
            var word = measurer.measure("give", style(sp))
            val total = sung.size.width + sp.sp.toPx() * 0.28f + word.size.width
            if (total > size.width * 0.88f) {
                sp *= size.width * 0.88f / total
                sung = measurer.measure("Never gonna", style(sp))
                word = measurer.measure("give", style(sp))
            }
            val px = sp.sp.toPx()
            val gap = px * 0.28f
            val shadow = Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, 2f), 12f)
            val y = (size.height - sung.size.height) / 2f
            val x = (size.width - (sung.size.width + gap + word.size.width)) / 2f
            drawGlassText(sung, text, Offset(x, y), 1f, shadow, glass, frost, px)
            drawGlassText(word, hl, Offset(x + sung.size.width + gap, y), 1f, shadow, glass, frost, px)
        }
    }
}
