package com.lyricstage.audio

import android.media.audiofx.Visualizer
import android.os.SystemClock
import android.util.Log
import java.util.Arrays
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/** One detected onset: when it landed, and how hard it hit (0.45 to 1, against the recent peaks). */
class Onset(val at: Long, val strength: Float)

/**
 * Listens to the phone's mixed audio output (Visualizer on session 0) and reports likely
 * onsets: moments where energy in the watched band jumps above its recent level. The
 * threshold is the median of the last few seconds plus a multiple of its spread, so a
 * steady rhythm never teaches the threshold to ignore itself the way a running average
 * does; a flat drone, whose spread collapses, is refused by the median step. Everything
 * stays on the device; only timestamps and strengths leave this class.
 *
 * Needs RECORD_AUDIO. The work per capture is one pass over ~100 FFT bins, about 20 times a
 * second, on the Visualizer's own thread.
 */
class AudioOnsets(
    /** Band to watch: the voice by default; 40 to 160 Hz finds kick drums (beats). */
    private val lowHz: Float = LOW_HZ,
    private val highHz: Float = HIGH_HZ,
    private val minGapMs: Long = MIN_GAP_MS,
    private val minFlux: Float = MIN_FLUX,
    /** How many spreads above the median a frame must rise; higher hears only the sharp ones. */
    private val sharpness: Float = SHARPNESS,
) {
    /** Each detected onset, oldest first. */
    val onsets = ConcurrentLinkedQueue<Onset>()

    /** The interval recent onsets keep, when they hold a steady tempo; 0 when they don't. */
    @Volatile var periodMs: Long = 0L
        private set

    private var visualizer: Visualizer? = null
    private var prev = FloatArray(0)

    // the last frames of flux, with scratch space for their median and spread
    private val hist = FloatArray(HISTORY)
    private val scratch = FloatArray(HISTORY)
    private var histAt = 0
    private var histFilled = false

    private var prevFlux = 0f
    private var peak = 0f
    private var lastOnset = 0L

    // the intervals between the last few onsets, for the steady tempo
    private val iois = LongArray(IOIS)
    private val ioiScratch = LongArray(IOIS)
    private var ioiAt = 0
    private var ioiCount = 0
    private var prevOnsetAt = 0L

    val running: Boolean get() = visualizer != null

    @Volatile private var startedAt = 0L
    @Volatile private var lastSoundAt = 0L
    @Volatile private var failed = false

    /**
     * True when audio sync cannot hear the music: the output could not be opened, or it has
     * delivered only silence for a while. Common with Bluetooth earphones, whose audio often
     * bypasses the mix the Visualizer reads.
     */
    fun isDeaf(now: Long = SystemClock.elapsedRealtime()): Boolean =
        failed || (visualizer != null && now - startedAt > DEAF_MS && now - lastSoundAt > DEAF_MS)

    fun start() {
        if (visualizer != null) return
        failed = false
        startedAt = SystemClock.elapsedRealtime()
        lastSoundAt = 0L
        try {
            val v = Visualizer(0)
            v.captureSize = Visualizer.getCaptureSizeRange()[1].coerceAtMost(1024)
            v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) = Unit
                override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                    if (fft != null) analyse(fft, samplingRate)
                }
            }, Visualizer.getMaxCaptureRate(), false, true)
            v.enabled = true
            visualizer = v
        } catch (e: Exception) {
            // no permission, or the device does not allow output capture
            Log.w(TAG, "Audio sync unavailable: ${e.message}")
            stop()
            failed = true
        }
    }

    fun stop() {
        visualizer?.let {
            runCatching { it.enabled = false }
            it.release()
        }
        visualizer = null
        onsets.clear()
        prev = FloatArray(0)
        prevFlux = 0f
        peak = 0f
        histAt = 0
        histFilled = false
        ioiAt = 0
        ioiCount = 0
        prevOnsetAt = 0L
        periodMs = 0L
    }

    /**
     * Spectral flux in the watched band. [fft] packs (re, im) bytes per bin after the first two;
     * [samplingRate] is in millihertz.
     */
    private fun analyse(fft: ByteArray, samplingRate: Int) {
        val bins = fft.size / 2
        val hzPerBin = samplingRate / 1000f / 2f / bins
        val lo = (lowHz / hzPerBin).toInt().coerceIn(1, bins - 1)
        val hi = (highHz / hzPerBin).toInt().coerceIn(lo + 1, bins - 1)
        if (prev.size != hi - lo) prev = FloatArray(hi - lo)
        var flux = 0f
        var loud = false
        for (k in lo until hi) {
            val re = fft[2 * k].toFloat()
            val im = fft[2 * k + 1].toFloat()
            if (re != 0f || im != 0f) loud = true
            val mag = ln(1f + sqrt(re * re + im * im))
            val i = k - lo
            flux += max(0f, mag - prev[i])
            prev[i] = mag
        }
        val now = SystemClock.elapsedRealtime()
        if (loud) lastSoundAt = now
        // a rising frame that clears the recent level by its own spread: an onset
        if (flux >= prevFlux && flux > threshold() && now - lastOnset > minGapMs) {
            lastOnset = now
            onsets.add(Onset(now, (flux / max(peak, 1e-4f)).coerceIn(0.45f, 1f)))
            while (onsets.size > 64) onsets.poll()
            notePeriod(now)
        }
        // the recent peak decays, so strength is measured against the last few seconds
        peak = max(flux, peak * PEAK_DECAY)
        prevFlux = flux
        hist[histAt] = flux
        histAt = (histAt + 1) % HISTORY
        if (histAt == 0) histFilled = true
    }

    /** Median of the recent flux plus [sharpness] times its spread, with a floor of [minFlux]. */
    private fun threshold(): Float {
        val n = if (histFilled) HISTORY else histAt
        if (n < 8) return minFlux
        System.arraycopy(hist, 0, scratch, 0, n)
        Arrays.sort(scratch, 0, n)
        val median = scratch[n / 2]
        for (i in 0 until n) scratch[i] = abs(hist[i] - median)
        Arrays.sort(scratch, 0, n)
        val spread = scratch[n / 2]
        // the median step refuses a flat drone, whose spread collapses to almost nothing
        return max(minFlux, max(median * 1.3f, median + sharpness * spread))
    }

    /** Keeps the interval between onsets when they hold a tempo, for the beats that follow. */
    private fun notePeriod(at: Long) {
        if (prevOnsetAt > 0) {
            val ioi = at - prevOnsetAt
            if (ioi in MIN_IOI_MS..MAX_IOI_MS) {
                iois[ioiAt] = ioi
                ioiAt = (ioiAt + 1) % IOIS
                if (ioiCount < IOIS) ioiCount++
            } else {
                ioiCount = 0
            }
        }
        prevOnsetAt = at
        if (ioiCount >= 4) {
            System.arraycopy(iois, 0, ioiScratch, 0, ioiCount)
            Arrays.sort(ioiScratch, 0, ioiCount)
            val mid = ioiScratch[ioiCount / 2]
            val lo = (mid * 4 / 5)
            val hi = (mid * 5 / 4)
            var agree = 0
            for (i in 0 until ioiCount) if (ioiScratch[i] in lo..hi) agree++
            periodMs = if (agree * 4 >= ioiCount * 3) mid else 0L
        } else {
            periodMs = 0L
        }
    }

    companion object {
        /** Kick drums: low band, at most four beats a second. */
        fun beats() = AudioOnsets(lowHz = 40f, highHz = 160f, minGapMs = 250L, minFlux = 1.2f, sharpness = 2.6f)

        const val TAG = "AudioOnsets"
        const val LOW_HZ = 250f
        const val HIGH_HZ = 3000f
        const val SHARPNESS = 2.4f
        const val MIN_FLUX = 4f
        const val MIN_GAP_MS = 110L
        const val DEAF_MS = 6_000L
        private const val HISTORY = 64
        private const val IOIS = 8
        private const val PEAK_DECAY = 0.97f
        private const val MIN_IOI_MS = 120L
        private const val MAX_IOI_MS = 2_500L
    }
}
