package com.lyricstage.core.palette

/**
 * Colours for one track: the background is the artwork's top colour used exactly as it
 * comes, the first two accents are the next top colours pushed apart in lightness until
 * they stand off the background, and the third is a glint beyond the lyrics' shade.
 * The lyrics colour is a white- or black-tinted shade of the artwork's most saturated
 * colour; the renderer's shadow keeps it readable, so it never has to out-contrast the
 * background on its own.
 */
data class StagePalette(
    val base: Int,
    val accents: List<Int>,
    val text: Int,
) {
    companion object {
        const val WHITE = 0xFFFFFFFF.toInt()

        /** Vivid system-like colours for tracks with no or grey artwork. */
        private val FALLBACK = intArrayOf(
            0xFFFF375F.toInt(), 0xFFFF9F0A.toInt(), 0xFFFFD60A.toInt(), 0xFF30D158.toInt(),
            0xFF64D2FF.toInt(), 0xFF0A84FF.toInt(), 0xFF5E5CE6.toInt(), 0xFFBF5AF2.toInt(),
        )

        fun fallback(seed: Int): StagePalette {
            val n = FALLBACK.size
            val s = Math.floorMod(seed, n)
            return StagePalette(
                base = 0xFF0B0B12.toInt(),
                accents = listOf(FALLBACK[s], FALLBACK[(s + 3) % n], FALLBACK[(s + 5) % n]),
                text = WHITE,
            )
        }
    }
}

/** A colour sampled from artwork and how much of the image it covers. */
data class Swatch(val rgb: Int, val population: Int)

/**
 * Builds a [StagePalette] from artwork swatches. Pure maths; the app feeds AndroidX Palette output.
 *
 * One rule for every cover, near-black included: the top colour by coverage becomes the
 * background as-is, the next two become the ripples, pushed in lightness until they clear
 * the background. Nothing is averaged into a new colour and no branch treats black
 * differently, so the same artwork always yields exactly the same palette.
 *
 * A cover is dark or light by the light the eye sees — WCAG luminance — never by HSL
 * lightness, which puts every pure hue at exactly 50 %: a saturated blue cover is "light"
 * to HSL and night to the eye, and black lyrics on it would vanish. Red and blue fall
 * below the line and wear the white-added shade; green and yellow fall above it and wear
 * the black-added one.
 */
object PaletteBuilder {
    /** Minimum lightness gap between the background and a ripple colour. */
    const val RIPPLE_GAP = 0.26f

    /** Minimum lightness gap between the two ripple colours, so the waves read as depth. */
    const val RIPPLE_SPREAD = 0.08f

    /** Lightness of the second ripple is pushed to this fraction of [RIPPLE_GAP] before separation. */
    private const val RIPPLE_GAP2 = 0.7f

    /** Lyrics on dark backgrounds: a white-added shade between these lightnesses. */
    const val TEXT_DARK_L = 0.88f

    /** Lyrics on light backgrounds: a black-added shade. */
    const val TEXT_LIGHT_L = 0.18f

    /**
     * WCAG relative luminance at which a cover starts counting as light — the same line
     * the app's accent chips use to decide they are light enough for dark text. HSL
     * lightness cannot be used: it calls a saturated blue cover "light" (its maths puts
     * every pure hue at 50 %) and that cover is night to the eye.
     */
    const val LIGHT_LUMINANCE = 0.35

    /** The lyric shade is floored at this contrast against its background - the
     * large-type bar. The white-added shade is measured against the artwork itself (solid
     * and artwork backgrounds draw it raw), the black-added shade against the background
     * as the liquid glass shader shows it, whose filmic tail darkens a light cover into
     * the mid tones where a light black would sink. */
    const val TEXT_MIN_CONTRAST = 3.0

    /** Highest saturation the lyrics shade may carry: a tint, not a pure colour. */
    const val TEXT_SAT_MAX = 0.32f

