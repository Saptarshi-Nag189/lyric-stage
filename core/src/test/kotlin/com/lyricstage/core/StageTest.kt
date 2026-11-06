package com.lyricstage.core

import com.lyricstage.core.lyrics.LyricsBuilder
import com.lyricstage.core.palette.Colour
import com.lyricstage.core.palette.PaletteBuilder
import com.lyricstage.core.palette.Swatch
import com.lyricstage.core.stage.Highlights
import com.lyricstage.core.stage.MeasuredLine
import com.lyricstage.core.stage.Rows
import com.lyricstage.core.stage.SizeF
import com.lyricstage.core.stage.Spring
import com.lyricstage.core.stage.TemplateChooser
import com.lyricstage.core.stage.Templates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StageTest {
    @Test fun rowsBalanceMinimisesWidestRow() {
        val rows = Rows.balance(listOf(5f, 1f, 1f, 1f, 1f, 5f), 2, 0f)
        assertEquals(listOf(0..2, 3..5), rows)
        assertEquals(1, Rows.balance(listOf(3f), 4, 1f).size)
    }

    @Test fun springSettlesExactly() {
        val s = Spring.Bouncy
        assertEquals(0f, s.value(0), 0f)
        assertTrue("overshoots", (1..400).any { s.value(it.toLong()) > 1f })
        assertEquals(1f, s.value(s.settleMs), 0f)
        assertTrue(s.settleMs in 300..1500)
    }

    @Test fun chooserNeverRepeatsAndIsStable() {
        for (name in Samples.names) {
            val lines = LyricsBuilder.fromLrc(Samples.read(name), 120_000).lines
            for (seed in 0 until 50) {
                val picks = TemplateChooser.chooseAll(lines, seed, null, false)
                for (i in 1 until picks.size) assertTrue("$name seed $seed line $i", picks[i] != picks[i - 1])
                assertEquals(picks, TemplateChooser.chooseAll(lines, seed, null, false))
                val calm = TemplateChooser.chooseAll(lines, seed, null, true)
                assertTrue(calm.none { it.name == "SCATTER" })
            }
        }
    }

    @Test fun highlightsOneToThree() {
        val lines = LyricsBuilder.fromLrc(Samples.read("english"), 120_000).lines
        for (seed in 0 until 30) for (l in lines) {
            val h = Highlights.pick(l, seed)
            assertTrue(h.size in 1..3)
            assertEquals(h, Highlights.pick(l, seed))
        }
    }

    @Test fun highlightsPreferImpactWords() {
        val line = LyricsBuilder.fromLrc("[00:01.00]<00:01.00>I <00:01.20>will <00:01.40>always <00:01.60>love <00:02.80>you <00:03.00>", 10_000).lines.single()
        val picked = Highlights.pick(line, 1)
        assertEquals(setOf(3), picked) // "love": emotive and held
        for (seed in 0 until 20) assertEquals(picked, Highlights.pick(line, seed))
        val hindi = LyricsBuilder.fromLrc("[00:01.00]हम तेरे बिन अब रह नहीं सकते", 10_000).lines.single()
        assertTrue(Highlights.pick(hindi, 3).isNotEmpty())
    }

    @Test fun paletteTopColourIsTheBackground() {
        val p = PaletteBuilder.build(listOf(Swatch(0xFFE0C040.toInt(), 500), Swatch(0xFF3060E0.toInt(), 300), Swatch(0xFFF0F0F0.toInt(), 900)), 1)
        // the most-covered colour becomes the background, used exactly as it comes
        assertEquals(0xFFF0F0F0.toInt(), p.base)
        assertEquals(3, p.accents.size)
        // text is a shade of the artwork's most saturated colour, never the background
        assertTrue(p.text != p.base)
        val (th, ts, tl) = Colour.toHsl(p.text)
        assertTrue("text carries the cover's hue", Colour.hueDistance(th, Colour.toHsl(0xFFE0C040.toInt())[0]) < 0.08f || Colour.hueDistance(th, Colour.toHsl(0xFF3060E0.toInt())[0]) < 0.08f)
        assertTrue("text saturation is a tint, not a pure colour", ts <= PaletteBuilder.TEXT_SAT_MAX + 0.01f)
        // the lyrics are always the light shade, whatever the cover, and the glint goes
        // one step beyond: the tone map draws even a light cover in the mid tones
        assertTrue(tl >= PaletteBuilder.TEXT_L - 0.11f)
        val glint = Colour.toHsl(p.accents[2])
        assertTrue(glint[2] >= PaletteBuilder.L_MAX - 0.01f)
    }

    @Test fun paletteRipplesStandOffTheBackground() {
        val p = PaletteBuilder.build(listOf(Swatch(0xFFE0C040.toInt(), 500), Swatch(0xFF3060E0.toInt(), 300), Swatch(0xFFF0F0F0.toInt(), 900)), 1)
        val bl = Colour.toHsl(p.base)[2]
        p.accents.take(2).forEach {
            val l = Colour.toHsl(it)[2]
            assertTrue("ripple clears the background (Δl ${kotlin.math.abs(l - bl)})", kotlin.math.abs(l - bl) >= PaletteBuilder.RIPPLE_GAP - 0.02f)
            assertTrue("ripple lightness stays in bounds", l >= PaletteBuilder.L_MIN - 0.01f && l <= PaletteBuilder.L_MAX + 0.01f)
        }
        // the two ripples sit apart in lightness, so the waves read as depth
        assertTrue(kotlin.math.abs(Colour.toHsl(p.accents[0])[2] - Colour.toHsl(p.accents[1])[2]) >= PaletteBuilder.RIPPLE_SPREAD - 0.02f)
    }

    @Test fun paletteIsTheSameEveryTime() {
        // no randomness, no time, no callback order: the same swatches give the same
        // palette however often the artwork is re-delivered or the app is restarted
        val swatches = listOf(
            Swatch(0xFF050505.toInt(), 580), Swatch(0xFF6B1F2A.toInt(), 210), Swatch(0xFFE8E4E4.toInt(), 110),
            Swatch(0xFF29141A.toInt(), 100),
        )
        val first = PaletteBuilder.build(swatches, 7)
        repeat(50) { assertEquals(first, PaletteBuilder.build(swatches, 7)) }
        // a differently sized but equivalent artwork (same shares) is the same cover
        val rescaled = swatches.map { Swatch(it.rgb, it.population * 3) }
        assertEquals(first, PaletteBuilder.build(rescaled, 7))
        // shuffling the input cannot change anything
        assertEquals(first, PaletteBuilder.build(shuffled(swatches, kotlin.random.Random(3)), 7))
    }

    @Test fun paletteBlackDominantCoverStaysPut() {
        // the reported case: mostly black with one strong colour. The background is the
        // cover's own near-black, as-is; the colour returns as the ripples, never as a
        // different background on the next open
        val black = 0xFF0C0C0C.toInt()
        val crimson = 0xFFD23C4C.toInt()
        val swatches = listOf(Swatch(black, 580), Swatch(crimson, 210), Swatch(0xFFE8E4E4.toInt(), 110))
        val p = PaletteBuilder.build(swatches, 11)
        assertEquals(black, p.base)
        val bl = Colour.toHsl(black)[2]
        p.accents.take(2).forEach { assertTrue(kotlin.math.abs(Colour.toHsl(it)[2] - bl) >= PaletteBuilder.RIPPLE_GAP - 0.02f) }
        // the crimson's hue returns in the ripples or the text, never a made-up colour
        val ch = Colour.toHsl(crimson)[0]
        assertTrue(
            p.accents.take(2).any { Colour.hueDistance(Colour.toHsl(it)[0], ch) < 0.08f } ||
                Colour.hueDistance(Colour.toHsl(p.text)[0], ch) < 0.08f,
        )
        // dark cover: the lyrics are the light shade
        assertTrue(Colour.toHsl(p.text)[2] >= PaletteBuilder.TEXT_L - 0.11f)
        // one strong hue only: the second ripple is derived from the cover, not invented
        val r2h = Colour.toHsl(p.accents[1])[0]
        assertTrue(Colour.hueDistance(r2h, ch) < 0.1f || Colour.hueDistance(r2h, Colour.toHsl(black)[0]) < 0.1f)
    }

    @Test fun palettePrimariesWearTheShadeThatReads() {
        // pure red, green, blue and yellow covers all sit at HSL lightness 0.5, and every
        // shade rule before this one picked a side. The stage's filmic tone map decides
        // it instead: red and blue show dark, green shows dark green, yellow shows olive,
        // so the light shade reads on all four - and the lyrics are ALWAYS light, never
        // the black tint that read as mud against those mid tones
        fun primary(base: Int, darker: Int, lighter: Int) =
            PaletteBuilder.build(listOf(Swatch(base, 900), Swatch(darker, 250), Swatch(lighter, 150)), 3)
        val red = primary(0xFFFF0000.toInt(), 0xFFB00000.toInt(), 0xFFFF7070.toInt())
        val green = primary(0xFF00FF00.toInt(), 0xFF00B000.toInt(), 0xFF70FF70.toInt())
        val blue = primary(0xFF0000FF.toInt(), 0xFF0000B0.toInt(), 0xFF7070FF.toInt())
        val yellow = primary(0xFFFFFF00.toInt(), 0xFFB0B000.toInt(), 0xFFFFFF70.toInt())

        listOf("red" to red, "green" to green, "blue" to blue, "yellow" to yellow).forEach { (name, p) ->
            assertTrue("$name lyrics are the light shade", Colour.toHsl(p.text)[2] >= PaletteBuilder.TEXT_L - 0.11f)
            // and the shade clears the large-type bar against the background as the stage
            // draws it: every mode sends the background through the same filmic tail
            val shown = Colour.contrast(Colour.rolled(p.base), p.text)
            assertTrue("$name lyrics clear the bar on the stage as drawn ($shown)", shown >= PaletteBuilder.TEXT_MIN_CONTRAST - 0.01)
            assertTrue("$name glint is light", Colour.toHsl(p.accents[2])[2] >= PaletteBuilder.L_MAX - 0.01f)
        }
        // ripples still pick their side by the light the eye sees, so they stand off the
        // background on every hue (the second ripple is held to its own smaller gap)
        val gaps = listOf(PaletteBuilder.RIPPLE_GAP, PaletteBuilder.RIPPLE_GAP * 0.7f)
        listOf(red, green, blue, yellow).forEach { p ->
            val bl = Colour.toHsl(p.base)[2]
            p.accents.take(2).forEachIndexed { i, acc ->
                assertTrue("ripple clears the background", kotlin.math.abs(Colour.toHsl(acc)[2] - bl) >= gaps[i] - 0.02f)
            }
        }
    }

    @Test fun highlightColoursGlowOnEveryCover() {
        // highlighted words glow in the palette's colours. On a light cover the ripple rule
        // puts the first ripple on the background's dark side, and a word glowing in it
        // would sink into the mid tones the stage draws; the highlight is lifted to the
        // lyric lightness with its saturation damped, keeping the art's hue
        fun primary(base: Int, darker: Int, lighter: Int) =
            PaletteBuilder.build(listOf(Swatch(base, 900), Swatch(darker, 250), Swatch(lighter, 150)), 3)
        val yellow = primary(0xFFFFFF00.toInt(), 0xFFB0B000.toInt(), 0xFFFFFF70.toInt())
        val green = primary(0xFF00FF00.toInt(), 0xFF00B000.toInt(), 0xFF70FF70.toInt())
        val grey = PaletteBuilder.build(listOf(Swatch(0xFFF0F0F0.toInt(), 900), Swatch(0xFFA5A5A5.toInt(), 270), Swatch(0xFF141414.toInt(), 270)), 5)
        listOf("yellow" to yellow, "green" to green, "grey" to grey).forEach { (name, p) ->
            p.accents.forEach { acc ->
                val hl = p.highlightColour(acc)
                assertTrue("$name highlight clears the bar as drawn (${Colour.contrast(Colour.rolled(p.base), hl)})",
                    Colour.contrast(Colour.rolled(p.base), hl) >= PaletteBuilder.TEXT_MIN_CONTRAST - 0.01)
            }
        }
        // dark covers: the ripples are already on the light side and pass unchanged, so
        // the glow keeps exactly the colour it had
        val red = primary(0xFFFF0000.toInt(), 0xFFB00000.toInt(), 0xFFFF7070.toInt())
        val blue = primary(0xFF0000FF.toInt(), 0xFF0000B0.toInt(), 0xFF7070FF.toInt())
        listOf(red, blue).forEach { p ->
            assertEquals(p.accents, p.accents.map { p.highlightColour(it) })
        }
    }

    @Test fun paletteBlackAndWhiteCoverStaysGrey() {
        // the reported case: a black-and-white cover (Demi Lovato's DEMI - light greys
        // dominant, near-black portrait, one mid grey) must give a black-and-white stage.
        // The old ripple rule added a flat saturation floor, and a grey's hue is red by
        // convention in HSL, so every grey picked up a pink tint
        val swatches = listOf(Swatch(0xFFF0F0F0.toInt(), 900), Swatch(0xFFA5A5A5.toInt(), 270), Swatch(0xFF141414.toInt(), 270))
        val p = PaletteBuilder.build(swatches, 5)
        fun pureGrey(c: Int) = Colour.red(c) == Colour.green(c) && Colour.green(c) == Colour.blue(c)
        assertEquals(0xFFF0F0F0.toInt(), p.base)
        p.accents.forEach { assertTrue("accent ${"%06X".format(it and 0xFFFFFF)} is a pure grey", pureGrey(it)) }
        assertTrue("lyrics are a pure grey", pureGrey(p.text))
        // light cover: the lyrics are the light grey shade, and they clear the bar as drawn
        assertTrue(Colour.toHsl(p.text)[2] >= PaletteBuilder.TEXT_L - 0.11f)
        assertTrue(Colour.contrast(Colour.rolled(p.base), p.text) >= PaletteBuilder.TEXT_MIN_CONTRAST - 0.01)
        // a dark-dominant monochrome cover: light grey pools under white lyrics, all grey
        val dark = PaletteBuilder.build(listOf(Swatch(0xFF141414.toInt(), 900), Swatch(0xFFD0D0D0.toInt(), 300), Swatch(0xFFA5A5A5.toInt(), 150)), 5)
        assertTrue(pureGrey(dark.base))
        dark.accents.forEach { assertTrue(pureGrey(it)) }
        assertTrue(pureGrey(dark.text))
        assertTrue(Colour.toHsl(dark.text)[2] >= PaletteBuilder.TEXT_L - 0.11f)
        // and a coloured cover keeps its colour: the saturation floor only ramps in past
        // near-grey, so nothing changes for artwork that actually has hue
        val coloured = PaletteBuilder.build(listOf(Swatch(0xFFD23C4C.toInt(), 900), Swatch(0xFF101828.toInt(), 300)), 5)
        assertTrue(Colour.toHsl(coloured.accents[0])[1] > 0.3f)
    }

    @Test fun paletteStaysWithTheCover() {
        // one blue hue: every accent stays blue-ish, none is a made-up colour
        val blue = PaletteBuilder.build(listOf(Swatch(0xFF2050D0.toInt(), 800), Swatch(0xFF101828.toInt(), 900)), 4)
        blue.accents.forEach { assertTrue(Colour.hueDistance(Colour.toHsl(it)[0], Colour.toHsl(0xFF2050D0.toInt())[0]) < 0.08f) }
        // a black and white cover: greys in the artwork's own tones, nothing saturated
        val grey = PaletteBuilder.build(listOf(Swatch(0xFF202020.toInt(), 900), Swatch(0xFFD0D0D0.toInt(), 400)), 4)
        grey.accents.forEach { assertTrue(Colour.toHsl(it)[1] <= 0.25f) }
        assertTrue(Colour.toHsl(grey.text)[1] <= 0.25f)
        // near-black cover: the background is that near-black, exactly as the artwork has it
        assertEquals(0xFF202020.toInt(), grey.base)
        // and a dark cover: the lyrics are the light shade, the light grey rides above it
        assertTrue(Colour.toHsl(grey.text)[2] >= PaletteBuilder.TEXT_L - 0.11f)
        assertTrue(Colour.toHsl(grey.accents[0])[2] >= PaletteBuilder.L_MIN + 0.5f)
    }

    private fun <T> shuffled(items: List<T>, rnd: kotlin.random.Random): List<T> {
        val out = items.toMutableList()
        for (i in out.size - 1 downTo 1) {
            val j = rnd.nextInt(i + 1)
            val t = out[i]; out[i] = out[j]; out[j] = t
        }
        return out
    }

    @Test fun templatesKeepWordsApart() {
        // settled words of row templates never overlap each other
        val line = MeasuredLine.measure(LyricsBuilder.fromLrc(Samples.read("english"), 60_000).lines[0], Samples.measure)
        for (t in Templates.all) {
            val layout = t.layout(line, SizeF(1080f, 2200f), 7)
            val boxes = layout.words.map { it.bounds() }
            for (i in boxes.indices) for (j in i + 1 until boxes.size) {
                val a = boxes[i]
                val b = boxes[j]
                // allow a sliver for rotated Flow words touching at the corners
                val overlapW = minOf(a.right, b.right) - maxOf(a.left, b.left)
                val overlapH = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
                assertTrue("${t.id} words $i/$j overlap", overlapW <= a.width * 0.12f || overlapH <= a.height * 0.2f)
            }
        }
    }
}
