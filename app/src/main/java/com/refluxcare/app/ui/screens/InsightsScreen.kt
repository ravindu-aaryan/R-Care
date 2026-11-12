@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.refluxcare.app.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
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
import com.refluxcare.app.ui.Page
import com.refluxcare.app.ui.SectionCard
import com.refluxcare.app.ui.Stat
import com.refluxcare.app.ui.repo
import com.refluxcare.app.util.T
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
                FilterChip(selected = range == r, onClick = { range = r }, label = { Text("$r days") })
            }
        }

        SectionCard(title = "Key insights") {
            data.messages.forEach { Bullet(it) }
        }

        SectionCard(title = "Possible triggers") {
            if (data.triggers.isEmpty()) {
                Text(
                    "Not enough data yet. Log your symptom score daily (even \"None\") — the app compares " +
                        "symptoms on and after rule-break days with other days."
                )
            } else {
                Text("Average symptom score (0–3) on the day of / day after the rule break vs. other days:",
                    style = MaterialTheme.typography.bodySmall)
                data.triggers.forEach { tr ->
                    Row {
                        Text(tr.title, modifier = Modifier.weight(1f))
                        Text("%.1f vs %.1f".format(tr.withAvg, tr.withoutAvg), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        SectionCard(title = "Habit completion") {
            Text("Average ${data.avgHabitPct?.roundToInt()?.let { "$it%" } ?: "—"} of daily habits on tracked days")
            BarChart(values = data.habitPct, labels = labels, maxValue = 100f, target = 80f)
        }

        SectionCard(title = "Sleep") {
            Row(Modifier.fillMaxWidth()) {
                Stat("Avg sleep", data.avgSleepMin?.let { T.duration(it.toLong()) } ?: "—", Modifier.weight(1f))
                Stat("Avg bedtime", data.avgBed?.let { T.fmtMinutesOfDay(it) } ?: "—", Modifier.weight(1f))
                Stat("Avg wake", data.avgWake?.let { T.fmtMinutesOfDay(it) } ?: "—", Modifier.weight(1f))
            }
            Text("In bed by 10 pm: ${data.onTimeBed}/${data.nightsTracked} · Up by 6:30: ${data.onTimeWake}/${data.nightsTracked}",
                style = MaterialTheme.typography.bodySmall)
            BarChart(values = data.sleepHours, labels = labels, maxValue = 10f, target = 7.5f)
            TextButton(onClick = { onOpen("sleep") }) { Text("Open sleep page") }
        }

        SectionCard(title = "Reflux symptoms") {
            Row(Modifier.fillMaxWidth()) {
                Stat("Avg score", data.avgSymptom?.let { "%.1f".format(it) } ?: "—", Modifier.weight(1f))
                Stat("Symptom-free", "${data.symptomFreeDays}/${data.symptomDays}", Modifier.weight(1f))
                Stat("Night symptoms", "${data.nightSymptomDays}", Modifier.weight(1f))
            }
            BarChart(values = data.symptoms, labels = labels, maxValue = 3f, color = MaterialTheme.colorScheme.error)
        }

        SectionCard(title = "Rule breaks") {
            Text("${data.totalBreaks} in this period", fontWeight = FontWeight.Medium)
            BarChart(
                values = data.breaksPerDay,
                labels = labels,
                maxValue = maxOf(4f, data.breaksPerDay.filterNotNull().maxOrNull() ?: 4f),
                color = MaterialTheme.colorScheme.error,
                height = 110.dp
            )
            data.topBreaks.forEach { (title, n) ->
                Row {
                    Text(title, modifier = Modifier.weight(1f))
                    Text("$n×", fontWeight = FontWeight.Bold)
                }
            }
        }

        SectionCard(title = "Exercise") {
            Row(Modifier.fillMaxWidth()) {
                Stat("Dumbbells", "${data.dumbbell}/${data.plannedDumbbell}", Modifier.weight(1f))
                Stat("Yoga", "${data.yoga}/${data.plannedYoga}", Modifier.weight(1f))
                Stat("Walks", "${data.walks}", Modifier.weight(1f))
            }
            Text("Steps logged: ${data.steps}", style = MaterialTheme.typography.bodySmall)
        }

        SectionCard(title = "Food tags") {
            if (data.tagCounts.isEmpty()) Text("No tagged meals. Tag meals as fried, spicy, outside food, etc. when logging.")
            data.tagCounts.forEach { (tag, n) ->
                Row {
                    Text(tag, modifier = Modifier.weight(1f))
                    Text("$n×", fontWeight = FontWeight.Bold)
                }
            }
        }

        SectionCard(title = "Export for your own analysis") {
            Text("Shares a daily summary CSV (opens in Excel / Google Sheets) and a full JSON backup.",
                style = MaterialTheme.typography.bodySmall)
            Button(onClick = {
                scope.launch {
                    try {
                        val intent = Exporter.export(ctx, repo.dao)
                        ctx.startActivity(Intent.createChooser(intent, "Export RefluxCare data"))
                    } catch (e: Exception) {
                        Toast.makeText(ctx, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }) { Text("Export data") }
        }
    }
}
