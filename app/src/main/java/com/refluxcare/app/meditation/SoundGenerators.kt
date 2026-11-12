package com.refluxcare.app.meditation

import kotlin.math.exp
import kotlin.math.pow

/**
 * Real-time ambient sound source. Implementations must be allocation-free in [fill]
 * and keep all oscillator phases continuous between calls.
 */
interface SoundGenerator {
    /**
     * Fill [buf] with [frames] interleaved stereo frames (L, R, L, R, ...) at [SAMPLE_RATE],
     * nominally within [-1, 1]. Master volume / limiting is applied by the player.
     */
    fun fill(buf: FloatArray, frames: Int)
}

internal object SoundGenerators {
    fun create(id: String): SoundGenerator? = when (id) {
        "rain" -> RainGenerator()
        "ocean" -> OceanGenerator()
        "bowls" -> BowlsGenerator()
        "om" -> OmGenerator()
        "brown" -> BrownNoiseGenerator()
        "chimes" -> ChimesGenerator()
        "forest" -> ForestGenerator()
        "binaural" -> BinauralGenerator()
        else -> null
    }
}

// ---------------------------------------------------------------------------------------------
// Rain
// ---------------------------------------------------------------------------------------------

internal class RainGenerator : SoundGenerator {
    private val rng = Rng()
    private val hiL = OnePoleLP(4200f)
    private val hiR = OnePoleLP(4200f)
    private val hi2L = OnePoleLP(6500f)
    private val hi2R = OnePoleLP(6500f)
    private val loL = OnePoleLP(650f)
    private val loR = OnePoleLP(650f)
    private val dcL = DcBlocker()
    private val dcR = DcBlocker()
    private var swellPhase = rng.nextFloat()

    private val maxDrops = 12
    private val dActive = BooleanArray(maxDrops)
    private val dPhase = FloatArray(maxDrops)
    private val dInc = FloatArray(maxDrops)
    private val dSweep = FloatArray(maxDrops)
    private val dEnv = FloatArray(maxDrops)
    private val dDecay = FloatArray(maxDrops)
    private val dAtk = FloatArray(maxDrops)
    private val dPanL = FloatArray(maxDrops)
    private val dPanR = FloatArray(maxDrops)

    private val dropRate = 14f / SR
    private val atkStep = 1f / (0.0015f * SR)

    override fun fill(buf: FloatArray, frames: Int) {
        for (i in 0 until frames) {
            swellPhase += 1f / (SR * 27f)
            if (swellPhase >= 1f) swellPhase -= 1f
            val intensity = 0.85f + 0.15f * Sine.at(swellPhase)

            val wl = rng.white()
            val wr = rng.white()
            var l = hi2L.process(hiL.process(wl)) * 0.9f + loL.process(wl) * 1.3f
            var r = hi2R.process(hiR.process(wr)) * 0.9f + loR.process(wr) * 1.3f
            l = dcL.process(l) * intensity
            r = dcR.process(r) * intensity

            if (rng.nextFloat() < dropRate * intensity) spawnDrop()

            for (d in 0 until maxDrops) {
                if (!dActive[d]) continue
                val s = Sine.at(dPhase[d]) * dEnv[d] * dAtk[d]
                dPhase[d] += dInc[d]
                if (dPhase[d] >= 1f) dPhase[d] -= 1f
                dInc[d] *= dSweep[d]
                if (dAtk[d] < 1f) {
                    dAtk[d] += atkStep
                    if (dAtk[d] > 1f) dAtk[d] = 1f
                }
                dEnv[d] *= dDecay[d]
                if (dEnv[d] < 1e-4f) dActive[d] = false
                l += s * dPanL[d]
                r += s * dPanR[d]
            }
            buf[2 * i] = l
            buf[2 * i + 1] = r
        }
    }

    private fun spawnDrop() {
        var idx = -1
        for (d in 0 until maxDrops) if (!dActive[d]) { idx = d; break }
        if (idx < 0) return
        val loud = rng.nextFloat()
        dActive[idx] = true
        dPhase[idx] = 0f
        dInc[idx] = rng.range(1600f, 4200f) / SR
        dSweep[idx] = 1f - rng.range(0.00005f, 0.0002f)
        dEnv[idx] = 0.015f + 0.06f * loud * loud
        dDecay[idx] = decayPerSample(rng.range(0.004f, 0.014f))
        dAtk[idx] = 0f
        val p = rng.nextFloat()
        dPanL[idx] = 1f - 0.7f * p
        dPanR[idx] = 0.3f + 0.7f * p
    }
}

