package com.refluxcare.app.meditation

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.max
import kotlin.math.min

private const val TAG = "MeditationAudio"

internal val meditationAudioAttributes: AudioAttributes
    get() = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

internal fun buildStereoTrack(bufferBytes: Int): AudioTrack =
    AudioTrack.Builder()
        .setAudioAttributes(meditationAudioAttributes)
        .setAudioFormat(
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build()
        )
        .setTransferMode(AudioTrack.MODE_STREAM)
        .setBufferSizeInBytes(bufferBytes)
        .build()

internal fun minStereoBufferBytes(): Int {
    val min = AudioTrack.getMinBufferSize(
        SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT
    )
    return if (min > 0) min else SAMPLE_RATE / 10 * 4
}

/** Ambient background sound for a session or preview. Call all methods from the main thread. */
internal interface AmbientPlayer {
    /** Start playback, fading in over [fadeInMs]. */
    fun start(fadeInMs: Long)

    /** Ramp the (unpaused) level to [level] (0..1) over [fadeMs]. */
    fun fadeTo(level: Float, fadeMs: Long)

    /** Fade out and pause, or resume and fade back in to the current level. */
    fun setPaused(paused: Boolean, fadeMs: Long)

    /** Stop immediately and free resources (non-blocking on the main thread). */
    fun release()
}

internal object AmbientPlayers {
    /** Returns null for silence / unknown ids. */
    fun create(context: Context, soundId: String): AmbientPlayer? {
        if (soundId.startsWith(MeditationSounds.CUSTOM_PREFIX)) {
            val uriString = soundId.removePrefix(MeditationSounds.CUSTOM_PREFIX)
            if (uriString.isBlank()) return null
            return CustomTrackPlayer(context, Uri.parse(uriString))
        }
        val gen = SoundGenerators.create(soundId) ?: return null
        return SynthPlayer(gen)
    }
}

// ---------------------------------------------------------------------------------------------
// Real-time synthesized playback via AudioTrack (MODE_STREAM) on a dedicated thread.
// ---------------------------------------------------------------------------------------------

