@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.refluxcare.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.refluxcare.app.data.MeditationSession
import com.refluxcare.app.meditation.MeditationController
import com.refluxcare.app.meditation.MeditationSound
import com.refluxcare.app.meditation.MeditationState
import com.refluxcare.app.meditation.MeditationSounds
import com.refluxcare.app.ui.BarChart
import com.refluxcare.app.ui.Glass
import com.refluxcare.app.ui.GlassCard
import com.refluxcare.app.ui.GlassChip
import com.refluxcare.app.ui.GradientButton
import com.refluxcare.app.ui.Page
import com.refluxcare.app.ui.SectionCard
import com.refluxcare.app.ui.Stat
import com.refluxcare.app.ui.repo
import com.refluxcare.app.util.T
import kotlinx.coroutines.launch
import java.time.LocalDate

private val PRESETS = listOf(3, 5, 10, 15, 20, 30, 45)
private const val DAILY_GOAL_MIN = 10f

private fun mmss(sec: Int): String {
    val s = sec.coerceAtLeast(0)
    return "%02d:%02d".format(s / 60, s % 60)
}

private fun displayName(ctx: Context, uri: Uri): String {
    var name: String? = null
    try {
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0) name = c.getString(i)
            }
        }
    } catch (_: Exception) {
    }
    val n = name?.substringBeforeLast('.', name ?: "")?.trim().orEmpty()
    return n.ifBlank { "My music" }
}

@Composable
fun MeditationScreen(onBack: (() -> Unit)? = null) {
    val state by MeditationController.state.collectAsState()

    // Remember the last active state to report a finished session.
    var wasActive by remember { mutableStateOf(false) }
    var lastActive by remember { mutableStateOf(MeditationState()) }
    var completedSec by rememberSaveable { mutableIntStateOf(-1) }
    if (state.active) lastActive = state

    LaunchedEffect(state.active) {
        if (state.active) {
            completedSec = -1
        } else if (wasActive) {
            completedSec = (lastActive.totalSec - lastActive.remainingSec).coerceAtLeast(0)
        }
        wasActive = state.active
    }

    if (state.active) {
        SessionView(state)
    } else {
        SetupView(onBack, completedSec) { completedSec = -1 }
    }
}

@Composable
private fun SessionView(state: MeditationState) {
    val ctx = LocalContext.current
    var confirmStop by remember { mutableStateOf(false) }
    var guide by rememberSaveable { mutableStateOf(true) }
    val sound = remember(state.soundId) { MeditationSounds.find(ctx, state.soundId) }

    val transition = rememberInfiniteTransition(label = "breath")
    // 4 s in / 6 s out over a 10 s cycle.
    val level by transition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 10_000
                0f at 0 using FastOutSlowInEasing
                1f at 4_000 using FastOutSlowInEasing
            },
            RepeatMode.Restart
        ),
        label = "level"
    )
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(10_000, easing = LinearEasing), RepeatMode.Restart),
        label = "phase"
    )
    val breatheIn = phase < 0.4f

    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val accent = listOf(Glass.Teal500, Glass.Violet500, Glass.Pink400, Glass.Teal500)
    val frac = if (state.totalSec > 0) state.remainingSec.toFloat() / state.totalSec else 0f

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        GlassCard(contentPadding = 24.dp) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🧘 Meditating", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth(0.85f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                    if (guide) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .scale(0.70f + 0.30f * level)
                                .alpha(0.20f + 0.25f * level)
                                .clip(RoundedCornerShape(50))
                                .background(Glass.accent)
                        )
                    }
                    Canvas(Modifier.fillMaxSize().padding(10.dp)) {
                        val stroke = 14.dp.toPx()
                        val inset = stroke / 2f
                        val sz = Size(size.width - stroke, size.height - stroke)
                        drawArc(track, 0f, 360f, false, Offset(inset, inset), sz, style = Stroke(stroke))
                        drawArc(
                            Brush.sweepGradient(accent, center = center),
                            -90f, 360f * frac, false, Offset(inset, inset), sz,
                            style = Stroke(stroke, cap = StrokeCap.Round)
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            mmss(state.remainingSec),
                            fontSize = 52.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "${sound?.emoji ?: "🎵"} ${state.soundName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (guide && !state.paused) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                if (breatheIn) "Breathe in" else "Breathe out",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (state.paused) {
                            Spacer(Modifier.height(6.dp))
                            Text("Paused", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GradientButton(
                        if (state.paused) "Resume" else "Pause",
                        onClick = {
                            if (state.paused) MeditationController.resume(ctx) else MeditationController.pause(ctx)
                        }
                    )
                    OutlinedButton(onClick = { confirmStop = true }) { Text("Stop") }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Breathing guide", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                    Switch(checked = guide, onCheckedChange = { guide = it })
                }
            }
        }
    }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text("End session early?") },
            text = { Text("It will still be logged if over 30 seconds.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmStop = false
                    MeditationController.stop(ctx)
                }) { Text("End session") }
            },
            dismissButton = { TextButton(onClick = { confirmStop = false }) { Text("Keep going") } }
        )
    }
}

