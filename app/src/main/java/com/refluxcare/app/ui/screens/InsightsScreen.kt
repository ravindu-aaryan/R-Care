@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.refluxcare.app.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.refluxcare.app.data.Exporter
import com.refluxcare.app.domain.InsightsCalc
import com.refluxcare.app.ui.BarChart
import com.refluxcare.app.ui.Bullet
import com.refluxcare.app.ui.Glass
import com.refluxcare.app.ui.GlassCard
import com.refluxcare.app.ui.GlassChip
import com.refluxcare.app.ui.GradientButton
import com.refluxcare.app.ui.IconBadge
import com.refluxcare.app.ui.Page
import com.refluxcare.app.ui.Stat
import com.refluxcare.app.ui.repo
import com.refluxcare.app.util.T
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
private fun InsHeader(emoji: String, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        IconBadge(emoji)
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .clip(shape)
            .background(Glass.surface)
            .border(1.dp, Glass.border, shape)
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        Stat(label, value)
    }
}

@Composable
private fun StatGrid(vararg items: Pair<String, String>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (l, v) -> StatTile(l, v, Modifier.weight(1f)) }
    }
}

@Composable
fun InsightsScreen(onOpen: (String) -> Unit) {
    val repo = repo()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var range by rememberSaveable { mutableIntStateOf(7) }

    val to = T.today()
    val from = to.minusDays((range - 1).toLong())
    val f = T.key(from)
    val t = T.key(to)

    val meals by remember(range) { repo.dao.mealsBetween(f, t) }.collectAsState(initial = emptyList())
    val sleeps by remember(range) { repo.dao.sleepBetween(f, t) }.collectAsState(initial = emptyList())
    val workouts by remember(range) { repo.dao.workoutsBetween(f, t) }.collectAsState(initial = emptyList())
    val symptoms by remember(range) { repo.dao.symptomsBetween(f, t) }.collectAsState(initial = emptyList())
    val habits by remember(range) { repo.dao.habitsBetween(f, t) }.collectAsState(initial = emptyList())
    val breaks by remember(range) { repo.dao.breaksBetween(f, t) }.collectAsState(initial = emptyList())

    val data = remember(range, meals, sleeps, workouts, symptoms, habits, breaks) {
        InsightsCalc.compute(from, to, meals, sleeps, workouts, symptoms, habits, breaks)
    }
    val labels = data.days.map { it.dayOfMonth.toString() }

    Page(title = "Insights", subtitle = "${T.day(from)} – ${T.day(to)}") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(7, 14, 30).forEach { r ->
                GlassChip(text = "$r days", selected = range == r, onClick = { range = r })
            }
        }

        val heroShape = RoundedCornerShape(24.dp)
        Box(
            Modifier
                .fillMaxWidth()
                .clip(heroShape)
                .background(Glass.accent, alpha = 0.28f)
                .border(1.dp, Glass.border, heroShape)
        ) {
            GlassCard(contentPadding = 20.dp) {
                InsHeader("💡", "Key insights")
                data.messages.forEach { Bullet(it) }
            }
        }

        GlassCard {
            InsHeader("🎯", "Possible triggers")
            if (data.triggers.isEmpty()) {
                Text(
                    "Not enough data yet. Log your symptom score daily (even \"None\") — the app compares " +
                        "symptoms on and after rule-break days with other days."
                )
            } else {
                Text("Average symptom score (0–3) on the day of / day after the rule break vs. other days:",
                    style = MaterialTheme.typography.bodySmall)
                data.triggers.forEach { tr ->
                    val trShape = RoundedCornerShape(14.dp)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(trShape)
                            .background(Glass.Pink400.copy(alpha = 0.20f))
                            .border(1.dp, Glass.Pink400.copy(alpha = 0.45f), trShape)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(tr.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("%.1f vs %.1f".format(tr.withAvg, tr.withoutAvg), fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }

        GlassCard {
            InsHeader("✅", "Habit completion")
            Text("Average ${data.avgHabitPct?.roundToInt()?.let { "$it%" } ?: "—"} of daily habits on tracked days")
            BarChart(values = data.habitPct, labels = labels, maxValue = 100f, target = 80f)
        }

        GlassCard {
            InsHeader("🌙", "Sleep")
            StatGrid(
                "Avg sleep" to (data.avgSleepMin?.let { T.duration(it.toLong()) } ?: "—"),
                "Avg bedtime" to (data.avgBed?.let { T.fmtMinutesOfDay(it) } ?: "—"),
                "Avg wake" to (data.avgWake?.let { T.fmtMinutesOfDay(it) } ?: "—")
            )
            Text("In bed by 10 pm: ${data.onTimeBed}/${data.nightsTracked} · Up by 6:30: ${data.onTimeWake}/${data.nightsTracked}",
                style = MaterialTheme.typography.bodySmall)
            BarChart(values = data.sleepHours, labels = labels, maxValue = 10f, color = Glass.Violet500, target = 7.5f)
            OutlinedButton(
                onClick = { onOpen("sleep") },
                border = BorderStroke(1.dp, Glass.border)
            ) { Text("Open sleep page") }
        }

        GlassCard {
            InsHeader("🔥", "Reflux symptoms")
            StatGrid(
                "Avg score" to (data.avgSymptom?.let { "%.1f".format(it) } ?: "—"),
                "Symptom-free" to "${data.symptomFreeDays}/${data.symptomDays}",
                "Night symptoms" to "${data.nightSymptomDays}"
            )
            BarChart(values = data.symptoms, labels = labels, maxValue = 3f, color = Glass.Pink400)
        }

        GlassCard {
            InsHeader("⚠️", "Rule breaks")
            Text("${data.totalBreaks} in this period", fontWeight = FontWeight.Medium)
            BarChart(
                values = data.breaksPerDay,
                labels = labels,
                maxValue = maxOf(4f, data.breaksPerDay.filterNotNull().maxOrNull() ?: 4f),
                color = Glass.Pink400,
                height = 110.dp
            )
            data.topBreaks.forEach { (title, n) ->
                Row {
                    Text(title, modifier = Modifier.weight(1f))
                    Text("$n×", fontWeight = FontWeight.Bold)
                }
            }
        }

        GlassCard {
            InsHeader("🏋️", "Exercise")
            StatGrid(
                "Dumbbells" to "${data.dumbbell}/${data.plannedDumbbell}",
                "Yoga" to "${data.yoga}/${data.plannedYoga}",
                "Walks" to "${data.walks}"
            )
            Text("Steps logged: ${data.steps}", style = MaterialTheme.typography.bodySmall)
        }

        GlassCard {
            InsHeader("🍽️", "Food tags")
            if (data.tagCounts.isEmpty()) Text("No tagged meals. Tag meals as fried, spicy, outside food, etc. when logging.")
            data.tagCounts.forEach { (tag, n) ->
                Row {
                    Text(tag, modifier = Modifier.weight(1f))
                    Text("$n×", fontWeight = FontWeight.Bold)
                }
            }
        }

        GlassCard {
            InsHeader("📤", "Export for your own analysis")
            Text("Shares a daily summary CSV (opens in Excel / Google Sheets) and a full JSON backup.",
                style = MaterialTheme.typography.bodySmall)
            GradientButton(text = "Export data", onClick = {
                scope.launch {
                    try {
                        val intent = Exporter.export(ctx, repo.dao)
                        ctx.startActivity(Intent.createChooser(intent, "Export RefluxCare data"))
                    } catch (e: Exception) {
                        Toast.makeText(ctx, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            })
        }
    }
}