    /** Ripple lightness is kept inside these bounds: never blinding, never void. */
    const val L_MIN = 0.04f
    const val L_MAX = 0.96f

    fun build(swatches: List<Swatch>, seed: Int): StagePalette {
        if (swatches.isEmpty()) return StagePalette.fallback(seed)
        // coverage first, ties by saturation, then lightness, then hue: a fixed order with
        // no seed and no randomness, so re-deriving can never change the colours
        val ranked = swatches.sortedWith(
            compareByDescending<Swatch> { it.population }
                .thenByDescending { Colour.toHsl(it.rgb)[1] }
                .thenByDescending { Colour.toHsl(it.rgb)[2] }
                .thenBy { Colour.toHsl(it.rgb)[0] },
        )
        val top = ranked[0]
        val (bh, bs, bl) = Colour.toHsl(top.rgb)
        // dark by the light the eye sees, not by HSL lightness: pure hues all sit at HSL
        // 50 %, and a saturated blue or red cover is night to the eye while green and
        // yellow are light. The line is the one the app's accent chips already use.
        val dark = Colour.luminance(top.rgb) < LIGHT_LUMINANCE
        val dir = if (dark) 1f else -1f

        fun ripple(src: Swatch?, gap: Float): FloatArray {
            // a missing colour is derived from the background's own hue and saturation:
            // never a made-up colour, and black covers keep their greys
            val (h, s, l) = src?.let { Colour.toHsl(it.rgb) } ?: floatArrayOf(bh, bs, bl)
            var nl = l
            if (kotlin.math.abs(nl - bl) < gap) nl = bl + dir * gap
            return floatArrayOf(h, (s * 1.08f + 0.08f).coerceAtMost(1f), nl.coerceIn(L_MIN, L_MAX))
        }

        val r1 = ripple(ranked.getOrNull(1), RIPPLE_GAP)
        val r2 = ripple(ranked.getOrNull(2), RIPPLE_GAP * RIPPLE_GAP2)
        // keep the two ripples apart in lightness; push the second one further in the same direction
        if (kotlin.math.abs(r2[2] - r1[2]) < RIPPLE_SPREAD) {
            r2[2] = (r1[2] + dir * RIPPLE_SPREAD).coerceIn(L_MIN, L_MAX)
        }

        // lyrics: hue from the most saturated colour in the artwork, saturation carried at
        // half and capped; lightness on the far side of the background, whites added on
        // dark covers, blacks added on light ones. Each shade is then floored where it is
        // weakest: the white-added one against the raw artwork (a red cover), the
        // black-added one against the background as the shader shows it (a green cover
        // is drawn in the mid tones, and a light black would sink into it)
        val accentSrc = ranked.maxWithOrNull(compareBy { Colour.toHsl(it.rgb)[1] }) ?: top
        val (ah, srcSat, _) = Colour.toHsl(accentSrc.rgb)
        val ts = (srcSat * 0.5f).coerceAtMost(TEXT_SAT_MAX)
        val tl = if (dark) TEXT_DARK_L else TEXT_LIGHT_L
        var text = Colour.fromHsl(ah, ts, tl)
        text = if (dark) {
            Colour.lightenForContrast(text, top.rgb, TEXT_MIN_CONTRAST)
        } else {
            Colour.deepenForContrast(text, Colour.rolled(top.rgb), TEXT_MIN_CONTRAST)
        }
        // the third accent, for highlighted words and the brightest pool: the lyrics' own
        // shade one lightness step beyond, near white on dark covers, near black on light ones
        val glint = Colour.fromHsl(ah, ts, if (dark) L_MAX else L_MIN)

        return StagePalette(
            base = top.rgb,
            accents = listOf(Colour.fromHsl(r1[0], r1[1], r1[2]), Colour.fromHsl(r2[0], r2[1], r2[2]), glint),
            text = text,
        )
    }
}
