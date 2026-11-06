package com.lyricstage.stage

import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.lyricstage.core.lyrics.LyricLine
import com.lyricstage.core.lyrics.Lyrics
import com.lyricstage.core.stage.REF_PX
import com.lyricstage.core.stage.SizeF
import com.lyricstage.core.stage.StagePlan
import com.lyricstage.core.stage.TemplateId
import com.lyricstage.core.stage.Templates
import com.lyricstage.core.stage.TextMeasure
import com.lyricstage.core.stage.WordMetrics
import com.lyricstage.core.text.Script
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * A [StagePlan] plus every text layout it draws, measured once at final sizes so each word
 * renders crisp at scale 1 once settled. Built on lyrics load and on stage size change.
 *
 * Both measuring passes run in parallel. A song's words are laid out by several workers at
 * once — one [TextMeasurer] per worker, each cacheless (a cacheless measurer holds no
 * mutable state of its own; the shared resolver's caches are synchronized) — so switching
 * songs no longer spends one long single-threaded burst on text shaping while the stage
 * waits blank. Pass 1 measures every *distinct* word once at the reference size (repeats
 * like "the" and "you" share a layout); pass 2 lays each line out at its final size, one
 * line per task pulled from a shared counter, so per-glyph templates don't block others.
 */
class PreparedStage(
    val plan: StagePlan,
    /** Per line, per placement (same order as `layout.words`). */
    val words: List<List<TextLayoutResult>>,
    /** Per line, per placement, per glyph; only for per-glyph templates. */
    val glyphs: List<List<List<TextLayoutResult>>?>,
    val subtitles: List<TextLayoutResult?>,
    /** Per line: words sung at least this long count as held notes (lift and glow). */
    val heldMs: LongArray,
) {
    companion object {
        /** A held note is at least this long, and at least [HELD_FACTOR] times the line's median word. */
        private const val HELD_MIN_MS = 650L
        private const val HELD_FACTOR = 1.8

        /** Measuring workers: half the cores, between 1 and 4, so the UI keeps its headroom. */
        private fun workerCount(): Int =
            (Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, 4)

        suspend fun prepare(
            lyrics: Lyrics,
            stage: SizeF,
            resolver: FontFamily.Resolver,
            density: Density,
            playSeed: Int,
            locked: TemplateId?,
            reduceMotion: Boolean,
            fontScale: Float,
        ): PreparedStage = coroutineScope {
            val workers = workerCount()
            val measurers = List(workers) { TextMeasurer(resolver, density, LayoutDirection.Ltr, 0) }

            // Pass 1 — every distinct (word, script) once, at the reference size, in parallel
            val keys = LinkedHashSet<WordKey>()
            for (line in lyrics.lines) for (w in line.words) keys.add(WordKey(w.text, line.script))
            val cache = ConcurrentHashMap<WordKey, WordMetrics>(keys.size * 2)
            if (keys.isNotEmpty()) {
                val list = keys.toList()
                val chunk = (list.size + workers - 1) / workers
                list.chunked(chunk).mapIndexed { w, part ->
                    async(Dispatchers.Default) {
                        val m = measurers[w]
                        for (k in part) cache[k] = measureWord(m, k.text, k.script, density)
                    }
                }.awaitAll()
            }
            val plan = StagePlan.build(
                lyrics, LookupMeasure(cache, measurers[0], density), stage, playSeed, locked, reduceMotion, fontScale,
            )

            // Pass 2 — final-size layouts, a line per task from a shared counter
            val n = plan.lines.size
            val words = arrayOfNulls<List<TextLayoutResult>>(n)
            val glyphs = arrayOfNulls<List<List<TextLayoutResult>>?>(n)
            val subs = arrayOfNulls<TextLayoutResult?>(n)
            val next = AtomicInteger()
            (0 until workers).map {
                async(Dispatchers.Default) {
                    val measurer = measurers[it]
                    while (true) {
                        val li = next.getAndIncrement()
                        if (li >= n) break
                        measureLine(plan, measurer, density, li, words, glyphs, subs)
                    }
                }
            }.awaitAll()

            val held = LongArray(n) { li ->
                val d = plan.lines[li].words.map { it.durationMs }.sorted()
                val median = d.getOrNull(d.size / 2) ?: 0L
                maxOf(HELD_MIN_MS, (median * HELD_FACTOR).toLong())
            }
            PreparedStage(
                plan,
                List(n) { words[it]!! },
                List(n) { glyphs[it] },
                List(n) { subs[it] },
                held,
            )
        }

        /** One line's final layouts: words at their placed size, glyphs for per-glyph templates, subtitle. */
        private fun measureLine(
            plan: StagePlan,
            measurer: TextMeasurer,
            density: Density,
            li: Int,
            words: Array<List<TextLayoutResult>?>,
            glyphs: Array<List<List<TextLayoutResult>>?>,
            subs: Array<TextLayoutResult?>,
        ) {
            val layout = plan.layouts[li]
            val line = plan.lines[li]
            words[li] = layout.words.map { p -> measurer.word(line.words[p.index].text, line.script, p.fontPx, density) }
            glyphs[li] = if (Templates.of(layout.template).perGlyph) {
                layout.words.map { p ->
                    plan.measured[li].words[p.index].glyphs.map { g -> measurer.word(g, line.script, p.fontPx, density) }
                }
            } else null
            val area = layout.subtitleArea
            val sub = line.sub
            subs[li] = if (area != null && !sub.isNullOrBlank()) {
                val px = (area.height * 0.34f).coerceAtMost(REF_PX * 0.6f)
                measurer.measure(
                    text = sub,
                    style = StageFonts.subtitle(Script.detect(sub), px, density).copy(textAlign = TextAlign.Center),
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 2,
                    constraints = Constraints(maxWidth = area.width.toInt().coerceAtLeast(1)),
                    density = density,
                )
            } else null
        }
    }
}

/** A word and the script it is laid out in: metrics are identical for the same pair. */
private data class WordKey(val text: String, val script: Script)

/** The plan's [TextMeasure]: prefetched metrics, single-worker fallback for a miss. */
private class LookupMeasure(
    private val cache: Map<WordKey, WordMetrics>,
    private val fallback: TextMeasurer,
    private val density: Density,
) : TextMeasure {
    override fun measure(text: String, line: LyricLine): WordMetrics =
        cache[WordKey(text, line.script)] ?: measureWord(fallback, text, line.script, density)
}