@Composable
private fun SetupView(onBack: (() -> Unit)?, completedSec: Int, dismissDone: () -> Unit) {
    val ctx = LocalContext.current
    val repo = repo()
    val scope = rememberCoroutineScope()
    val today = T.today()

    val sessions by remember { repo.dao.meditationsBetween(T.key(today.minusDays(120)), T.key(today)) }
        .collectAsState(initial = emptyList())
    val previewing by MeditationController.previewingId.collectAsState()

    var minutes by rememberSaveable { mutableIntStateOf(10) }
    var soundId by rememberSaveable { mutableStateOf(MeditationSounds.SILENCE) }
    var endBell by rememberSaveable { mutableStateOf(true) }
    var guide by rememberSaveable { mutableStateOf(true) }
    var showCustom by remember { mutableStateOf(false) }
    var customText by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    var askedNotif by rememberSaveable { mutableStateOf(false) }

    val sounds: List<MeditationSound> = remember(refresh) { MeditationSounds.all(ctx) }
    if (sounds.none { it.id == soundId }) soundId = MeditationSounds.SILENCE

    fun doStart() {
        MeditationController.stopPreview()
        MeditationController.start(ctx, minutes, soundId, endBell)
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { doStart() }
    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            MeditationSounds.addCustom(ctx, uri, displayName(ctx, uri))
            refresh++
        }
    }

    // Stats
    val todayKey = T.key(today)
    val todaySec = sessions.filter { it.date == todayKey }.sumOf { it.actualSec }
    val daysWith: Set<String> = sessions.filter { it.actualSec >= 60 }.map { it.date }.toSet()
    var streak = 0
    var d: LocalDate = if (todayKey in daysWith) today else today.minusDays(1)
    while (T.key(d) in daysWith) {
        streak++
        d = d.minusDays(1)
    }
    val weekFrom = T.key(today.minusDays(6))
    val weekCount = sessions.count { it.date >= weekFrom }

    Page(title = "Meditation", subtitle = "Calm the body, calm the stomach", onBack = onBack) {
        if (completedSec >= 0) {
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Session complete 🌿", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "${mmss(completedSec)} of calm" + if (completedSec < 30) " (too short to be logged)" else "",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = dismissDone) { Text("Dismiss") }
                }
            }
        }

        GlassCard(contentPadding = 20.dp) {
            Text("🧘 Meditate", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat("Today (min)", (todaySec / 60).toString(), Modifier.weight(1f))
                Stat("Streak (days)", streak.toString(), Modifier.weight(1f))
                Stat("Sessions this week", weekCount.toString(), Modifier.weight(1f))
            }
        }

        SectionCard("Duration") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PRESETS.forEach { m ->
                    GlassChip("$m min", selected = minutes == m, onClick = { minutes = m })
                }
                GlassChip(
                    if (minutes !in PRESETS) "Custom · $minutes min" else "Custom",
                    selected = minutes !in PRESETS,
                    onClick = {
                        customText = minutes.toString()
                        showCustom = true
                    }
                )
            }
        }

        SectionCard("Sound") {
            sounds.forEach { s ->
                SoundTile(
                    sound = s,
                    selected = s.id == soundId,
                    previewing = previewing == s.id,
                    onSelect = { soundId = s.id },
                    onPreview = { MeditationController.preview(ctx, s.id) },
                    onRemove = {
                        if (previewing == s.id) MeditationController.stopPreview()
                        if (soundId == s.id) soundId = MeditationSounds.SILENCE
                        MeditationSounds.removeCustom(ctx, s.id)
                        refresh++
                    }
                )
            }
            OutlinedButton(
                onClick = { pickLauncher.launch(arrayOf("audio/*")) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("🎵 Add your music") }
        }

        SectionCard("Options") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("End bell", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                Switch(checked = endBell, onCheckedChange = { endBell = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Breathing guide (4 s in, 6 s out)", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                Switch(checked = guide, onCheckedChange = { guide = it })
            }
        }

        GradientButton(
            "Start meditation",
            onClick = {
                val needs = Build.VERSION.SDK_INT >= 33 && !askedNotif &&
                    ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                if (needs) {
                    askedNotif = true
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    doStart()
                }
            },
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
        )

        SectionCard("Today") {
            val todays = sessions.filter { it.date == todayKey }
            if (todays.isEmpty()) {
                Text("No sessions yet today.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(
                    "${todaySec / 60} min across ${todays.size} session${if (todays.size == 1) "" else "s"}. Daily goal: ${DAILY_GOAL_MIN.toInt()} min.",
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        SectionCard("History") {
            val days = (0..13).map { today.minusDays((13 - it).toLong()) }
            val perDay = days.map { day ->
                sessions.filter { it.date == T.key(day) }.sumOf { it.actualSec } / 60f
            }
            Text("Minutes per day (dashed line: ${DAILY_GOAL_MIN.toInt()} min goal)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            BarChart(
                values = perDay,
                labels = days.map { T.short(it) },
                maxValue = maxOf(perDay.maxOrNull() ?: 0f, DAILY_GOAL_MIN) * 1.15f,
                target = DAILY_GOAL_MIN
            )
            val recent = sessions.take(20)
            if (recent.isEmpty()) {
                Text("Your sessions will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            recent.forEach { s ->
                SessionRow(s) { scope.launch { repo.deleteMeditation(s) } }
            }
        }

        GlassCard {
            Text("Tips", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("• Sit upright, not right after a meal. Wait an hour or two to keep acid down.", color = MaterialTheme.colorScheme.onSurface)
            Text("• Belly breathing helps reflux: it relaxes the diaphragm around the stomach valve.", color = MaterialTheme.colorScheme.onSurface)
        }
    }

    if (showCustom) {
        val parsed = customText.toIntOrNull()
        val valid = parsed != null && parsed in 1..180
        AlertDialog(
            onDismissRequest = { showCustom = false },
            title = { Text("Custom duration") },
            text = {
                OutlinedTextField(
                    value = customText,
                    onValueChange = { v -> customText = v.filter { it.isDigit() }.take(3) },
                    label = { Text("Minutes (1-180)") },
                    singleLine = true,
                    isError = customText.isNotEmpty() && !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            },
            confirmButton = {
                TextButton(enabled = valid, onClick = {
                    minutes = parsed ?: minutes
                    showCustom = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showCustom = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SoundTile(
    sound: MeditationSound,
    selected: Boolean,
    previewing: Boolean,
    onSelect: () -> Unit,
    onPreview: () -> Unit,
    onRemove: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    val border = if (selected) Modifier.border(2.dp, Glass.accent, shape) else Modifier.border(1.dp, Glass.border, shape)
    val tint = if (selected) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) else Modifier.background(Glass.surface)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(tint)
            .then(border)
            .clickable(onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(sound.emoji, fontSize = 24.sp)
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(sound.name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(sound.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (sound.id != MeditationSounds.SILENCE) {
            Text(
                if (previewing) "■" else "▶",
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onPreview)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
        if (sound.isCustom) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Delete, contentDescription = "Remove track", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SessionRow(s: MeditationSession, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                "${T.day(T.parse(s.date))} · ${T.time(s.startTime)}",
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "${mmss(s.actualSec)} · ${s.sound} · " + if (s.completed) "✓ completed" else "stopped early",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete session", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
