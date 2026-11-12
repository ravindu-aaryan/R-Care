package com.refluxcare.app.domain

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import com.refluxcare.app.util.T
import java.time.LocalDate

/**
 * Detects sleep from the phone's screen on/off history (UsageStatsManager).
 *
 * For a wake-up day D we look at the window D-1 19:00 → D 13:00, collect every period
 * the screen was off, merge periods separated by short checks (≤ 10 min, e.g. looking at
 * the time at night) and take the longest one of at least 3 hours as the night's sleep.
 */
object SleepTracker {

    private const val MERGE_GAP_MS = 10 * 60 * 1000L
    private const val MIN_SLEEP_MS = 3 * 60 * 60 * 1000L

    fun hasPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun permissionIntent(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Returns (sleepStart, wakeTime) in epoch millis, or null if nothing sleep-like was found. */
    fun detect(context: Context, wakeDay: LocalDate): Pair<Long, Long>? {
        if (!hasPermission(context)) return null
        val windowStart = T.millis(wakeDay.minusDays(1), 19, 0)
        val windowEnd = minOf(T.millis(wakeDay, 13, 0), System.currentTimeMillis())
        if (windowEnd <= windowStart) return null

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = usm.queryEvents(windowStart, windowEnd) ?: return null
        val ev = UsageEvents.Event()

        val offPeriods = mutableListOf<LongArray>()
        var offStart: Long? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            when (ev.eventType) {
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    if (offStart == null) offStart = ev.timeStamp
                }
                UsageEvents.Event.SCREEN_INTERACTIVE -> {
                    val s = offStart
                    if (s != null) offPeriods.add(longArrayOf(s, ev.timeStamp))
                    offStart = null
                }
            }
        }
        // Screen still off at the end of the window (only counts if the window already closed)
        val lastOff = offStart
        if (lastOff != null && windowEnd < System.currentTimeMillis()) {
            offPeriods.add(longArrayOf(lastOff, windowEnd))
        }
        if (offPeriods.isEmpty()) return null

        offPeriods.sortBy { it[0] }
        val merged = mutableListOf<LongArray>()
        for (p in offPeriods) {
            val last = merged.lastOrNull()
            if (last != null && p[0] - last[1] <= MERGE_GAP_MS) {
                last[1] = maxOf(last[1], p[1])
            } else {
                merged.add(longArrayOf(p[0], p[1]))
            }
        }

        val best = merged.maxByOrNull { it[1] - it[0] } ?: return null
        if (best[1] - best[0] < MIN_SLEEP_MS) return null
        return best[0] to best[1]
    }
}