internal class SynthPlayer(
    private val generator: SoundGenerator,
    private val masterVolume: Float = MASTER_VOLUME
) : AmbientPlayer {

    @Volatile private var running = false
    @Volatile private var pauseRequested = false
    @Volatile private var targetGain = 0f
    @Volatile private var gainStep = 1f
    private val lock = ReentrantLock()
    private val resumed = lock.newCondition()
    private var thread: Thread? = null

    override fun start(fadeInMs: Long) {
        if (thread != null) return
        running = true
        fadeTo(1f, fadeInMs)
        thread = Thread({ runLoop() }, "meditation-synth").also { it.start() }
    }

    override fun fadeTo(level: Float, fadeMs: Long) {
        gainStep = stepFor(fadeMs)
        targetGain = level.coerceIn(0f, 1f)
    }

    override fun setPaused(paused: Boolean, fadeMs: Long) {
        gainStep = stepFor(fadeMs)
        pauseRequested = paused
        if (!paused) lock.withLock { resumed.signalAll() }
    }

    override fun release() {
        running = false
        lock.withLock { resumed.signalAll() }
        val t = thread ?: return
        thread = null
        // Never block the main thread; elsewhere wait briefly for a clean shutdown.
        if (Looper.myLooper() != Looper.getMainLooper()) {
            try {
                t.join(500)
            } catch (ignored: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    private fun stepFor(fadeMs: Long): Float {
        val samples = max(1L, fadeMs * SAMPLE_RATE / 1000L)
        return 1f / samples.toFloat()
    }

    private fun runLoop() {
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        } catch (ignored: Throwable) {
        }
        var track: AudioTrack? = null
        try {
            val frames = SAMPLE_RATE * BUFFER_MS / 1000
            val bufBytes = max(minStereoBufferBytes(), frames * 4 * 2)
            val t = buildStereoTrack(bufBytes)
            track = t
            val fbuf = FloatArray(frames * 2)
            val sbuf = ShortArray(frames * 2)
            var gain = 0f
            t.play()
            while (running) {
                if (pauseRequested && gain <= 0f) {
                    t.pause()
                    lock.withLock {
                        while (running && pauseRequested) {
                            resumed.await(250, TimeUnit.MILLISECONDS)
                        }
                    }
                    if (!running) break
                    t.play()
                    continue
                }
                generator.fill(fbuf, frames)
                val target = if (pauseRequested) 0f else targetGain
                val step = gainStep
                for (i in 0 until frames) {
                    if (gain < target) {
                        gain += step
                        if (gain > target) gain = target
                    } else if (gain > target) {
                        gain -= step
                        if (gain < target) gain = target
                    }
                    val g = gain * gain * masterVolume
                    sbuf[2 * i] = toPcm16(fbuf[2 * i] * g)
                    sbuf[2 * i + 1] = toPcm16(fbuf[2 * i + 1] * g)
                }
                var off = 0
                while (off < sbuf.size && running) {
                    val w = t.write(sbuf, off, sbuf.size - off)
                    if (w <= 0) break
                    off += w
                }
            }
        } catch (ignored: InterruptedException) {
        } catch (t: Throwable) {
            Log.w(TAG, "Synth playback failed", t)
        } finally {
            try {
                track?.pause()
                track?.flush()
            } catch (ignored: Throwable) {
            }
            try {
                track?.stop()
            } catch (ignored: Throwable) {
            }
            try {
                track?.release()
            } catch (ignored: Throwable) {
            }
        }
    }

    private companion object {
        const val BUFFER_MS = 80
    }
}

// ---------------------------------------------------------------------------------------------
// User-provided music via MediaPlayer, with volume ramps on the main looper.
// ---------------------------------------------------------------------------------------------

internal class CustomTrackPlayer(context: Context, private val uri: Uri) : AmbientPlayer {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var prepared = false
    private var released = false
    private var paused = false
    private var current = 0f
    private var target = 1f
    private var step = 0.05f
    private var rampScheduled = false

    private val ramp = object : Runnable {
        override fun run() {
            rampScheduled = false
            if (released) return
            val goal = if (paused) 0f else target
            current = if (current < goal) min(goal, current + step) else max(goal, current - step)
            val mp = player
            if (mp != null && prepared) {
                try {
                    val v = current * current * CUSTOM_VOLUME
                    mp.setVolume(v, v)
                    if (paused && current <= 0f && mp.isPlaying) mp.pause()
                } catch (e: IllegalStateException) {
                    Log.w(TAG, "MediaPlayer ramp failed", e)
                }
            }
            if (current != goal) scheduleRamp()
        }
    }

    private fun scheduleRamp() {
        if (rampScheduled || released) return
        rampScheduled = true
        handler.postDelayed(ramp, TICK_MS)
    }

    override fun start(fadeInMs: Long) {
        if (player != null || released) return
        val mp = MediaPlayer()
        player = mp
        try {
            mp.setAudioAttributes(meditationAudioAttributes)
            mp.setDataSource(appContext, uri)
            mp.isLooping = true
            mp.setVolume(0f, 0f)
            mp.setOnPreparedListener { p ->
                if (released) return@setOnPreparedListener
                prepared = true
                try {
                    if (!paused) p.start()
                } catch (e: IllegalStateException) {
                    Log.w(TAG, "MediaPlayer start failed", e)
                }
                scheduleRamp()
            }
            mp.setOnErrorListener { _, what, extra ->
                Log.w(TAG, "MediaPlayer error what=$what extra=$extra")
                true
            }
            mp.prepareAsync()
        } catch (e: Exception) {
            Log.w(TAG, "Cannot play custom track $uri", e)
            releasePlayer()
        }
        fadeTo(1f, fadeInMs)
    }

    override fun fadeTo(level: Float, fadeMs: Long) {
        target = level.coerceIn(0f, 1f)
        step = TICK_MS.toFloat() / max(fadeMs, TICK_MS).toFloat()
        scheduleRamp()
    }

    override fun setPaused(paused: Boolean, fadeMs: Long) {
        this.paused = paused
        step = TICK_MS.toFloat() / max(fadeMs, TICK_MS).toFloat()
        if (!paused) {
            val mp = player
            if (mp != null && prepared) {
                try {
                    if (!mp.isPlaying) mp.start()
                } catch (e: IllegalStateException) {
                    Log.w(TAG, "MediaPlayer resume failed", e)
                }
            }
        }
        scheduleRamp()
    }

    override fun release() {
        if (released) return
        released = true
        handler.removeCallbacks(ramp)
        releasePlayer()
    }

    private fun releasePlayer() {
        val mp = player ?: return
        player = null
        prepared = false
        try {
            mp.stop()
        } catch (ignored: Throwable) {
        }
        try {
            mp.release()
        } catch (ignored: Throwable) {
        }
    }

    private companion object {
        const val TICK_MS = 50L
        const val CUSTOM_VOLUME = 0.8f
    }
}

// ---------------------------------------------------------------------------------------------
// End-of-session bell (3 soft strikes) on its own short-lived thread.
// ---------------------------------------------------------------------------------------------

internal class BellPlayer {
    @Volatile private var cancelled = false
    private var thread: Thread? = null

    /** Plays the bell; [onDone] is invoked on the bell thread when finished or cancelled. */
    fun play(onDone: () -> Unit) {
        if (thread != null) return
        thread = Thread({ run(onDone) }, "meditation-bell").also { it.start() }
    }

    fun cancel() {
        cancelled = true
    }

    private fun run(onDone: () -> Unit) {
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        } catch (ignored: Throwable) {
        }
        var track: AudioTrack? = null
        try {
            val pcm = BellSynth.render()
            if (cancelled) return
            val t = buildStereoTrack(max(minStereoBufferBytes(), SAMPLE_RATE / 5 * 4))
            track = t
            t.play()
            val chunk = 4096
            var off = 0
            while (off < pcm.size && !cancelled) {
                val n = t.write(pcm, off, min(chunk, pcm.size - off))
                if (n <= 0) break
                off += n
            }
            if (!cancelled) {
                // Pad with silence so everything audible has played before we stop/release.
                val zeros = ShortArray(chunk)
                var padded = 0
                val padTotal = SAMPLE_RATE / 2 * 2 // 0.5 s stereo
                while (padded < padTotal && !cancelled) {
                    val n = t.write(zeros, 0, zeros.size)
                    if (n <= 0) break
                    padded += n
                }
                t.stop()
                Thread.sleep(150)
            }
        } catch (ignored: InterruptedException) {
        } catch (t: Throwable) {
            Log.w(TAG, "Bell playback failed", t)
        } finally {
            try {
                track?.pause()
            } catch (ignored: Throwable) {
            }
            try {
                track?.release()
            } catch (ignored: Throwable) {
            }
            try {
                onDone()
            } catch (t: Throwable) {
                Log.w(TAG, "Bell callback failed", t)
            }
        }
    }
}
