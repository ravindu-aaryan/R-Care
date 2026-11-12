package com.refluxcare.app.meditation

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.refluxcare.app.R

/**
 * Foreground service that owns the meditation timer and audio so a session keeps running
 * with the screen off or the app in the background. Driven by [MeditationController].
 * All work happens on the main thread except audio rendering (dedicated threads).
 */
class MeditationService : Service() {

    companion object {
        internal const val ACTION_START = "com.refluxcare.app.meditation.action.START"
        internal const val ACTION_PAUSE = "com.refluxcare.app.meditation.action.PAUSE"
        internal const val ACTION_RESUME = "com.refluxcare.app.meditation.action.RESUME"
        internal const val ACTION_STOP = "com.refluxcare.app.meditation.action.STOP"
        internal const val EXTRA_MINUTES = "minutes"
        internal const val EXTRA_SOUND_ID = "soundId"
        internal const val EXTRA_END_BELL = "endBell"

        private const val TAG = "MeditationService"
        private const val CHANNEL_ID = "meditation"
        private const val NOTIFICATION_ID = 4201
        private const val START_FADE_MS = 3_000L
        private const val END_FADE_MS = 8_000L
        private const val PAUSE_FADE_MS = 1_500L
        private const val STOP_FADE_MS = 1_500L
        private const val BELL_WAKE_MS = 20_000L
        private const val WAKE_MARGIN_MS = 60_000L
    }

    private class Session(
        val totalSec: Int,
        val soundId: String,
        val soundName: String,
        val endBell: Boolean,
        val startedAt: Long
    ) {
        var accumulatedMs = 0L
        var runStart = 0L
        var paused = false
        var pausedByFocus = false
        var fadingOut = false
    }

    private val handler = Handler(Looper.getMainLooper())
    private var session: Session? = null
    private var player: AmbientPlayer? = null
    private val retiring = mutableSetOf<AmbientPlayer>()
    private var bell: BellPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var focusRequest: AudioFocusRequest? = null
    private var isForeground = false
    private var contentIntent: PendingIntent? = null

    private val audioManager: AudioManager? by lazy {
        getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    private val tick = Runnable { onTick() }

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                session?.pausedByFocus = false
                pauseSession(transientFocusLoss = false)
                abandonFocus()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pauseSession(transientFocusLoss = true)
            AudioManager.AUDIOFOCUS_GAIN -> {
                val s = session
                if (s != null && s.paused && s.pausedByFocus) resumeSession()
            }
            else -> Unit // AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK: let the system duck us.
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_PAUSE -> pauseSession(transientFocusLoss = false)
            ACTION_RESUME -> resumeSession()
            ACTION_STOP -> stopSession()
        }
        if (session == null && bell == null && retiring.isEmpty()) {
            // Nothing to do (e.g. stale command after process death): make sure the UI is reset.
            if (MeditationController.state.value.active) MeditationController.publish(MeditationState())
            finishService()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        session?.let { s ->
            session = null
            deliverResult(s, (elapsedMs(s) / 1000L).toInt(), completed = false)
            MeditationController.publish(MeditationState())
        }
        player?.release()
        player = null
        retiring.forEach { it.release() }
        retiring.clear()
        bell?.cancel()
        bell = null
        abandonFocus()
        releaseWakeLock()
        super.onDestroy()
    }

    // --- commands -------------------------------------------------------------------------------

    private fun handleStart(intent: Intent) {
        val minutes = intent.getIntExtra(EXTRA_MINUTES, 10).coerceIn(1, 600)
        val soundId = intent.getStringExtra(EXTRA_SOUND_ID) ?: MeditationSounds.SILENCE
        val endBell = intent.getBooleanExtra(EXTRA_END_BELL, true)

        // Replace any running session (counts as ended early) and any ringing bell.
        session?.let { old ->
            session = null
            handler.removeCallbacks(tick)
            deliverResult(old, (elapsedMs(old) / 1000L).toInt(), completed = false)
        }
        player?.let { retire(it, 300L) }
        player = null
        bell?.cancel()
        bell = null

        val sound = MeditationSounds.find(this, soundId)
        val name = sound?.name
            ?: if (soundId.startsWith(MeditationSounds.CUSTOM_PREFIX)) "Your music" else "Silence"
        val s = Session(
            totalSec = minutes * 60,
            soundId = soundId,
            soundName = name,
            endBell = endBell,
            startedAt = System.currentTimeMillis()
        )
        s.runStart = SystemClock.elapsedRealtime()
        session = s

        promoteToForeground()
        acquireWakeLock(s.totalSec * 1000L + WAKE_MARGIN_MS)

        player = AmbientPlayers.create(this, soundId)?.also {
            requestFocus()
            it.start(START_FADE_MS)
        }
        publishState()
        scheduleTick()
    }

