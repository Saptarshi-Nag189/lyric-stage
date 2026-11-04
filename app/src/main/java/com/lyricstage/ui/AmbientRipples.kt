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
 * otherwise at an unhurried, slightly irregular pace from random spots.
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
        fun ripple(strength: Float) = currentEmit(
            Ripple(0.15f + rnd.nextFloat() * 0.7f, 0.2f + rnd.nextFloat() * 0.6f, strength, newLine = false),
        )
        var lastBeat = 0L
        var lastBeatAt = 0L
        var nextGentle = SystemClock.elapsedRealtime() + 600 + rnd.nextLong(900)
        while (true) {
            // with a beat listener, check often for new beats; otherwise sleep until the next
            // gentle ripple is due (at most a second, so a change of song or gap is noticed)
            val b0 = beats
            delay(if (b0 != null && b0.running) TICK_MS else (nextGentle - SystemClock.elapsedRealtime()).coerceIn(TICK_MS, 1_000L))
            if (!currentAlways && !wanted(currentLyrics, clock.smoothedPositionMs())) continue
            val now = SystemClock.elapsedRealtime()
            val b = beats
            if (b != null && b.running && !b.isDeaf(now)) {
                val onset = b.onsets.lastOrNull()
                if (onset != null && onset > lastBeat) {
                    lastBeat = onset
                    if (now - onset < 250) {
                        ripple(0.55f + rnd.nextFloat() * 0.3f)
                        lastBeatAt = now
                    }
                }
                // beats are driving: gentle ripples only fill a long silence between them
                if (now - lastBeatAt < BEAT_QUIET_MS) {
                    nextGentle = now + GENTLE_MIN_MS
                    continue
                }
            }
            if (now >= nextGentle) {
                ripple(0.45f + rnd.nextFloat() * 0.3f)
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
