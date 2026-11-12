package com.refluxcare.app.meditation

import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin

/** Output sample rate for all synthesized audio. */
internal const val SAMPLE_RATE = 44100
internal const val SR = 44100f

/** Master volume applied to synthesized ambience (before soft clipping). */
internal const val MASTER_VOLUME = 0.35f

/** Linear-interpolated sine lookup. Phase is in cycles (any real value; it is wrapped). */
internal object Sine {
    private const val SIZE = 4096
    private val table = FloatArray(SIZE + 1) { sin(2.0 * PI * it / SIZE).toFloat() }

    fun at(phase: Float): Float {
        val x = (phase - floor(phase)) * SIZE
        var i = x.toInt()
        if (i >= SIZE) i = SIZE - 1
        if (i < 0) i = 0
        val f = x - i
        val a = table[i]
        return a + (table[i + 1] - a) * f
    }
}

/** Tiny allocation-free xorshift PRNG. */
internal class Rng(seed: Int = nextSeed()) {
    private var s: Int = if (seed == 0) 0x2545F491 else seed

    fun nextInt(): Int {
        var x = s
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        s = x
        return x
    }

    /** Uniform in [0, 1). */
    fun nextFloat(): Float = (nextInt() ushr 8) * (1f / 16777216f)

    /** Uniform in [-1, 1). */
    fun white(): Float = nextFloat() * 2f - 1f

    fun range(a: Float, b: Float): Float = a + (b - a) * nextFloat()

    companion object {
        private val counter = AtomicInteger(0x1234567)
        fun nextSeed(): Int =
            (System.nanoTime().toInt() xor counter.addAndGet(0x9E3779B9.toInt())).let { if (it == 0) 1 else it }
    }
}

/** One-pole low-pass filter. */
internal class OnePoleLP(cutoffHz: Float) {
    private var a = coef(cutoffHz)
    private var y = 0f

    fun setCutoff(hz: Float) {
        a = coef(hz)
    }

    fun process(x: Float): Float {
        y += a * (x - y)
        return y
    }

    companion object {
        fun coef(hz: Float): Float = (1.0 - exp(-2.0 * PI * hz / SR)).toFloat()
    }
}

/** DC blocking high-pass (~30 Hz). */
internal class DcBlocker(private val r: Float = 0.995f) {
    private var x1 = 0f
    private var y1 = 0f

    fun process(x: Float): Float {
        val y = x - x1 + r * y1
        x1 = x
        y1 = y
        return y
    }
}

/** Per-sample exponential decay multiplier for time constant [tauSec]. */
internal fun decayPerSample(tauSec: Float): Float = exp(-1f / (tauSec * SR))

/** Smooth soft clipper: linear below 0.7, asymptotically approaches 1.0. */
internal fun softClip(x: Float): Float {
    val ax = abs(x)
    if (ax <= 0.7f) return x
    val over = ax - 0.7f
    val y = 0.7f + 0.3f * (over / (over + 0.3f))
    return if (x < 0f) -y else y
}

internal fun toPcm16(x: Float): Short = (softClip(x) * 32767f).toInt().toShort()
