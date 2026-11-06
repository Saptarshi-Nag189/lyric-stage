package com.lyricstage.core.palette

/**
 * Colours for one track: the background is the artwork's top colour as the stage shows
 * it (the glass shader's filmic tail darkens a light cover - yellow becomes olive, green
 * becomes dark green, light grey becomes grey), the first two accents are the next top
 * colours pushed apart in lightness until they stand off the background, and the third is
 * a glint beyond the lyrics' shade. The lyrics colour is always a light, white-added shade
 * of the artwork's most saturated colour - yellow on olive, light grey on grey, light
 * greens on dark green - floored at a contrast bar against the background as the stage
 * draws it; the renderer's shadow adds the rest of the readability.
 */
data class StagePalette(
    val base: Int,
    val accents: List<Int>,
    val text: Int,
) {
    /**
     * A colour for highlighted words from [accent]. The ripple rule pushes a light cover's
     * ripples to the background's dark side, and a word glowing in one would sink into the
     * mid tones the stage draws; such an accent is lifted to the lyric shade's lightness
     * with its saturation damped to a tint, so the glow keeps the art's hue and reads as
     * light type. Accents on the background's light side pass unchanged.
     */
    fun highlightColour(accent: Int): Int {
        val bl = Colour.toHsl(base)[2]
        if (Colour.toHsl(accent)[2] >= bl - PaletteBuilder.RIPPLE_GAP / 2f) return accent
        val (h, s, _) = Colour.toHsl(accent)
        return Colour.fromHsl(h, s * 0.6f, Colour.toHsl(text)[2])
    }

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
 * to HSL and night to the eye. That line now picks only which side the ripples fall on
 * (red and blue get lighter ones, green and yellow darker ones), because the lyrics are
 * always light: the stage sends every background through one filmic tone map (the glass
 * shader's tail; the solid and gradient modes apply the same curve), which turns a light
 * cover dark enough for light type - yellow shows as olive, green as dark green, light
 * grey as grey. Dark lyrics on light covers are gone; against the mid tones the stage
 * actually draws, they read as mud.
 *
 * A grey artwork stays grey: no saturation is invented for it, so a black-and-white
 * cover gives a black-and-white stage — background, ripples, lyrics and glint all on the
 * one grey axis.
 */
object PaletteBuilder {
    /** Minimum lightness gap between the background and a ripple colour. */
    const val RIPPLE_GAP = 0.26f

    /** Minimum lightness gap between the two ripple colours, so the waves read as depth. */
    const val RIPPLE_SPREAD = 0.08f

    /** Lightness of the second ripple is pushed to this fraction of [RIPPLE_GAP] before separation. */
    private const val RIPPLE_GAP2 = 0.7f

    /** Lyrics are always a light, white-added shade at this lightness. */
    const val TEXT_L = 0.88f

    /**
     * WCAG relative luminance at which a cover starts counting as light — the same line
     * the app's accent chips use to decide they are light enough for dark text. HSL
     * lightness cannot be used: it calls a saturated blue cover "light" (its maths puts
     * every pure hue at 50 %) and that cover is night to the eye.
     */
    const val LIGHT_LUMINANCE = 0.35

    /** The lyric shade is floored at this contrast against the background as the stage
     *  draws it — every mode sends the background through the same filmic tail
     *  ([Colour.rolled]) — so one measurement covers the glass, the solid colour and the
     *  gradient fallback alike. */
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
            // colours are pushed a little beyond what the artwork had so the waves read,
            // but a grey keeps its own (zero) saturation: a grey's hue is red by
            // convention in HSL, so even a small flat floor would pink a black-and-white
            // cover. The floor ramps in only past near-grey, so every coloured cover
            // keeps exactly the saturation it had before
            val sat = (s * 1.08f + 0.08f * (s / 0.06f).coerceAtMost(1f)).coerceAtMost(1f)
            return floatArrayOf(h, sat, nl.coerceIn(L_MIN, L_MAX))
        }

        val r1 = ripple(ranked.getOrNull(1), RIPPLE_GAP)
        val r2 = ripple(ranked.getOrNull(2), RIPPLE_GAP * RIPPLE_GAP2)
        // keep the two ripples apart in lightness; push the second one further in the same direction
        if (kotlin.math.abs(r2[2] - r1[2]) < RIPPLE_SPREAD) {
            r2[2] = (r1[2] + dir * RIPPLE_SPREAD).coerceIn(L_MIN, L_MAX)
        }

        // lyrics: hue from the most saturated colour in the artwork, saturation carried at
        // half and capped, always the light shade - the stage's filmic tail darkens even a
        // yellow or light grey cover into the mid tones, so light type reads on every
        // cover and dark type never appears. The shade is floored against the background
        // as the stage draws it, tone-mapped, which is the bar it must actually clear.
        val accentSrc = ranked.maxWithOrNull(compareBy { Colour.toHsl(it.rgb)[1] }) ?: top
        val (ah, srcSat, _) = Colour.toHsl(accentSrc.rgb)
        val ts = (srcSat * 0.5f).coerceAtMost(TEXT_SAT_MAX)
        var text = Colour.fromHsl(ah, ts, TEXT_L)
        text = Colour.lightenForContrast(text, Colour.rolled(top.rgb), TEXT_MIN_CONTRAST)
        // the third accent, for highlighted words and the brightest pool: the lyrics' own
        // shade one lightness step beyond, near white
        val glint = Colour.fromHsl(ah, ts, L_MAX)

        return StagePalette(
            base = top.rgb,
            accents = listOf(Colour.fromHsl(r1[0], r1[1], r1[2]), Colour.fromHsl(r2[0], r2[1], r2[2]), glint),
            text = text,
        )
    }
}