    private fun pauseSession(transientFocusLoss: Boolean) {
        val s = session ?: return
        if (s.paused) return
        s.accumulatedMs += SystemClock.elapsedRealtime() - s.runStart
        s.paused = true
        s.pausedByFocus = transientFocusLoss
        handler.removeCallbacks(tick)
        player?.setPaused(true, PAUSE_FADE_MS)
        if (!transientFocusLoss) abandonFocus()
        releaseWakeLock()
        publishState()
        updateNotification()
    }

    private fun resumeSession() {
        val s = session ?: return
        if (!s.paused) return
        s.paused = false
        s.pausedByFocus = false
        s.runStart = SystemClock.elapsedRealtime()
        val remainingMs = s.totalSec * 1000L - elapsedMs(s)
        acquireWakeLock(remainingMs.coerceAtLeast(0L) + WAKE_MARGIN_MS)
        player?.let {
            requestFocus()
            it.setPaused(false, PAUSE_FADE_MS)
            if (remainingMs <= END_FADE_MS) {
                s.fadingOut = true
                it.fadeTo(0f, remainingMs.coerceAtLeast(300L))
            }
        }
        publishState()
        updateNotification()
        scheduleTick()
    }

    /** Ends the session early with a short fade. */
    private fun stopSession() {
        val s = session
        if (s == null) {
            // A Stop while only the bell is ringing silences it.
            bell?.cancel()
            bell = null
            return
        }
        handler.removeCallbacks(tick)
        val played = (elapsedMs(s) / 1000L).toInt()
        session = null
        player?.let {
            it.fadeTo(0f, STOP_FADE_MS)
            retire(it, STOP_FADE_MS + 100L)
        }
        player = null
        abandonFocus()
        releaseWakeLock()
        deliverResult(s, played, completed = false)
        MeditationController.publish(MeditationState())
        bell?.cancel()
        bell = null
        handler.postDelayed({
            if (session == null && bell == null) finishService()
        }, STOP_FADE_MS + 200L)
    }

    private fun completeSession() {
        val s = session ?: return
        handler.removeCallbacks(tick)
        session = null
        player?.release() // already faded to silence during the last seconds
        player = null
        abandonFocus()
        deliverResult(s, s.totalSec, completed = true)
        MeditationController.publish(MeditationState())

        if (s.endBell) {
            acquireWakeLock(BELL_WAKE_MS)
            updateNotification()
            val b = BellPlayer()
            bell = b
            b.play {
                handler.post {
                    if (bell === b) bell = null
                    if (session == null && bell == null) finishService()
                }
            }
        } else {
            finishService()
        }
    }

    // --- timer --------------------------------------------------------------------------------

    private fun elapsedMs(s: Session): Long =
        s.accumulatedMs + if (s.paused) 0L else SystemClock.elapsedRealtime() - s.runStart

    private fun remainingMs(s: Session): Long = s.totalSec * 1000L - elapsedMs(s)

    private fun scheduleTick() {
        handler.removeCallbacks(tick)
        val s = session ?: return
        if (s.paused) return
        val delay = (1000L - elapsedMs(s) % 1000L).coerceIn(20L, 1000L)
        handler.postDelayed(tick, delay)
    }

    private fun onTick() {
        val s = session ?: return
        if (s.paused) return
        val remaining = remainingMs(s)
        if (remaining <= 0L) {
            completeSession()
            return
        }
        if (!s.fadingOut && remaining <= END_FADE_MS + 200L) {
            s.fadingOut = true
            player?.fadeTo(0f, remaining.coerceAtLeast(300L))
        }
        publishState()
        updateNotification()
        scheduleTick()
    }

    private fun remainingSec(s: Session): Int =
        ((remainingMs(s).coerceAtLeast(0L) + 500L) / 1000L).toInt().coerceAtMost(s.totalSec)

