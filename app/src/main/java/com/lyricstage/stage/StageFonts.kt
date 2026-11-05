package com.lyricstage.stage

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.em
import com.lyricstage.R
import com.lyricstage.core.text.Script

/** Bundled variable fonts, one family per script. Weight is set through the wght axis. */
object StageFonts {
    private fun family(res: Int) = FontFamily(
        Font(res, FontWeight.Black),
        Font(res, FontWeight.SemiBold),
        Font(res, FontWeight.Medium),
    )

    /** Bricolage Grotesque at a given weight and optical size (12 for small text, 96 for display). */
    @OptIn(ExperimentalTextApi::class)
    private fun bricolage(weight: Int, opsz: Float, as_: FontWeight = FontWeight(weight)) = Font(
        R.font.bricolage,
        as_,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.Setting("opsz", opsz)),
    )

    /** Latin lyrics: Bricolage at its heaviest, display cut. */
    private val stageLatin = FontFamily(
        bricolage(800, 96f, FontWeight.Black),
        bricolage(600, 48f, FontWeight.SemiBold),
        bricolage(500, 36f, FontWeight.Medium),
    )

    /** The app's own screens: text-size cuts for body copy, display cuts for headings. */
    val ui = FontFamily(
        bricolage(400, 14f),
        bricolage(500, 14f),
        bricolage(600, 18f),
        bricolage(700, 24f),
        bricolage(800, 36f),
        bricolage(800, 72f, FontWeight.Black),
    )

    private val devanagari = family(R.font.noto_sans_devanagari)
    private val bengali = family(R.font.noto_sans_bengali)
    private val japanese = family(R.font.noto_sans_jp)

    fun forScript(script: Script): FontFamily = when (script) {
        Script.DEVANAGARI -> devanagari
        Script.BENGALI -> bengali
        Script.JAPANESE -> japanese
        Script.KOREAN -> FontFamily.SansSerif
        else -> stageLatin
    }

    /** Stage word style at an exact pixel size. */
    fun word(script: Script, fontPx: Float, density: Density): TextStyle = TextStyle(
        fontFamily = forScript(script),
        fontWeight = FontWeight.Black,
        fontSize = with(density) { fontPx.toSp() },
        // a touch of tightening suits heavy Latin type; other scripts keep shaping untouched
        letterSpacing = if (script == Script.LATIN || script == Script.OTHER) (-0.01).em else 0.em,
    )

    fun subtitle(script: Script, fontPx: Float, density: Density): TextStyle = TextStyle(
        fontFamily = forScript(script),
        fontWeight = FontWeight.SemiBold,
        fontSize = with(density) { fontPx.toSp() },
    )
}
