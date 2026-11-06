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

/** Typefaces for Latin lyrics, picked in Settings. All bundled, SIL Open Font License. */
enum class LyricFont(val label: String) {
    UNBOUNDED("Unbounded"), BRICOLAGE("Bricolage"), BIG_SHOULDERS("Big Shoulders"), SYNE("Syne"), DARKER("Darker Grotesque");

    companion object {
        fun fromName(name: String?) = entries.firstOrNull { it.name == name } ?: BRICOLAGE
    }
}

/**
 * Typefaces. Bricolage runs every app screen and is the default for the lyrics, with four
 * other families pickable for the stage in Settings. One Anek family per Indic script is
 * applied automatically from the lyrics text; its even proportions across scripts keep
 * mixed-language songs coherent. Japanese and Korean use the system's own CJK typeface
 * (Noto Sans CJK on most phones): full quality, every glyph, no megabyte font shipped in
 * the APK. All bundled fonts are under the SIL Open Font License.
 */
object StageFonts {
    /** A variable family at real weights: the stage renders heavy, so Indic lyrics get
     *  genuinely bold cuts through the wght axis like Bricolage, not a default-weight instance. */
    private fun vfam(res: Int) = FontFamily(
        cut(res, FontWeight.Black, 800, 800),
        cut(res, FontWeight.SemiBold, 600, 800),
        cut(res, FontWeight.Medium, 500, 800),
    )

    /** One cut of a variable font: [weight] on the wght axis (clamped to what it has), optional opsz. */
    @OptIn(ExperimentalTextApi::class)
    private fun cut(res: Int, slot: FontWeight, weight: Int, maxWeight: Int, opsz: Float? = null): Font {
        val w = weight.coerceAtMost(maxWeight)
        val settings = if (opsz != null) FontVariation.Settings(FontVariation.weight(w), FontVariation.Setting("opsz", opsz))
        else FontVariation.Settings(FontVariation.weight(w))
        return Font(res, slot, variationSettings = settings)
    }

    /**
     * Bricolage at every weight the theme and the stage use: text optical sizes for the
     * small UI, display sizes for the words. Its wght axis runs 200-800, so the Black slots
     * clamp to 800 - the heaviest cut it has.
     */
    val bricolage = FontFamily(
        cut(R.font.bricolage, FontWeight.Normal, 400, 800, opsz = 16f),
        cut(R.font.bricolage, FontWeight.Medium, 500, 800, opsz = 16f),
        cut(R.font.bricolage, FontWeight.SemiBold, 600, 800, opsz = 32f),
        cut(R.font.bricolage, FontWeight.Bold, 700, 800, opsz = 56f),
        cut(R.font.bricolage, FontWeight.ExtraBold, 800, 800, opsz = 72f),
        cut(R.font.bricolage, FontWeight.Black, 900, 800, opsz = 96f),
    )

    private class Spec(val res: Int, val max: Int, val opsz: Boolean = false)

    private fun spec(f: LyricFont) = when (f) {
        LyricFont.UNBOUNDED -> Spec(R.font.unbounded, 900)
        LyricFont.BRICOLAGE -> Spec(R.font.bricolage, 800, opsz = true)
        LyricFont.BIG_SHOULDERS -> Spec(R.font.big_shoulders, 900)
        LyricFont.SYNE -> Spec(R.font.syne, 800)
        LyricFont.DARKER -> Spec(R.font.darker_grotesque, 900)
    }

    private val lyricFamilies = HashMap<LyricFont, FontFamily>()

    /** Lyrics in [f]: the heaviest cut for words, lighter ones for subtitles. */
    @Synchronized
    fun lyrics(f: LyricFont): FontFamily = lyricFamilies.getOrPut(f) {
        val s = spec(f)
        FontFamily(
            cut(s.res, FontWeight.Black, 900, s.max, if (s.opsz) 96f else null),
            cut(s.res, FontWeight.SemiBold, 600, s.max, if (s.opsz) 48f else null),
            cut(s.res, FontWeight.Medium, 500, s.max, if (s.opsz) 36f else null),
        )
    }

    /** The lyric font in use; set from Settings before the stage is laid out. */
    @Volatile var lyricFont: LyricFont = LyricFont.BRICOLAGE

    // Anek for the nine Indic scripts, one family per script, applied automatically from
    // the lyrics text
    private val devanagari = vfam(R.font.anek_devanagari)
    private val bangla = vfam(R.font.anek_bangla)
    private val gurmukhi = vfam(R.font.anek_gurmukhi)
    private val gujarati = vfam(R.font.anek_gujarati)
    private val odia = vfam(R.font.anek_odia)
    private val tamil = vfam(R.font.anek_tamil)
    private val telugu = vfam(R.font.anek_telugu)
    private val kannada = vfam(R.font.anek_kannada)
    private val malayalam = vfam(R.font.anek_malayalam)

    fun forScript(script: Script): FontFamily = when (script) {
        Script.DEVANAGARI -> devanagari
        Script.BENGALI -> bangla
        Script.GURMUKHI -> gurmukhi
        Script.GUJARATI -> gujarati
        Script.ODIA -> odia
        Script.TAMIL -> tamil
        Script.TELUGU -> telugu
        Script.KANNADA -> kannada
        Script.MALAYALAM -> malayalam
        Script.JAPANESE, Script.KOREAN -> FontFamily.SansSerif
        else -> lyrics(lyricFont)
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
