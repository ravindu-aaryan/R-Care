@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.refluxcare.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.refluxcare.app.data.CustomFood
import com.refluxcare.app.data.MealLog
import com.refluxcare.app.data.WorkoutLog
import com.refluxcare.app.data.tagList
import com.refluxcare.app.domain.Content
import com.refluxcare.app.domain.MealTypes
import com.refluxcare.app.domain.Portions
import com.refluxcare.app.domain.RuleIds
import com.refluxcare.app.domain.Tags
import com.refluxcare.app.domain.WorkoutTypes
import com.refluxcare.app.ui.pickTime
import com.refluxcare.app.util.T
import java.time.LocalTime

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
}

/** Add or edit a meal / drink for [date]. */
@Composable
fun MealDialog(
    date: String,
    existing: MealLog?,
    customFoods: List<CustomFood>,
    onDismiss: () -> Unit,
    onSave: (MealLog) -> Unit
) {
    val ctx = LocalContext.current
    val day = T.parse(date)
    val isToday = day == T.today()

    var type by remember { mutableStateOf(existing?.mealType ?: MealTypes.guess(LocalTime.now().hour)) }
    var time by remember {
        mutableStateOf(existing?.time ?: if (isToday) System.currentTimeMillis() else T.millis(day, MealTypes.defaultHour(type), 0))
    }
    var items by remember { mutableStateOf(existing?.items ?: "") }
    var portion by remember { mutableStateOf(existing?.portion ?: Portions.NORMAL) }
    var tags by remember { mutableStateOf(existing?.tagList()?.toSet() ?: emptySet()) }
    var note by remember { mutableStateOf(existing?.note ?: "") }

    val suggestions = remember(type, customFoods) {
        (Content.foods[type].orEmpty().map { it.name } +
            customFoods.filter { it.mealType == type || it.mealType == "ANY" }.map { it.name }).distinct()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Log meal / drink" else "Edit meal") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FieldLabel("Meal")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MealTypes.all.forEach { (id, label) ->
                        FilterChip(
                            selected = type == id,
                            onClick = {
                                type = id
                                if (existing == null && !isToday) time = T.millis(day, MealTypes.defaultHour(id), 0)
                            },
                            label = { Text(label) }
                        )
                    }
                }

                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Time: ${T.time(time)}", modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        pickTime(ctx, time, "Meal time") { h, m -> time = T.millis(day, h, m) }
                    }) { Text("Change") }
                }

                OutlinedTextField(
                    value = items,
                    onValueChange = { items = it },
                    label = { Text("What did you eat / drink?") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (suggestions.isNotEmpty()) {
                    Text("Tap to add:", style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        suggestions.forEach { s ->
                            SuggestionChip(
                                onClick = { items = if (items.isBlank()) s else "${items.trimEnd().trimEnd(',')}, $s" },
                                label = { Text(s) }
                            )
                        }
                    }
                }

                FieldLabel("Portion")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Portions.all.forEach { (id, label) ->
                        FilterChip(selected = portion == id, onClick = { portion = id }, label = { Text(label) })
                    }
                }

                FieldLabel("Tags (used to detect rule breaks)")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Tags.all.forEach { (id, label) ->
                        FilterChip(
                            selected = id in tags,
                            onClick = { tags = if (id in tags) tags - id else tags + id },
                            label = { Text(label) }
                        )
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = items.isNotBlank(),
                onClick = {
                    onSave(
                        MealLog(
                            id = existing?.id ?: 0,
                            date = date,
                            mealType = type,
                            time = time,
                            items = items.trim(),
                            portion = portion,
                            tags = tags.joinToString(","),
                            note = note.trim()
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Log a walk with minutes and steps. */
@Composable
fun WalkDialog(date: String, onDismiss: () -> Unit, onSave: (WorkoutLog) -> Unit) {
    val ctx = LocalContext.current
    val day = T.parse(date)
    var minutes by remember { mutableStateOf("20") }
    var steps by remember { mutableStateOf("") }
    var time by remember {
        mutableStateOf(if (day == T.today()) System.currentTimeMillis() else T.millis(day, 19, 15))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log walk") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Time: ${T.time(time)}", modifier = Modifier.weight(1f))
                    TextButton(onClick = { pickTime(ctx, time, "Walk time") { h, m -> time = T.millis(day, h, m) } }) {
                        Text("Change")
                    }
                }
                OutlinedTextField(
                    value = minutes,
                    onValueChange = { v -> minutes = v.filter { it.isDigit() }.take(3) },
                    label = { Text("Minutes") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = steps,
                    onValueChange = { v -> steps = v.filter { it.isDigit() }.take(6) },
                    label = { Text("Steps (from your phone's step counter)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    WorkoutLog(
                        date = date,
                        type = WorkoutTypes.WALK,
                        time = time,
                        durationMin = minutes.toIntOrNull() ?: 0,
                        steps = steps.toIntOrNull() ?: 0,
                        details = "Walk"
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Manually note a rule you broke. */
@Composable
fun RuleBreakDialog(
    defaultDate: String,
    onDismiss: () -> Unit,
    onSave: (date: String, ruleId: String, detail: String) -> Unit
) {
    var date by remember { mutableStateOf(defaultDate) }
    var ruleId by remember { mutableStateOf(RuleIds.OTHER) }
    var detail by remember { mutableStateOf("") }
    val dates = remember(defaultDate) {
        val base = T.parse(defaultDate)
        (0..6).map { T.key(base.minusDays(it.toLong())) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log a rule break") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FieldLabel("Day")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    dates.forEach { d ->
                        FilterChip(selected = date == d, onClick = { date = d }, label = { Text(T.day(T.parse(d))) })
                    }
                }
                FieldLabel("Which rule?")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Content.rules.forEach { r ->
                        FilterChip(selected = ruleId == r.id, onClick = { ruleId = r.id }, label = { Text(r.title) })
                    }
                }
                OutlinedTextField(
                    value = detail,
                    onValueChange = { detail = it },
                    label = { Text("What happened? (e.g. office party, travel)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = ruleId != RuleIds.OTHER || detail.isNotBlank(),
                onClick = { onSave(date, ruleId, detail.trim()) }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Add your own healthy food to the diet list. */
@Composable
fun AddFoodDialog(onDismiss: () -> Unit, onSave: (CustomFood) -> Unit) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("ANY") }
    var note by remember { mutableStateOf("") }
    val options = listOf("ANY" to "Any meal") + MealTypes.all.filter { it.first != MealTypes.OTHER }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add healthy food") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Food name") },
                    modifier = Modifier.fillMaxWidth()
                )
                FieldLabel("Best for")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    options.forEach { (id, label) ->
                        FilterChip(selected = type == id, onClick = { type = id }, label = { Text(label) })
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (portion, recipe tip…)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                onSave(CustomFood(name = name.trim(), mealType = type, note = note.trim()))
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
