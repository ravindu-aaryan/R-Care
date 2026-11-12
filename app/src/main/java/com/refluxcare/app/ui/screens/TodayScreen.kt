@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.refluxcare.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.refluxcare.app.data.MealLog
import com.refluxcare.app.data.SymptomLog
import com.refluxcare.app.data.tagList
import com.refluxcare.app.domain.Content
import com.refluxcare.app.domain.MealTypes
import com.refluxcare.app.domain.Portions
import com.refluxcare.app.domain.SleepTracker
import com.refluxcare.app.domain.Tags
import com.refluxcare.app.domain.WorkoutTypes
import com.refluxcare.app.ui.Page
import com.refluxcare.app.ui.Pill
import com.refluxcare.app.ui.SectionCard
import com.refluxcare.app.ui.repo
import com.refluxcare.app.util.T
import kotlinx.coroutines.launch

private val symptomLabels = listOf("None", "Mild", "Moderate", "Severe")

@Composable
fun TodayScreen(onOpen: (String) -> Unit) {
    val repo = repo()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var dateKey by rememberSaveable { mutableStateOf(T.todayKey()) }
    val day = T.parse(dateKey)
    val isToday = day == T.today()

    val meals by remember(dateKey) { repo.dao.mealsFor(dateKey) }.collectAsState(initial = emptyList())
    val sleep by remember(dateKey) { repo.dao.sleepFor(dateKey) }.collectAsState(initial = null)
    val workouts by remember(dateKey) { repo.dao.workoutsFor(dateKey) }.collectAsState(initial = emptyList())
    val symptom by remember(dateKey) { repo.dao.symptomFor(dateKey) }.collectAsState(initial = null)
    val habits by remember(dateKey) { repo.dao.habitsFor(dateKey) }.collectAsState(initial = emptyList())
    val breaks by remember(dateKey) { repo.dao.breaksBetween(dateKey, dateKey) }.collectAsState(initial = emptyList())
    val customFoods by remember { repo.dao.customFoods() }.collectAsState(initial = emptyList())

    var editingMeal by remember { mutableStateOf<MealLog?>(null) }
    var showMeal by remember { mutableStateOf(false) }
    var showWalk by remember { mutableStateOf(false) }
    var showBreak by remember { mutableStateOf(false) }

    val done = habits.filter { it.done }.map { it.habitId }.toSet()
    val planned = Content.plannedWorkout(day.dayOfWeek)
    val hasPerm = SleepTracker.hasPermission(ctx)

    Page(
        title = if (isToday) "Today" else T.day(day),
        subtitle = (if (isToday) T.day(day) + " · " else "") +
            (planned?.let { WorkoutTypes.label(it) + " day" } ?: "Rest day"),
        actions = {
            IconButton(onClick = { dateKey = T.key(day.minusDays(1)) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous day")
            }
            IconButton(enabled = !isToday, onClick = { dateKey = T.key(day.plusDays(1)) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next day")
            }
        }
    ) {
        // Shortcuts to the info pages
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { onOpen("routine") }, modifier = Modifier.weight(1f)) { Text("Routine") }
            FilledTonalButton(onClick = { onOpen("sleep") }, modifier = Modifier.weight(1f)) { Text("Sleep") }
            FilledTonalButton(onClick = { onOpen("guide") }, modifier = Modifier.weight(1f)) { Text("Guide") }
        }

        // Summary
        SectionCard(title = "Summary") {
            val pct = done.size.toFloat() / Content.habits.size
            Text("Habits: ${done.size} / ${Content.habits.size}")
            LinearProgressIndicator(progress = { pct }, modifier = Modifier.fillMaxWidth())
            Text(
                if (breaks.isEmpty()) "No rule breaks ${if (isToday) "so far today" else "this day"} 👍"
                else "${breaks.size} rule break(s) — see below",
                color = if (breaks.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }

        // Sleep
        SectionCard(
            title = "Last night's sleep",
            action = { TextButton(onClick = { onOpen("sleep") }) { Text("Details") } }
        ) {
            val s = sleep
            if (s != null) {
                val mins = (s.wakeTime - s.sleepStart) / T.MINUTE
                Text("Bedtime ${T.time(s.sleepStart)}  →  Woke ${T.time(s.wakeTime)}", fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(T.duration(mins))
                    Pill(if (s.source == "auto") "Auto from screen activity" else "Edited by you")
                }
            } else {
                Text("No sleep record for this night yet.")
                if (!hasPerm) {
                    Text(
                        "Allow usage access so the app can detect sleep from when your screen turns off and on.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(onClick = { ctx.startActivity(SleepTracker.permissionIntent()) }) { Text("Allow usage access") }
                }
            }
        }

        // Symptoms
        SectionCard(title = "Reflux symptoms") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                symptomLabels.forEachIndexed { i, label ->
                    FilterChip(
                        selected = symptom?.severity == i,
                        onClick = {
                            scope.launch {
                                repo.setSymptom(SymptomLog(dateKey, i, symptom?.night ?: false, symptom?.note ?: ""))
                            }
                        },
                        label = { Text(label) }
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = symptom?.night ?: false,
                    onCheckedChange = { checked ->
                        scope.launch {
                            repo.setSymptom(SymptomLog(dateKey, symptom?.severity ?: 1, checked, symptom?.note ?: ""))
                        }
                    }
                )
                Text("Symptoms at night / lying down")
            }
        }

        // Habits
        SectionCard(title = "Daily habits") {
            Content.habits.forEach { h ->
                val checked = h.id in done
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { scope.launch { repo.setHabit(dateKey, h.id, !checked) } },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = checked, onCheckedChange = { c -> scope.launch { repo.setHabit(dateKey, h.id, c) } })
                    Column(Modifier.weight(1f)) {
                        Text(h.title)
                        Text(h.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Meals
        SectionCard(
            title = "Meals & drinks",
            action = {
                TextButton(onClick = { editingMeal = null; showMeal = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Add")
                }
            }
        ) {
            if (meals.isEmpty()) Text("Nothing logged yet. Log every meal and drink, including tea.")
            meals.forEach { m ->
                Row(
                    Modifier.fillMaxWidth().clickable { editingMeal = m; showMeal = true },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${T.time(m.time)} · ${MealTypes.label(m.mealType)}", fontWeight = FontWeight.SemiBold)
                        Text(m.items)
                        val tagText = m.tagList().joinToString(", ") { Tags.label(it) }
                        Text(
                            Portions.label(m.portion) + if (tagText.isNotEmpty()) " · $tagText" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { scope.launch { repo.deleteMeal(m) } }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete meal")
                    }
                }
                HorizontalDivider()
            }
        }

        // Activity
        SectionCard(title = "Exercise & walks") {
            if (workouts.isEmpty()) Text(planned?.let { "Planned: ${WorkoutTypes.label(it)} + evening walk" } ?: "Rest day: evening walk only")
            workouts.forEach { w ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${T.time(w.time)} · ${WorkoutTypes.label(w.type)}", fontWeight = FontWeight.SemiBold)
                        val extra = buildString {
                            append("${w.durationMin} min")
                            if (w.steps > 0) append(" · ${w.steps} steps")
                        }
                        Text(extra, style = MaterialTheme.typography.bodySmall)
                        if (w.details.isNotBlank() && w.type != WorkoutTypes.WALK) {
                            Text(w.details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    IconButton(onClick = { scope.launch { repo.deleteWorkout(w) } }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onOpen("exercise") }, modifier = Modifier.weight(1f)) { Text("Start workout") }
                OutlinedButton(onClick = { showWalk = true }, modifier = Modifier.weight(1f)) { Text("Log walk") }
            }
        }

        // Rule breaks
        SectionCard(
            title = "Rule breaks",
            action = { TextButton(onClick = { showBreak = true }) { Text("Log one") } }
        ) {
            if (breaks.isEmpty()) Text("None. Breaks are detected automatically from your logs, or add one yourself.")
            breaks.forEach { b ->
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(b.title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                        Pill(if (b.source == "auto") "Auto" else "Manual")
                    }
                    if (b.detail.isNotBlank()) Text(b.detail, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    if (showMeal) {
        MealDialog(
            date = dateKey,
            existing = editingMeal,
            customFoods = customFoods,
            onDismiss = { showMeal = false },
            onSave = { m -> showMeal = false; scope.launch { repo.saveMeal(m) } }
        )
    }
    if (showWalk) {
        WalkDialog(
            date = dateKey,
            onDismiss = { showWalk = false },
            onSave = { w -> showWalk = false; scope.launch { repo.saveWorkout(w) } }
        )
    }
    if (showBreak) {
        RuleBreakDialog(
            defaultDate = dateKey,
            onDismiss = { showBreak = false },
            onSave = { d, id, detail ->
                showBreak = false
                scope.launch {
                    repo.addManualBreak(d, id, detail)
                    Toast.makeText(ctx, "Rule break noted", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}