    private fun publishState() {
        val s = session ?: return
        MeditationController.publish(
            MeditationState(
                active = true,
                paused = s.paused,
                totalSec = s.totalSec,
                remainingSec = remainingSec(s),
                soundId = s.soundId,
                soundName = s.soundName,
                startedAt = s.startedAt
            )
        )
    }

    private fun deliverResult(s: Session, actualSec: Int, completed: Boolean) {
        MeditationController.dispatchSessionEnded(
            MeditationResult(
                startedAt = s.startedAt,
                plannedMin = s.totalSec / 60,
                actualSec = actualSec.coerceIn(0, s.totalSec),
                soundName = s.soundName,
                completed = completed
            )
        )
    }

    private fun retire(p: AmbientPlayer, delayMs: Long) {
        retiring.add(p)
        handler.postDelayed({
            if (retiring.remove(p)) p.release()
        }, delayMs)
    }

    private fun finishService() {
        handler.removeCallbacks(tick)
        releaseWakeLock()
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            Log.w(TAG, "stopForeground failed", e)
        }
        isForeground = false
        stopSelf()
    }

    // --- wake lock & audio focus --------------------------------------------------------------

    private fun acquireWakeLock(timeoutMs: Long) {
        try {
            val wl = wakeLock ?: run {
                val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
                pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RefluxCare:meditation").also {
                    it.setReferenceCounted(false)
                    wakeLock = it
                }
            }
            wl.acquire(timeoutMs.coerceAtLeast(1_000L))
        } catch (e: Exception) {
            Log.w(TAG, "Wake lock acquire failed", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
        } catch (e: Exception) {
            Log.w(TAG, "Wake lock release failed", e)
        }
    }

    private fun requestFocus() {
        if (focusRequest != null) return
        val am = audioManager ?: return
        try {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(meditationAudioAttributes)
                .setOnAudioFocusChangeListener(focusListener, handler)
                .build()
            am.requestAudioFocus(req)
            // Even if not granted we keep playing quietly; keep the request to receive changes.
            focusRequest = req
        } catch (e: Exception) {
            Log.w(TAG, "Audio focus request failed", e)
        }
    }

    private fun abandonFocus() {
        val req = focusRequest ?: return
        focusRequest = null
        try {
            audioManager?.abandonAudioFocusRequest(req)
        } catch (e: Exception) {
            Log.w(TAG, "Audio focus abandon failed", e)
        }
    }

    // --- notification -------------------------------------------------------------------------

    private fun promoteToForeground() {
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
            isForeground = true
        } catch (e: Exception) {
            // e.g. ForegroundServiceStartNotAllowedException; keep running as a started service.
            Log.w(TAG, "startForeground failed", e)
        }
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(CHANNEL_ID, "Meditation", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shows the meditation timer while a session is running"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, MeditationService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun launchPendingIntent(): PendingIntent? {
        contentIntent?.let { return it }
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return PendingIntent.getActivity(
            this, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        ).also { contentIntent = it }
    }

    private fun buildNotification(): Notification {
        ensureChannel()
        val b = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_meditation)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        launchPendingIntent()?.let { b.setContentIntent(it) }

        val s = session
        if (s != null) {
            val remaining = remainingSec(s)
            val elapsed = (s.totalSec - remaining).coerceIn(0, s.totalSec)
            b.setContentTitle("Meditating · ${s.soundName}")
            b.setContentText(
                if (s.paused) "Paused · ${formatTime(remaining)} left" else "${formatTime(remaining)} remaining"
            )
            b.setProgress(s.totalSec, elapsed, false)
            if (s.paused) {
                b.addAction(android.R.drawable.ic_media_play, "Resume", servicePendingIntent(ACTION_RESUME, 2))
            } else {
                b.addAction(android.R.drawable.ic_media_pause, "Pause", servicePendingIntent(ACTION_PAUSE, 1))
            }
            b.addAction(
                android.R.drawable.ic_menu_close_clear_cancel, "Stop", servicePendingIntent(ACTION_STOP, 3)
            )
        } else {
            b.setContentTitle("Meditation complete")
            b.setContentText("Well done. Take a slow breath.")
        }
        return b.build()
    }

    @SuppressLint("MissingPermission")
    private fun updateNotification() {
        if (!isForeground) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        try {
            getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, buildNotification())
        } catch (e: Exception) {
            Log.w(TAG, "Notification update failed", e)
        }
    }

    private fun formatTime(totalSec: Int): String {
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val sec = totalSec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
    }
}
