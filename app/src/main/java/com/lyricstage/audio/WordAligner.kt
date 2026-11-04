package com.lyricstage.audio

import com.lyricstage.core.lyrics.LyricLine
import kotlin.math.abs

/**
 * Turns vocal onsets into a timing correction for estimated words. Line starts come from the
 * lyrics and are trusted, so the correction starts at zero on every line; within a line each
 * onset that lands near a word start pulls the words towards it.
 */
class WordAligner {
    var shiftMs = 0L
        private set
    private var line = -1

    fun reset() {
        shiftMs = 0
        line = -1
    }

    /**
     * @param onsetMs onset positions on the playback timeline (already converted).
     */
    fun update(lineIndex: Int, current: LyricLine?, onsetMs: List<Long>) {
        if (lineIndex != line) {
            line = lineIndex
            shiftMs = 0
        }
        if (current == null || onsetMs.isEmpty()) return
        for (t in onsetMs) {
            val nearest = current.words.minByOrNull { abs(it.startMs + shiftMs - t) } ?: continue
            val err = t - (nearest.startMs + shiftMs)
            if (abs(err) <= WINDOW_MS) shiftMs = (shiftMs + (err * GAIN).toLong()).coerceIn(-MAX_MS, MAX_MS)
        }
    }

    private companion object {
        const val WINDOW_MS = 220L
        const val GAIN = 0.4
        const val MAX_MS = 450L
    }
}
