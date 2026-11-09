package com.lyricstage.ui

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.lyricstage.audio.AudioOnsets
import com.lyricstage.core.clock.PlaybackClock
import com.lyricstage.core.lyrics.Lyrics
import com.lyricstage.core.lyrics.LyricsKind
import com.lyricstage.core.stage.Ripple
import com.lyricstage.stage.StageRenderer
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Ripples for when no sung word makes them: no lyrics, instrumentals, unsynced text, the
 * intro and long gaps between lines. On beats heard by [beats] when it can hear the music;
 * otherwise at an unhurried, slightly irregular pace. Beats land from alternating sides of
 * the lower stage, like the music bouncing between two points, every fourth one accented;
 * when the beats hold a tempo the missed ones still land softly on the grid.
 */
@Composable
fun AmbientRipples(
    active: Boolean,
    lyrics: Lyrics?,
    clock: PlaybackClock,
    beats: AudioOnsets?,
    /** Beats for the whole song, sung lines included (the "Beats" ripple setting). */
    always: Boolean = false,
    emit: (Ripple) -> Unit,
) {
    val currentLyrics by rememberUpdatedState(lyrics)
    val currentAlways by rememberUpdatedState(always)
    val currentEmit by rememberUpdatedState(emit)
    LaunchedEffect(active, beats) {
        if (!active) return@LaunchedEffect
        val rnd = Random(SystemClock.elapsedRealtime())
        var side = if (rnd.nextBoolean()) 1 else -1
        var beatIndex = 0
        var lastOnsetAt = 0L      // the last real onset taken from the listener
        var lastRippleAt = 0L     // the last beat ripple, heard or carried
        var gridNext = 0L         // when the tempo says the next beat lands; 0 when it doesn't
        var carried = 0           // beats landed on the grid without hearing one, in a row
        var nextGentle = SystemClock.elapsedRealtime() + 600 + rnd.nextLong(900)
        fun place(strength: Float, beat: Boolean) {
            side = -side
            val x = (0.5f + side * (0.20f + 0.12f * rnd.nextFloat())).coerceIn(0.06f, 0.94f)
            val y = 0.58f + 0.20f * rnd.nextFloat()
            currentEmit(Ripple(x, y, strength, beat))
        }
        while (true) {
            // with a beat listener, check often for new beats; otherwise sleep until the next
            // gentle ripple is due (at most a second, so a change of song or gap is noticed)
            val b0 = beats
            delay(if (b0 != null && b0.running) TICK_MS else (nextGentle - SystemClock.elapsedRealtime()).coerceIn(TICK_MS, 1_000L))
            if (!currentAlways && !wanted(currentLyrics, clock.smoothedPositionMs())) continue
            val now = SystemClock.elapsedRealtime()
            val b = beats
            if (b != null && b.running && !b.isDeaf(now)) {
                var heard = false
                while (true) {
                    val o = b.onsets.poll() ?: break
                    if (o.at <= lastOnsetAt) continue
                    lastOnsetAt = o.at
                    // a beat older than a moment feels like a different one; let it go
                    if (now - o.at > 260) continue
                    beatIndex++
                    val accent = if (beatIndex % 4 == 0) 0.14f else 0f
                    place((0.52f + 0.34f * o.strength + accent).coerceAtMost(1f), true)
                    heard = true
                    lastRippleAt = now
                    carried = 0
                }
                val period = b.periodMs
                if (heard) {
                    gridNext = if (period > 0) lastOnsetAt + period else 0L
                } else if (gridNext > 0 && period > 0) {
                    // a steady tempo: a beat the ear missed still lands, softly, on the grid -
                    // but no more than three in a row without hearing one
                    if (now >= gridNext - TICK_MS && carried < 3) {
                        beatIndex++
                        place(0.5f, true)
                        carried++
                        lastRippleAt = now
                        gridNext += period
                    } else if (now > gridNext + period * 2) {
                        gridNext = 0
                    }
                }
                // beats are driving: gentle ripples only fill a long silence between them
                if (now - lastRippleAt < BEAT_QUIET_MS) {
                    nextGentle = now + GENTLE_MIN_MS
                    continue
                }
            }
            if (now >= nextGentle) {
                place(0.45f + rnd.nextFloat() * 0.3f, false)
                nextGentle = now + GENTLE_MIN_MS + rnd.nextLong(GENTLE_SPREAD_MS)
            }
        }
    }
}

/** True when nothing sung is making ripples: no timed lines, or between them. */
fun wantsAmbientRipples(lyrics: Lyrics?): Boolean =
    lyrics == null || lyrics.kind == LyricsKind.INSTRUMENTAL || lyrics.kind == LyricsKind.PLAIN || lyrics.lines.isEmpty()

private fun wanted(lyrics: Lyrics?, pos: Long): Boolean {
    if (wantsAmbientRipples(lyrics)) return true
    val lines = lyrics!!.lines
    // in a line (or just before it enters): sung words make the ripples
    val next = lines.firstOrNull { it.startMs > pos }
    val inLine = lines.any { pos >= it.startMs && pos < it.endMs }
    return !inLine && (next == null || next.startMs - pos >= StageRenderer.GAP_MIN_MS)
}

private const val TICK_MS = 60L
private const val BEAT_QUIET_MS = 4_000L
private const val GENTLE_MIN_MS = 1_400L
private const val GENTLE_SPREAD_MS = 1_600L
