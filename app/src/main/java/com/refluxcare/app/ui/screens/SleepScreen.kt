package com.refluxcare.app.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.refluxcare.app.data.SleepRecord
import com.refluxcare.app.domain.Content
import com.refluxcare.app.domain.SleepTracker
import com.refluxcare.app.ui.BarChart
import com.refluxcare.app.ui.Bullet
import com.refluxcare.app.ui.Glass
import com.refluxcare.app.ui.GlassCard
import com.refluxcare.app.ui.GradientButton
import com.refluxcare.app.ui.IconBadge
import com.refluxcare.app.ui.Page
import com.refluxcare.app.ui.Pill
import com.refluxcare.app.ui.Stat
import com.refluxcare.app.ui.pickTime
import com.refluxcare.app.ui.repo
import com.refluxcare.app.util.T
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Asks for bedtime then wake time. Bedtimes from noon onward belong to the evening before. */
private fun editSleep(context: Context, wakeDay: LocalDate, existing: SleepRecord?, onSave: (Long, Long) -> Unit) {
    pickTime(context, existing?.sleepStart ?: T.millis(wakeDay.minusDays(1), 22, 0), "Bedtime (fell asleep)") { bh, bm ->
        val bedDay = if (bh >= 12) wakeDay.minusDays(1) else wakeDay
        val bed = T.millis(bedDay, bh, bm)
        pickTime(context, existing?.wakeTime ?: T.millis(wakeDay, 6, 0), "Wake-up time") { wh, wm ->
            val wake = T.millis(wakeDay, wh, wm)
            if (wake > bed) onSave(bed, wake)
            else Toast.makeText(context, "Wake time must be after bedtime", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
private fun SleepHeader(emoji: String, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        IconBadge(emoji)
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SleepScreen(onBack: () -> Unit) {
    val repo = repo()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = T.today()
    val from = today.minusDays(13)
    val records by remember { repo.dao.sleepBetween(T.key(from), T.key(today)) }.collectAsState(initial = emptyList())
    val hasPerm = SleepTracker.hasPermission(ctx)

    val byDate = records.associateBy { it.date }
    val days = (0..13).map { today.minusDays((13 - it).toLong()) }
    val last7 = records.filter { it.date >= T.key(today.minusDays(6)) }

    Page(title = "Sleep", subtitle = "Target: asleep 9:30–10:00 pm · up 5:45–6:00 am", onBack = onBack) {
        val lastRec = days.reversed().firstNotNullOfOrNull { byDate[T.key(it)] }
        GlassCard(contentPadding = 20.dp) {
            SleepHeader("🌙", "Last night")
            if (lastRec != null) {
                Text(
                    T.duration((lastRec.wakeTime - lastRec.sleepStart) / T.MINUTE),
                    fontSize = 44.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    "${T.time(lastRec.sleepStart)} → ${T.time(lastRec.wakeTime)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text("No data yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        GlassCard {
            SleepHeader("😴", "Automatic tracking")
            if (hasPerm) {
                Text("✓ Usage access is on. Bedtime = when your screen went off for the night; wake = when you first turned it on in the morning.")
            } else {
                Text("Turn on usage access for RefluxCare so the app can read when your screen turns off and on. Nothing leaves your phone.")
                GradientButton(text = "Allow usage access", onClick = { ctx.startActivity(SleepTracker.permissionIntent()) })
            }
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val n = repo.syncSleep(ctx)
                        for (i in 0..9) repo.reevaluate(T.key(today.minusDays(i.toLong())))
                        Toast.makeText(ctx, if (n > 0) "Updated $n night(s)" else "No new sleep found", Toast.LENGTH_SHORT).show()
                    }
                },
                border = BorderStroke(1.dp, Glass.border)
            ) { Text("Detect now") }
            Text(
                "Short checks at night (under 10 min) are ignored. If you watch videos in bed or the phone was used " +
                    "by someone else, edit the night manually — manual edits are never overwritten.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        GlassCard {
            SleepHeader("📊", "Last 7 nights")
            if (last7.isEmpty()) {
                Text("No data yet.")
            } else {
                val avgMin = last7.map { (it.wakeTime - it.sleepStart) / T.MINUTE }.average().toLong()
                val avgBed = last7.map { val m = T.minutesOfDay(it.sleepStart); if (m < 720) m + 1440 else m }.average().toInt()
                val avgWake = last7.map { T.minutesOfDay(it.wakeTime) }.average().toInt()
                Row(Modifier.fillMaxWidth()) {
                    Stat("Avg sleep", T.duration(avgMin), Modifier.weight(1f))
                    Stat("Avg bedtime", T.fmtMinutesOfDay(avgBed), Modifier.weight(1f))
                    Stat("Avg wake", T.fmtMinutesOfDay(avgWake), Modifier.weight(1f))
                }
            }
            Text("Hours slept (dashed line = 7.5 h)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            BarChart(
                values = days.map { d -> byDate[T.key(d)]?.let { (it.wakeTime - it.sleepStart) / 3_600_000f } },
                labels = days.map { it.dayOfMonth.toString() },
                maxValue = 10f,
                color = Glass.Violet500,
                target = 7.5f
            )
        }

        GlassCard {
            SleepHeader("🌙", "Nights")
            days.reversed().forEach { d ->
                val key = T.key(d)
                val r = byDate[key]
                val rowShape = RoundedCornerShape(16.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(rowShape)
                        .background(Glass.surface)
                        .border(1.dp, Glass.border, rowShape)
                        .padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Night → ${T.day(d)}", fontWeight = FontWeight.SemiBold)
                        if (r != null) {
                            val late = r.sleepStart > T.millis(d.minusDays(1), Content.BEDTIME_LIMIT_H, 0)
                            Text(
                                "${T.time(r.sleepStart)} → ${T.time(r.wakeTime)} · ${T.duration((r.wakeTime - r.sleepStart) / T.MINUTE)}",
                                color = if (late) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                            Pill(if (r.source == "auto") "Auto" else "Manual")
                        } else {
                            Text("Not recorded", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    IconButton(onClick = {
                        editSleep(ctx, d, r) { bed, wake ->
                            scope.launch { repo.saveSleep(SleepRecord(key, bed, wake, "manual")) }
                        }
                    }) { Icon(Icons.Filled.Edit, contentDescription = "Edit") }
                    if (r != null) {
                        IconButton(onClick = { scope.launch { repo.deleteSleep(r) } }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                        }
                    }
                }
            }
        }

        GlassCard {
            SleepHeader("😴", "Sleep setup for reflux")
            Bullet("Raise the head of the bed 15–20 cm with blocks or a wedge.")
            Bullet("Sleep on your left side.")
            Bullet("Last food at least 3 hours before bed.")
            Bullet("Phone away at bedtime — it also makes tracking accurate.")
        }
    }
}
