package com.refluxcare.app.meditation

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MeditationState(
    val active: Boolean = false,      // a session exists (running or paused)
    val paused: Boolean = false,
    val totalSec: Int = 0,
    val remainingSec: Int = 0,
    val soundId: String = MeditationSounds.SILENCE,
    val soundName: String = "Silence",
    val startedAt: Long = 0L
)

data class MeditationResult(
    val startedAt: Long,
    val plannedMin: Int,
    val actualSec: Int,
    val soundName: String,
    val completed: Boolean
)

object MeditationController {
    private const val TAG = "MeditationController"
    private const val MAX_MINUTES = 600
    private const val PREVIEW_MS = 15_000L
    private const val PREVIEW_FADE_IN_MS = 800L
    private const val PREVIEW_END_FADE_MS = 1_200L
    private const val PREVIEW_STOP_FADE_MS = 300L

    /** Minimum played seconds for a session to be reported via [onSessionEnded]. */
    internal const val MIN_LOGGED_SEC = 30

    private val _state = MutableStateFlow(MeditationState())
    val state: StateFlow<MeditationState> = _state.asStateFlow()

    private val _previewingId = MutableStateFlow<String?>(null)
    val previewingId: StateFlow<String?> = _previewingId.asStateFlow()

    /** Called on the main thread once per session that ran for at least 30 s. */
    @Volatile
    var onSessionEnded: ((MeditationResult) -> Unit)? = null

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    // Preview state (main thread only).
    private var previewPlayer: AmbientPlayer? = null
    private var previewEnd: Runnable? = null

    fun start(context: Context, minutes: Int, soundId: String, endBell: Boolean = true) {
        val app = context.applicationContext
        stopPreview()
        val mins = minutes.coerceIn(1, MAX_MINUTES)
        val name = MeditationSounds.find(app, soundId)?.name
            ?: if (soundId.startsWith(MeditationSounds.CUSTOM_PREFIX)) "Your music" else "Silence"
        _state.value = MeditationState(
            active = true,
            paused = false,
            totalSec = mins * 60,
            remainingSec = mins * 60,
            soundId = soundId,
            soundName = name,
            startedAt = System.currentTimeMillis()
        )
        val intent = Intent(app, MeditationService::class.java)
            .setAction(MeditationService.ACTION_START)
            .putExtra(MeditationService.EXTRA_MINUTES, mins)
            .putExtra(MeditationService.EXTRA_SOUND_ID, soundId)
            .putExtra(MeditationService.EXTRA_END_BELL, endBell)
        try {
            ContextCompat.startForegroundService(app, intent)
        } catch (e: Exception) {
            Log.e(TAG, "Could not start meditation service", e)
            _state.value = MeditationState()
        }
    }

    fun pause(context: Context) {
        val s = _state.value
        if (!s.active || s.paused) return
        _state.value = s.copy(paused = true)
        send(context, MeditationService.ACTION_PAUSE)
    }

    fun resume(context: Context) {
        val s = _state.value
        if (!s.active || !s.paused) return
        _state.value = s.copy(paused = false)
        send(context, MeditationService.ACTION_RESUME)
    }

    /** Ends the session early (result completed = false). */
    fun stop(context: Context) {
        if (!_state.value.active) return
        send(context, MeditationService.ACTION_STOP)
        _state.value = MeditationState()
    }

    /** Plays a ~15 s preview; calling again with the same id stops it. No-op during a session. */
    fun preview(context: Context, soundId: String) {
        val app = context.applicationContext
        runOnMain { previewInternal(app, soundId) }
    }

    fun stopPreview() {
        runOnMain { stopPreviewInternal(PREVIEW_STOP_FADE_MS) }
    }

    // --- internal hooks for MeditationService -------------------------------------------------

    internal fun publish(state: MeditationState) {
        _state.value = state
    }

    internal fun dispatchSessionEnded(result: MeditationResult) {
        if (result.actualSec < MIN_LOGGED_SEC) return
        val cb = onSessionEnded ?: return
        runOnMain {
            try {
                cb(result)
            } catch (t: Throwable) {
                Log.e(TAG, "onSessionEnded callback failed", t)
            }
        }
    }

    // --- preview ----------------------------------------------------------------------------

    private fun previewInternal(app: Context, soundId: String) {
        if (_state.value.active) return
        if (_previewingId.value == soundId) {
            stopPreviewInternal(PREVIEW_STOP_FADE_MS)
            return
        }
        stopPreviewInternal(PREVIEW_STOP_FADE_MS)
        val player = AmbientPlayers.create(app, soundId) ?: return
        previewPlayer = player
        _previewingId.value = soundId
        player.start(PREVIEW_FADE_IN_MS)
        val end = Runnable {
            if (previewPlayer === player) stopPreviewInternal(PREVIEW_END_FADE_MS)
        }
        previewEnd = end
        mainHandler.postDelayed(end, PREVIEW_MS)
    }

    private fun stopPreviewInternal(fadeMs: Long) {
        previewEnd?.let { mainHandler.removeCallbacks(it) }
        previewEnd = null
        val p = previewPlayer
        previewPlayer = null
        _previewingId.value = null
        if (p != null) {
            p.fadeTo(0f, fadeMs)
            mainHandler.postDelayed({ p.release() }, fadeMs + 100L)
        }
    }

    private fun send(context: Context, action: String) {
        val app = context.applicationContext
        try {
            app.startService(Intent(app, MeditationService::class.java).setAction(action))
        } catch (e: Exception) {
            // e.g. background start restrictions when the service is no longer running.
            Log.w(TAG, "Could not send $action to meditation service", e)
            if (action == MeditationService.ACTION_STOP) _state.value = MeditationState()
        }
    }

    private inline fun runOnMain(crossinline block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post { block() }
    }
}
