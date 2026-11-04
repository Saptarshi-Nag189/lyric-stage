package com.lyricstage.audio

import android.media.audiofx.Visualizer
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Listens to the phone's mixed audio output (Visualizer on session 0) and reports likely
 * vocal onsets: moments where energy in the voice band (250 Hz to 3 kHz) jumps above its
 * recent average. Everything stays on the device; only timestamps leave this class.
 *
 * Needs RECORD_AUDIO. The work per capture is one pass over ~100 FFT bins, about 20 times a
 * second, on the Visualizer's own thread.
 */
class AudioOnsets {
    /** `SystemClock.elapsedRealtime()` of each detected onset, oldest first. */
    val onsets = ConcurrentLinkedQueue<Long>()

    private var visualizer: Visualizer? = null
    private var prev = FloatArray(0)
    private var mean = 0f
    private var variance = 0f
    private var lastOnset = 0L

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
    }

    /**
     * Spectral flux in the voice band. [fft] packs (re, im) bytes per bin after the first two;
     * [samplingRate] is in millihertz.
     */
    private fun analyse(fft: ByteArray, samplingRate: Int) {
        val bins = fft.size / 2
        val hzPerBin = samplingRate / 1000f / 2f / bins
        val lo = (LOW_HZ / hzPerBin).toInt().coerceIn(1, bins - 1)
        val hi = (HIGH_HZ / hzPerBin).toInt().coerceIn(lo + 1, bins - 1)
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
        // adaptive threshold: running mean and spread of the flux
        val d = flux - mean
        mean += ALPHA * d
        variance = (1 - ALPHA) * (variance + ALPHA * d * d)
        val now = SystemClock.elapsedRealtime()
        if (loud) lastSoundAt = now
        if (flux > mean + K * sqrt(variance) && flux > MIN_FLUX && now - lastOnset > MIN_GAP_MS) {
            lastOnset = now
            onsets.add(now)
            while (onsets.size > 64) onsets.poll()
        }
    }

    private companion object {
        const val TAG = "AudioOnsets"
        const val LOW_HZ = 250f
        const val HIGH_HZ = 3000f
        const val ALPHA = 0.08f
        const val K = 1.6f
        const val MIN_FLUX = 4f
        const val MIN_GAP_MS = 110L
        const val DEAF_MS = 6_000L
    }
}