// ---------------------------------------------------------------------------------------------
// Ocean waves
// ---------------------------------------------------------------------------------------------

internal class OceanGenerator : SoundGenerator {
    private val rng = Rng()
    private var bL = 0f
    private var bR = 0f
    private val lpL = OnePoleLP(600f)
    private val lpR = OnePoleLP(600f)
    private val dcL = DcBlocker()
    private val dcR = DcBlocker()
    private var wavePhase = rng.nextFloat()
    private var waveInc = rng.range(0.08f, 0.12f) / SR
    private var depth = rng.range(0.75f, 1f)
    private var counter = 0

    private fun swell(p: Float): Float {
        val s = 0.5f - 0.5f * Sine.at(p + 0.25f) // 0 at trough, 1 at crest
        return 0.15f + 0.85f * depth * s * s
    }

    override fun fill(buf: FloatArray, frames: Int) {
        for (i in 0 until frames) {
            wavePhase += waveInc
            if (wavePhase >= 1f) {
                wavePhase -= 1f
                waveInc = rng.range(0.08f, 0.12f) / SR
                depth = rng.range(0.7f, 1f)
            }
            val envL = swell(wavePhase)
            val envR = swell(wavePhase - 0.025f)
            if ((counter++ and 15) == 0) {
                lpL.setCutoff(260f + 2300f * envL * envL)
                lpR.setCutoff(260f + 2300f * envR * envR)
            }
            bL = (bL + rng.white() * 0.02f) * 0.998f
            bR = (bR + rng.white() * 0.02f) * 0.998f
            val l = bL * 1.6f * (0.45f + 0.55f * envL) + lpL.process(rng.white()) * 1.3f * envL
            val r = bR * 1.6f * (0.45f + 0.55f * envR) + lpR.process(rng.white()) * 1.3f * envR
            buf[2 * i] = dcL.process(l)
            buf[2 * i + 1] = dcR.process(r)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Singing bowls
// ---------------------------------------------------------------------------------------------

internal class BowlsGenerator : SoundGenerator {
    private val rng = Rng()
    private val voices = 6
    private val partials = 8 // 4 partials x detuned pair
    private val ratios = floatArrayOf(1f, 2.76f, 5.4f, 8.9f)
    private val amps = floatArrayOf(1f, 0.5f, 0.26f, 0.12f)
    private val tauMul = floatArrayOf(1f, 0.62f, 0.38f, 0.22f)
    private val beats = floatArrayOf(0.7f, 1.3f, 2.1f, 3.0f)
    private val baseFreqs = floatArrayOf(196f, 220f, 246.94f, 261.63f, 293.66f)

    private val active = BooleanArray(voices)
    private val atk = FloatArray(voices)
    private val panL = FloatArray(voices)
    private val panR = FloatArray(voices)
    private val ph = FloatArray(voices * partials)
    private val inc = FloatArray(voices * partials)
    private val env = FloatArray(voices * partials)
    private val dec = FloatArray(voices * partials)
    private val atkStep = 1f / (0.02f * SR)
    private var untilNext = (0.4f * SR).toInt()
    private var lastFreq = -1

    override fun fill(buf: FloatArray, frames: Int) {
        for (i in 0 until frames) {
            if (--untilNext <= 0) {
                strike()
                untilNext = (rng.range(8f, 14f) * SR).toInt()
            }
            var l = 0f
            var r = 0f
            for (v in 0 until voices) {
                if (!active[v]) continue
                val base = v * partials
                var s = 0f
                for (p in 0 until partials) {
                    val k = base + p
                    s += Sine.at(ph[k]) * env[k]
                    ph[k] += inc[k]
                    if (ph[k] >= 1f) ph[k] -= 1f
                    env[k] *= dec[k]
                }
                if (atk[v] < 1f) {
                    s *= atk[v]
                    atk[v] += atkStep
                    if (atk[v] > 1f) atk[v] = 1f
                }
                l += s * panL[v]
                r += s * panR[v]
                if (env[base] < 1e-3f && env[base + 1] < 1e-3f) active[v] = false
            }
            buf[2 * i] = l * 0.34f
            buf[2 * i + 1] = r * 0.34f
        }
    }

    private fun strike() {
        var v = -1
        for (j in 0 until voices) if (!active[j]) { v = j; break }
        if (v < 0) {
            // Steal the quietest voice (already near-silent in practice).
            var minE = Float.MAX_VALUE
            for (j in 0 until voices) {
                val e = env[j * partials]
                if (e < minE) { minE = e; v = j }
            }
        }
        var fi = (rng.nextFloat() * baseFreqs.size).toInt().coerceIn(0, baseFreqs.size - 1)
        if (fi == lastFreq) fi = (fi + 1 + (rng.nextFloat() * (baseFreqs.size - 1)).toInt()) % baseFreqs.size
        lastFreq = fi
        val f0 = baseFreqs[fi]
        val decaySec = rng.range(8f, 12f)
        val tau = decaySec / 3f
        val strength = rng.range(0.7f, 1f)
        val base = v * partials
        for (j in 0 until 4) {
            for (d in 0 until 2) {
                val k = base + j * 2 + d
                val sign = if (d == 0) -0.5f else 0.5f
                val f = f0 * ratios[j] + sign * beats[j] * rng.range(0.7f, 1.3f)
                inc[k] = f / SR
                ph[k] = rng.nextFloat()
                env[k] = amps[j] * 0.5f * strength
                dec[k] = decayPerSample(tau * tauMul[j])
            }
        }
        val p = rng.range(0.25f, 0.75f)
        panL[v] = 1.2f * (1f - p) + 0.2f
        panR[v] = 1.2f * p + 0.2f
        atk[v] = 0f
        active[v] = true
    }
}

// ---------------------------------------------------------------------------------------------
// Om drone
// ---------------------------------------------------------------------------------------------

internal class OmGenerator : SoundGenerator {
    private val f0 = 136.1f
    private val harmonics = 10
    private var phL = 0f
    private var phR = 0f
    private var subPh = 0f
    private val incL = f0 / SR
    private val incR = f0 * 1.0022f / SR
    private val subInc = f0 * 0.5f / SR
    private val w = FloatArray(harmonics)
    private val wTarget = FloatArray(harmonics)
    private var morphPh = 0f
    private var breathPh = 0f
    private var counter = 0

    init {
        updateTargets()
        for (h in 0 until harmonics) w[h] = wTarget[h]
    }

    private fun updateTargets() {
        val morph = 0.5f - 0.5f * Sine.at(morphPh + 0.25f) // 0 = closed "mmm", 1 = open "ooo"
        val fc = 260f + 560f * morph
        var sum = 0f
        for (h in 0 until harmonics) {
            val n = (h + 1).toFloat()
            val d = (f0 * n - fc) / 170f
            val boost = exp(-d * d)
            var t = (1f / n.pow(1.25f)) * (0.35f + 1.4f * boost * (0.25f + 0.75f * morph))
            if (h >= 4) t *= 0.4f + 0.6f * morph
            wTarget[h] = t
            sum += t
        }
        val norm = 0.8f / sum
        for (h in 0 until harmonics) wTarget[h] *= norm
    }

    override fun fill(buf: FloatArray, frames: Int) {
        for (i in 0 until frames) {
            if ((counter++ and 127) == 0) updateTargets()
            morphPh += 0.045f / SR
            if (morphPh >= 1f) morphPh -= 1f
            breathPh += 0.07f / SR
            if (breathPh >= 1f) breathPh -= 1f

            var l = 0f
            var r = 0f
            for (h in 0 until harmonics) {
                w[h] += (wTarget[h] - w[h]) * 0.0015f
                val n = (h + 1).toFloat()
                l += w[h] * Sine.at(phL * n)
                r += w[h] * Sine.at(phR * n)
            }
            val sub = 0.18f * Sine.at(subPh)
            val amp = 0.75f + 0.25f * Sine.at(breathPh)
            phL += incL
            if (phL >= 1f) phL -= 1f
            phR += incR
            if (phR >= 1f) phR -= 1f
            subPh += subInc
            if (subPh >= 1f) subPh -= 1f
            buf[2 * i] = (l + sub) * amp
            buf[2 * i + 1] = (r + sub) * amp
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Brown noise
// ---------------------------------------------------------------------------------------------

internal class BrownNoiseGenerator : SoundGenerator {
    private val rng = Rng()
    private var bL = 0f
    private var bR = 0f
    private val lpL = OnePoleLP(1500f)
    private val lpR = OnePoleLP(1500f)
    private val dcL = DcBlocker(0.998f)
    private val dcR = DcBlocker(0.998f)

    override fun fill(buf: FloatArray, frames: Int) {
        for (i in 0 until frames) {
            bL = (bL + rng.white() * 0.02f) * 0.998f
            bR = (bR + rng.white() * 0.02f) * 0.998f
            buf[2 * i] = lpL.process(dcL.process(bL * 2.6f))
            buf[2 * i + 1] = lpR.process(dcR.process(bR * 2.6f))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Wind chimes
// ---------------------------------------------------------------------------------------------

internal class ChimesGenerator : SoundGenerator {
    private val rng = Rng()
    private val voices = 12
    private val partials = 3
    private val notes = floatArrayOf(523.25f, 587.33f, 659.25f, 783.99f, 880f, 1046.5f)
    private val ratios = floatArrayOf(1f, 2.76f, 5.4f)
    private val amps = floatArrayOf(1f, 0.28f, 0.1f)
    private val tauMul = floatArrayOf(1f, 0.45f, 0.25f)

    private val active = BooleanArray(voices)
    private val atk = FloatArray(voices)
    private val panL = FloatArray(voices)
    private val panR = FloatArray(voices)
    private val ph = FloatArray(voices * partials)
    private val inc = FloatArray(voices * partials)
    private val env = FloatArray(voices * partials)
    private val dec = FloatArray(voices * partials)
    private val atkStep = 1f / (0.003f * SR)

    private val airL = OnePoleLP(500f)
    private val airR = OnePoleLP(500f)
    private var gustPh = rng.nextFloat()
    private var untilNext = (0.8f * SR).toInt()

    override fun fill(buf: FloatArray, frames: Int) {
        for (i in 0 until frames) {
            gustPh += 0.03f / SR
            if (gustPh >= 1f) gustPh -= 1f
            val gust = 0.5f - 0.5f * Sine.at(gustPh + 0.25f)
            val airAmp = (0.25f + 0.75f * gust) * 0.5f
            var l = airL.process(rng.white()) * airAmp
            var r = airR.process(rng.white()) * airAmp

            if (--untilNext <= 0) {
                strike()
                untilNext = (rng.range(0.3f, 1.6f) / (0.25f + gust) * SR).toInt()
            }

            for (v in 0 until voices) {
                if (!active[v]) continue
                val base = v * partials
                var s = 0f
                for (p in 0 until partials) {
                    val k = base + p
                    s += Sine.at(ph[k]) * env[k]
                    ph[k] += inc[k]
                    if (ph[k] >= 1f) ph[k] -= 1f
                    env[k] *= dec[k]
                }
                if (atk[v] < 1f) {
                    s *= atk[v]
                    atk[v] += atkStep
                    if (atk[v] > 1f) atk[v] = 1f
                }
                l += s * panL[v]
                r += s * panR[v]
                if (env[base] < 5e-4f) active[v] = false
            }
            buf[2 * i] = l
            buf[2 * i + 1] = r
        }
    }

    private fun strike() {
        var v = -1
        for (j in 0 until voices) if (!active[j]) { v = j; break }
        if (v < 0) {
            var minE = Float.MAX_VALUE
            for (j in 0 until voices) {
                val e = env[j * partials]
                if (e < minE) { minE = e; v = j }
            }
        }
        val f0 = notes[(rng.nextFloat() * notes.size).toInt().coerceIn(0, notes.size - 1)]
        val tau = rng.range(3f, 5f) / 3f
        val vol = rng.range(0.08f, 0.22f)
        val base = v * partials
        for (p in 0 until partials) {
            val k = base + p
            inc[k] = f0 * ratios[p] / SR
            ph[k] = 0f
            env[k] = amps[p] * vol
            dec[k] = decayPerSample(tau * tauMul[p])
        }
        val pan = rng.nextFloat()
        panL[v] = 1f - 0.7f * pan
        panR[v] = 0.3f + 0.7f * pan
        atk[v] = 0f
        active[v] = true
    }
}

// ---------------------------------------------------------------------------------------------
// Forest
// ---------------------------------------------------------------------------------------------

internal class ForestGenerator : SoundGenerator {
    private val rng = Rng()
    private val w1L = OnePoleLP(300f)
    private val w1R = OnePoleLP(300f)
    private val w2L = OnePoleLP(600f)
    private val w2R = OnePoleLP(600f)
    private val leafHiL = OnePoleLP(3000f)
    private val leafHiR = OnePoleLP(3000f)
    private val leafLoL = OnePoleLP(1000f)
    private val leafLoR = OnePoleLP(1000f)
    private var g1 = rng.nextFloat()
    private var g2 = rng.nextFloat()

    private val birds = 2
    private val state = IntArray(birds)          // 0 idle, 1 chirp, 2 gap
    private val counter = IntArray(birds)
    private val chirpsLeft = IntArray(birds)
    private val pos = FloatArray(birds)
    private val dur = FloatArray(birds)
    private val f1 = FloatArray(birds)
    private val f2 = FloatArray(birds)
    private val baseF = FloatArray(birds)
    private val up = BooleanArray(birds)
    private val phase = FloatArray(birds)
    private val vibPh = FloatArray(birds)
    private val amp = FloatArray(birds)
    private val bPanL = FloatArray(birds)
    private val bPanR = FloatArray(birds)

    init {
        counter[0] = (rng.range(0.8f, 3f) * SR).toInt()
        counter[1] = (rng.range(3f, 8f) * SR).toInt()
    }

    override fun fill(buf: FloatArray, frames: Int) {
        for (i in 0 until frames) {
            g1 += 0.04f / SR
            if (g1 >= 1f) g1 -= 1f
            g2 += 0.11f / SR
            if (g2 >= 1f) g2 -= 1f
            val gust = 0.35f + 0.65f * (0.5f - 0.5f * Sine.at(g1 + 0.25f)) * (0.7f + 0.3f * Sine.at(g2))

            val nl = rng.white()
            val nr = rng.white()
            var l = w2L.process(w1L.process(nl)) * 1.8f * gust
            var r = w2R.process(w1R.process(nr)) * 1.8f * gust
            val leaf = gust * gust * 0.12f
            l += (leafHiL.process(nl) - leafLoL.process(nl)) * leaf
            r += (leafHiR.process(nr) - leafLoR.process(nr)) * leaf

            for (b in 0 until birds) {
                when (state[b]) {
                    0 -> if (--counter[b] <= 0) startCall(b)
                    2 -> if (--counter[b] <= 0) startChirp(b)
                    else -> {
                        val x = pos[b] / dur[b]
                        val f = f1[b] + (f2[b] - f1[b]) * x
                        val vib = 1f + 0.025f * Sine.at(vibPh[b])
                        phase[b] += f * vib / SR
                        if (phase[b] >= 1f) phase[b] -= 1f
                        vibPh[b] += 32f / SR
                        if (vibPh[b] >= 1f) vibPh[b] -= 1f
                        val e = Sine.at(x * 0.5f)
                        val s = (Sine.at(phase[b]) + 0.12f * Sine.at(phase[b] * 2f)) * e * e * amp[b]
                        l += s * bPanL[b]
                        r += s * bPanR[b]
                        pos[b] += 1f
                        if (pos[b] >= dur[b]) {
                            chirpsLeft[b]--
                            if (chirpsLeft[b] > 0) {
                                state[b] = 2
                                counter[b] = (rng.range(0.04f, 0.12f) * SR).toInt()
                            } else {
                                state[b] = 0
                                counter[b] = (rng.range(if (b == 0) 1.5f else 3f, if (b == 0) 6f else 9f) * SR).toInt()
                            }
                        }
                    }
                }
            }
            buf[2 * i] = l
            buf[2 * i + 1] = r
        }
    }

    private fun startCall(b: Int) {
        chirpsLeft[b] = 1 + (rng.nextFloat() * 5f).toInt()
        baseF[b] = rng.range(2200f, 4300f)
        up[b] = rng.nextFloat() < 0.5f
        amp[b] = rng.range(0.05f, 0.13f)
        val p = rng.nextFloat()
        bPanL[b] = 1f - 0.8f * p
        bPanR[b] = 0.2f + 0.8f * p
        startChirp(b)
    }

    private fun startChirp(b: Int) {
        val a = baseF[b] * rng.range(0.92f, 1.1f)
        val z = a * (if (up[b]) rng.range(1.1f, 1.45f) else rng.range(0.65f, 0.9f))
        f1[b] = a.coerceIn(1900f, 5200f)
        f2[b] = z.coerceIn(1900f, 5200f)
        dur[b] = rng.range(0.05f, 0.13f) * SR
        pos[b] = 0f
        state[b] = 1
    }
}

// ---------------------------------------------------------------------------------------------
// Binaural "deep calm hum"
// ---------------------------------------------------------------------------------------------

internal class BinauralGenerator : SoundGenerator {
    private val rng = Rng()
    private var pL = 0f
    private var pR = 0f
    private var sL = 0f
    private var sR = 0f
    private var breath = 0f
    private var bed = 0f
    private val bedLp = OnePoleLP(400f)
    private val incL = 200f / SR
    private val incR = 206f / SR
    private val subIncL = 100f / SR
    private val subIncR = 103f / SR

    override fun fill(buf: FloatArray, frames: Int) {
        for (i in 0 until frames) {
            breath += 0.05f / SR
            if (breath >= 1f) breath -= 1f
            val amp = 0.85f + 0.15f * Sine.at(breath)
            bed = (bed + rng.white() * 0.02f) * 0.998f
            val noise = bedLp.process(bed) * 0.25f
            val l = (0.55f * Sine.at(pL) + 0.25f * Sine.at(sL)) * amp + noise
            val r = (0.55f * Sine.at(pR) + 0.25f * Sine.at(sR)) * amp + noise
            pL += incL
            if (pL >= 1f) pL -= 1f
            pR += incR
            if (pR >= 1f) pR -= 1f
            sL += subIncL
            if (sL >= 1f) sL -= 1f
            sR += subIncR
            if (sR >= 1f) sR -= 1f
            buf[2 * i] = l
            buf[2 * i + 1] = r
        }
    }
}

// ---------------------------------------------------------------------------------------------
// End bell (pre-rendered)
// ---------------------------------------------------------------------------------------------

internal object BellSynth {
    const val DURATION_SEC = 7.5f

    /** Renders three soft bowl strikes as interleaved stereo 16-bit PCM. */
    fun render(): ShortArray {
        val frames = (SR * DURATION_SEC).toInt()
        val out = FloatArray(frames)
        val strikes = floatArrayOf(0f, 2.2f, 4.4f)
        val strengths = floatArrayOf(1f, 0.85f, 0.7f)
        val ratios = floatArrayOf(1f, 2.76f, 5.4f, 8.9f)
        val amps = floatArrayOf(1f, 0.45f, 0.2f, 0.08f)
        val taus = floatArrayOf(2.6f, 1.6f, 0.9f, 0.5f)
        val beats = floatArrayOf(1.2f, 1.8f, 2.5f, 3.2f)
        val f0 = 220f
        val atkSamples = 0.008f * SR
        for (s in strikes.indices) {
            val start = (strikes[s] * SR).toInt()
            for (p in ratios.indices) {
                for (d in 0 until 2) {
                    val f = f0 * ratios[p] + (if (d == 0) -0.5f else 0.5f) * beats[p]
                    val inc = f / SR
                    var phase = 0f
                    var e = amps[p] * 0.5f * strengths[s]
                    val dec = decayPerSample(taus[p])
                    var i = start
                    while (i < frames) {
                        val t = (i - start).toFloat()
                        val atk = if (t < atkSamples) t / atkSamples else 1f
                        out[i] += e * atk * Sine.at(phase)
                        phase += inc
                        if (phase >= 1f) phase -= 1f
                        e *= dec
                        if (e < 1e-5f) break
                        i++
                    }
                }
            }
        }
        // Gentle tail fade over the last 0.6 s.
        val fadeLen = (0.6f * SR).toInt()
        for (k in 0 until fadeLen) {
            val idx = frames - fadeLen + k
            out[idx] *= 1f - k.toFloat() / fadeLen
        }
        var peak = 1e-6f
        for (x in out) { val a = if (x < 0) -x else x; if (a > peak) peak = a }
        val gain = 0.42f / peak
        val pcm = ShortArray(frames * 2)
        for (i in 0 until frames) {
            val v = toPcm16(out[i] * gain)
            pcm[2 * i] = v
            pcm[2 * i + 1] = v
        }
        return pcm
    }
}
