package com.lyricstage.background

import com.lyricstage.core.stage.Ripple

/**
 * Ring buffer of the most recent ripples, laid out for the shader's `uRip[8]` uniform:
 * x, y (0 to 1, top-left origin), start time in shader seconds, strength. A beat carries
 * its strength negative: the shader reads the sign and draws it as a pulse, not a splash.
 */
class RippleEmitter {
    val data = FloatArray(MAX * 4)
    private var next = 0

    fun emit(r: Ripple, shaderTime: Float) {
        val i = (next++ % MAX) * 4
        data[i] = r.x
        data[i + 1] = r.y
        data[i + 2] = shaderTime
        data[i + 3] = if (r.beat) -r.strength else r.strength
    }

    fun clear() {
        data.fill(0f)
    }

    companion object {
        const val MAX = 8
    }
}
