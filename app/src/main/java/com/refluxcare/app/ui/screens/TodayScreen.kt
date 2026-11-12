@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.refluxcare.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.refluxcare.app.ui.Glass
import com.refluxcare.app.ui.GlassCard
import com.refluxcare.app.ui.GlassChip
import com.refluxcare.app.ui.GradientButton
import com.refluxcare.app.ui.IconBadge
import com.refluxcare.app.ui.Stat
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
        // Hero summary
        val pct = if (Content.habits.isEmpty()) 0f else done.size.toFloat() / Content.habits.size
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconBadge("✨")
                Text("Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ProgressRing(pct, Modifier.size(96.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        T.day(day),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        "Habits: ${done.size} / ${Content.habits.size}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Stat("Breaks", breaks.size.toString())
                        Stat("Meals", meals.size.toString())
                        Stat("Workouts", workouts.size.toString())
                    }
                }
            }
            Text(
                if (breaks.isEmpty()) "No rule breaks ${if (isToday) "so far today" else "this day"} 👍"
                else "${breaks.size} rule break(s) — see below",
                color = if (breaks.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }

        // Shortcuts to the info pages
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassChip("⏰ Routine", false) { onOpen("routine") }
            GlassChip("🌙 Sleep", false) { onOpen("sleep") }
            GlassChip("📖 Guide", false) { onOpen("guide") }
            GlassChip("⚠️ Rule breaks", false) { onOpen("rules") }
        }

        // Sleep
        GlassSection(
            emoji = "🌙",
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
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    GradientButton("Allow usage access", onClick = { ctx.startActivity(SleepTracker.permissionIntent()) })
                }
            }
        }

        // Symptoms
        GlassSection(emoji = "🔥", title = "Reflux symptoms") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                symptomLabels.forEachIndexed { i, label ->
                    GlassChip(
                        text = label,
                        selected = symptom?.severity == i,
                        onClick = {
                            scope.launch {
                                repo.setSymptom(SymptomLog(dateKey, i, symptom?.night ?: false, symptom?.note ?: ""))
                            }
                        }
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
        GlassSection(emoji = "✅", title = "Daily habits") {
            Content.habits.forEach { h ->
                val checked = h.id in done
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent
                        )
                        .clickable { scope.launch { repo.setHabit(dateKey, h.id, !checked) } }
                        .padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = checked, onCheckedChange = { c -> scope.launch { repo.setHabit(dateKey, h.id, c) } })
                    Column(Modifier.weight(1f)) {
                        Text(h.title, fontWeight = FontWeight.Medium)
                        Text(h.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Meals
        GlassSection(
            emoji = "🍽️",
            title = "Meals & drinks",
            action = {
                TextButton(onClick = { editingMeal = null; showMeal = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Add")
                }
            }
        ) {
            if (meals.isEmpty()) {
                Text(
                    "Nothing logged yet. Log every meal and drink, including tea.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            meals.forEach { m ->
                Tile(onClick = { editingMeal = m; showMeal = true }) {
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
            }
        }

        // Activity
        GlassSection(emoji = "🏋️", title = "Exercise & walks") {
            if (workouts.isEmpty()) {
                Text(
                    planned?.let { "Planned: ${WorkoutTypes.label(it)} + evening walk" } ?: "Rest day: evening walk only",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            workouts.forEach { w ->
                Tile {
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                GradientButton("Start workout", onClick = { onOpen("exercise") }, modifier = Modifier.weight(1f))
                OutlinedButton(
                    onClick = { showWalk = true },
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, Glass.border)
                ) { Text("Log walk") }
            }
        }

        // Rule breaks
        GlassSection(
            emoji = "⚠️",
            title = "Rule breaks",
            action = { TextButton(onClick = { showBreak = true }) { Text("Log one") } }
        ) {
            if (breaks.isEmpty()) {
                Text(
                    "None. Breaks are detected automatically from your logs, or add one yourself.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            breaks.forEach { b ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
                        .padding(12.dp)
                ) {
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

@Composable
private fun GlassSection(
    emoji: String,
    title: String,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconBadge(emoji)
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            action?.invoke()
        }
        content()
    }
}

@Composable
private fun Tile(onClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val base = Modifier
        .fillMaxWidth()
        .clip(shape)
        .background(Glass.surface)
        .border(1.dp, Glass.border, shape)
    Row(
        (if (onClick != null) base.clickable(onClick = onClick) else base).padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
private fun ProgressRing(progress: Float, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val colors = listOf(Glass.Teal500, Glass.Violet500, Glass.Pink400)
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 10.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            if (progress > 0f) {
                rotate(-90f) {
                    drawArc(
                        brush = Brush.sweepGradient(colors, center = center),
                        startAngle = 0f,
                        sweepAngle = 360f * progress.coerceIn(0f, 1f),
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round)
                    )
                }
            }
        }
        Text(
            "${(progress * 100).toInt()}%",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold
        )
    }
}
