package com.lyricstage.ui

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.lyricstage.R
import com.lyricstage.background.BackgroundClock
import com.lyricstage.background.GlassTextEffect
import com.lyricstage.background.LiquidGlassField
import com.lyricstage.background.StageGlassText
import com.lyricstage.background.RippleEmitter
import com.lyricstage.background.StageBackground
import com.lyricstage.core.palette.StagePalette
import com.lyricstage.core.stage.Ripple
import com.lyricstage.settings.BackgroundMode
import com.lyricstage.stage.LyricFont
import com.lyricstage.stage.StageFonts
import com.lyricstage.stage.drawGlassText
import com.lyricstage.ui.theme.Ui
import kotlinx.coroutines.delay
import kotlin.random.Random

/** A lyric line in the chosen lyric font, sung word highlighted, on the current palette. */
@Composable
fun FontPreview(lyricFont: LyricFont, palette: StagePalette) {
    val lyrics = remember(lyricFont) { StageFonts.lyrics(lyricFont) }
    val text = Color(palette.text)
    val hl = Color(palette.accents.first())
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(palette.base)).padding(18.dp),
    ) {
        Text(
            buildAnnotatedString {
                append("Never gonna ")
                withStyle(SpanStyle(color = hl)) { append("give") }
                withStyle(SpanStyle(color = text.copy(alpha = 0.32f))) { append(" you up") }
            },
            color = text,
            style = TextStyle(fontFamily = lyrics, fontWeight = FontWeight.Black, fontSize = 34.sp, lineHeight = 36.sp),
        )
        Text(
            stringResource(R.string.hint_font_sample),
            color = text.copy(alpha = 0.55f),
            style = TextStyle(fontFamily = lyrics, fontWeight = FontWeight.Medium, fontSize = 14.sp),
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

/**
 * A small live stage: the background with its own ripples, and a sung word drawn with the
 * current glass and frost, so the switch and the slider show their effect straight away.
 * On the liquid background the word is glass the same way the stage is: by the shader
 * that looks at the line from outside and bends the field behind it at the rim.
 * Elsewhere it is the drawn kind of glass.
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
    lyricFont: LyricFont,
    reduceMotion: Boolean,
) {
    val clock = remember { BackgroundClock() }
    val ripples = remember { RippleEmitter() }
    val stage = remember(mode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && mode == BackgroundMode.LIQUID) {
            val f = LiquidGlassField()
            f to (GlassTextEffect(f) as StageGlassText)
        } else {
            null
        }
    }
    LaunchedEffect(Unit) {
        val rnd = Random(7)
        var n = 0
        while (true) {
            ripples.emit(Ripple(0.15f + rnd.nextFloat() * 0.7f, 0.2f + rnd.nextFloat() * 0.6f, 0.7f + rnd.nextFloat() * 0.3f), clock.seconds())
            n++
            delay(900L + rnd.nextLong(700L))
        }
    }
    val measurer = rememberTextMeasurer()
    val family = remember(lyricFont) { StageFonts.lyrics(lyricFont) }
    val text = Color(palette.text)
    val hl = lerp(text, Color(palette.accents.first()), 0.85f)
    // read as state inside the draw block, so every change of the switch and the slider
    // redraws the preview for sure, including the trip back to solid (the glass off)
    val glassNow = rememberUpdatedState(glass)
    val frostNow = rememberUpdatedState(frost)

    Box(Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(24.dp))) {
        StageBackground(
            mode, palette, art, ripples, clock, reduceMotion,
            animate = true, black = false, modifier = Modifier.fillMaxSize(),
            pools = pools, rippleStrength = rippleStrength,
            field = stage?.first,
        )
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val fx = stage?.second
                    renderEffect = if (fx != null && fx.tick.longValue > 0L && glassNow.value > 0.01f) fx.effect else null
                },
        ) {
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
            val fx = stage?.second
            if (fx != null && fx.tick.longValue > 0L && glassNow.value > 0.01f) {
                // the shader does the glass: the words are drawn solid and looked at from outside
                fx.uniforms(glassNow.value, frostNow.value, (px * 0.04f).coerceIn(2.5f, 5f))
                drawText(sung, text, Offset(x, y), 1f)
                drawText(word, hl, Offset(x + sung.size.width + gap, y), 1f)
            } else {
                drawGlassText(sung, text, Offset(x, y), 1f, shadow, glassNow.value, frostNow.value, px)
                drawGlassText(word, hl, Offset(x + sung.size.width + gap, y), 1f, shadow, glassNow.value, frostNow.value, px)
            }
        }
    }
}
