package com.refluxcare.app.ui.screens

import android.media.AudioManager
import android.media.ToneGenerator
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.refluxcare.app.data.WorkoutLog
import com.refluxcare.app.domain.Content
import com.refluxcare.app.domain.Exercise
import com.refluxcare.app.domain.MealTypes
import com.refluxcare.app.domain.WorkoutTypes
import com.refluxcare.app.ui.Bullet
import com.refluxcare.app.ui.ExpandableCard
import com.refluxcare.app.ui.Glass
import com.refluxcare.app.ui.GlassCard
import com.refluxcare.app.ui.GradientButton
import com.refluxcare.app.ui.IconBadge
import com.refluxcare.app.ui.Page
import com.refluxcare.app.ui.repo
import com.refluxcare.app.util.T
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek

@Composable
fun ExerciseScreen(onStart: (String) -> Unit) {
    val repo = repo()
    val scope = rememberCoroutineScope()
    val today = T.today()
    val planned = Content.plannedWorkout(today.dayOfWeek)
    val monday = today.with(DayOfWeek.MONDAY)
    val weekLogs by remember { repo.dao.workoutsBetween(T.key(monday), T.key(monday.plusDays(6))) }
        .collectAsState(initial = emptyList())

    Page(title = "Exercise", subtitle = "Dumbbells + yoga, always on an empty stomach") {
        val first = planned ?: WorkoutTypes.YOGA
        val second = if (first == WorkoutTypes.DUMBBELL) WorkoutTypes.YOGA else WorkoutTypes.DUMBBELL
        GlassCard(contentPadding = 20.dp) {
            ExHeader(if (planned == WorkoutTypes.DUMBBELL) "🏋️" else "🧘", "Today")
            Text(
                planned?.let { WorkoutTypes.label(it) } ?: "Rest day",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                if (planned != null) "Planned for today" else "Sunday is a rest day — just your evening walk.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GradientButton(
                    text = if (first == WorkoutTypes.DUMBBELL) "Start dumbbells" else "Start yoga",
                    onClick = { onStart(first) },
                    modifier = Modifier.weight(1f)
                )
                GradientButton(
                    text = if (second == WorkoutTypes.DUMBBELL) "Start dumbbells" else "Start yoga",
                    onClick = { onStart(second) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        GlassCard {
            ExHeader("📅", "This week")
            val dumbbell = weekLogs.count { it.type == WorkoutTypes.DUMBBELL }
            val yoga = weekLogs.count { it.type == WorkoutTypes.YOGA }
            val walks = weekLogs.count { it.type == WorkoutTypes.WALK }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ProgressRing("Dumbbells", dumbbell, 3)
                ProgressRing("Yoga", yoga, 3)
                ProgressRing("Walks", walks, 7)
            }
            weekLogs.forEach { w ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${T.day(T.parse(w.date))} · ${WorkoutTypes.label(w.type)} · ${w.durationMin} min" +
                            if (w.steps > 0) " · ${w.steps} steps" else "",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { scope.launch { repo.deleteWorkout(w) } }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                    }
                }
            }
        }

        GlassCard {
            ExHeader("📅", "Weekly plan")
            Content.weeklyPlan.forEach { (day, plan) ->
                Row {
                    Text(day, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(110.dp))
                    Text(plan)
                }
            }
        }

        ExpandableCard(title = "Rules for exercising with reflux", startExpanded = true) {
            Content.exerciseRules.forEach { Bullet(it) }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconBadge("🏋️")
            Text("Dumbbell routine", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Content.dumbbell.forEach { ExerciseInfoCard(it) }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconBadge("🧘")
            Text("Yoga stretches", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Content.yoga.forEach { ExerciseInfoCard(it) }
    }
}

@Composable
private fun ExHeader(emoji: String, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        IconBadge(emoji)
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ProgressRing(label: String, done: Int, goal: Int, size: Dp = 80.dp) {
    val progress = (done.toFloat() / goal).coerceIn(0f, 1f)
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val sweep = Brush.sweepGradient(listOf(Glass.Teal500, Glass.Violet500, Glass.Pink400, Glass.Teal500))
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 9.dp.toPx()
                val inset = stroke / 2f
                val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
                drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                if (progress > 0f) {
                    rotate(-90f) {
                        drawArc(
                            sweep, 0f, 360f * progress, false, Offset(inset, inset), arcSize,
                            style = Stroke(stroke, cap = StrokeCap.Round)
                        )
                    }
                }
            }
            Text("$done/$goal", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
        }
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ExerciseInfoCard(ex: Exercise) {
    ExpandableCard(title = ex.name, subtitle = ex.prescription) {
        ex.steps.forEachIndexed { i, s -> Bullet(s, prefix = "${i + 1}.") }
        if (ex.tips.isNotEmpty()) {
            Text("Tips", fontWeight = FontWeight.SemiBold)
            ex.tips.forEach { Bullet(it) }
        }
    }
}

private suspend fun beep() {
    try {
        val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
        tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 600)
        delay(800)
        tg.release()
    } catch (_: Exception) {
    }
}

/** Guided session: tick off sets, use timers, then save to the log. */
@Composable
fun WorkoutSessionScreen(type: String, onDone: () -> Unit) {
    val repo = repo()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val list = remember(type) { Content.exercisesFor(type) }
    val start = rememberSaveable { System.currentTimeMillis() }
    val todayKey = remember { T.todayKey() }

    val sets = remember { mutableStateMapOf<String, Int>() }
    val weights = remember { mutableStateMapOf<String, String>() }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }

    var timerLeft by remember { mutableIntStateOf(0) }
    var timerLabel by remember { mutableStateOf("") }
    var timerRun by remember { mutableIntStateOf(0) }
    var timerTotal by remember { mutableIntStateOf(1) }
    LaunchedEffect(timerRun) {
        if (timerRun == 0 || timerLeft <= 0) return@LaunchedEffect
        while (timerLeft > 0) {
            delay(1000)
            timerLeft -= 1
        }
        beep()
    }
    fun startTimer(label: String, seconds: Int) {
        timerLabel = label
        timerLeft = seconds
        timerTotal = seconds.coerceAtLeast(1)
        timerRun += 1
    }

    val meals by remember { repo.dao.mealsFor(todayKey) }.collectAsState(initial = emptyList())
    val recentMeal = meals
        .filter { it.mealType != MealTypes.EARLY && now - it.time in 0 until Content.EXERCISE_GAP_HOURS * T.HOUR }
        .maxByOrNull { it.time }

    val elapsedMin = (now - start) / T.MINUTE
    val elapsedSec = ((now - start) / 1000) % 60

    Page(
        title = WorkoutTypes.label(type),
        subtitle = "Elapsed ${elapsedMin}m ${elapsedSec}s",
        onBack = onDone
    ) {
        if (recentMeal != null) {
            val coral = Color(0xFFFF6B6B)
            val shape = RoundedCornerShape(24.dp)
            Box(Modifier.clip(shape).background(coral.copy(alpha = 0.22f)).border(1.dp, coral.copy(alpha = 0.6f), shape)) {
                GlassCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        IconBadge("⚠️")
                        Text(
                            "You ate at ${T.time(recentMeal.time)}. Wait until 2 hours after eating before exercising, " +
                                "or keep it to gentle stretches. Saving now will be logged as a rule break.",
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        if (timerLeft > 0) {
            val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            val sweep = Brush.sweepGradient(listOf(Glass.Teal500, Glass.Violet500, Glass.Pink400, Glass.Teal500))
            val frac = (timerLeft.toFloat() / timerTotal).coerceIn(0f, 1f)
            GlassCard(contentPadding = 20.dp) {
                Text(
                    timerLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            val stroke = 14.dp.toPx()
                            val inset = stroke / 2f
                            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
                            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                            rotate(-90f) {
                                drawArc(
                                    sweep, 0f, 360f * frac, false, Offset(inset, inset), arcSize,
                                    style = Stroke(stroke, cap = StrokeCap.Round)
                                )
                            }
                        }
                        Text(
                            "%d:%02d".format(timerLeft / 60, timerLeft % 60),
                            fontSize = 44.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    OutlinedButton(
                        onClick = { timerLeft = 0; timerRun += 1 },
                        border = androidx.compose.foundation.BorderStroke(1.dp, Glass.border)
                    ) { Text("Stop") }
                }
            }
        }

        if (type == WorkoutTypes.DUMBBELL) {
            Text(
                "Breathe out as you lift. Rest 60 seconds between sets.",
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            Text(
                "Move slowly with your breath. Never force a stretch.",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        list.forEach { ex ->
            val doneSets = sets[ex.id] ?: 0
            val complete = ex.sets > 0 && doneSets >= ex.sets
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (complete) Glass.Teal500.copy(alpha = 0.22f) else Color.Transparent)
            ) {
                GlassCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                (if (complete) "✓ " else "") + ex.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (complete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            Text(ex.prescription, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    ex.steps.forEachIndexed { i, s -> Bullet(s, prefix = "${i + 1}.") }
                    ex.tips.forEach { Bullet(it, prefix = "💡") }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Done: $doneSets/${ex.sets}", modifier = Modifier.weight(1f))
                        OutlinedButton(
                            onClick = { sets[ex.id] = (doneSets - 1).coerceAtLeast(0) },
                            border = androidx.compose.foundation.BorderStroke(1.dp, Glass.border)
                        ) { Text("−") }
                        GradientButton(
                            text = if (ex.sets > 1) "+ Set" else "Done",
                            onClick = {
                                sets[ex.id] = doneSets + 1
                                if (type == WorkoutTypes.DUMBBELL && ex.holdSeconds == 0 && doneSets + 1 < ex.sets) {
                                    startTimer("Rest — ${ex.name}", 60)
                                }
                            }
                        )
                    }

                    if (type == WorkoutTypes.DUMBBELL && ex.holdSeconds == 0 && ex.id != "pushup") {
                        OutlinedTextField(
                            value = weights[ex.id] ?: "",
                            onValueChange = { v -> weights[ex.id] = v.filter { it.isDigit() || it == '.' }.take(5) },
                            label = { Text("Weight (kg)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.width(140.dp)
                        )
                    }
                    if (ex.holdSeconds > 0) {
                        GradientButton(
                            text = "Start ${if (ex.holdSeconds >= 60) "${ex.holdSeconds / 60} min" else "${ex.holdSeconds} s"} timer",
                            onClick = { startTimer(ex.name, ex.holdSeconds) }
                        )
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onDone,
                modifier = Modifier.weight(1f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Glass.border)
            ) { Text("Discard") }
            GradientButton(
                text = "Finish & save",
                modifier = Modifier.weight(1f),
                onClick = {
                    val details = list.filter { (sets[it.id] ?: 0) > 0 }.joinToString("; ") { ex ->
                        val w = weights[ex.id]?.takeIf { it.isNotBlank() }?.let { " @ ${it}kg" } ?: ""
                        "${ex.name} ${sets[ex.id]}×$w"
                    }.ifBlank { "Session" }
                    val log = WorkoutLog(
                        date = todayKey,
                        type = type,
                        time = start,
                        durationMin = ((System.currentTimeMillis() - start) / T.MINUTE).toInt().coerceAtLeast(1),
                        details = details
                    )
                    scope.launch {
                        repo.saveWorkout(log)
                        Toast.makeText(ctx, "Workout saved 💪", Toast.LENGTH_SHORT).show()
                        onDone()
                    }
                }
            )
        }
    }
}
