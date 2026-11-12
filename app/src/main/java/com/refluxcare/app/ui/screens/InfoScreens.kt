package com.refluxcare.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.refluxcare.app.domain.Content
import com.refluxcare.app.domain.MealTypes
import com.refluxcare.app.ui.Bullet
import com.refluxcare.app.ui.ExpandableCard
import com.refluxcare.app.ui.Page
import com.refluxcare.app.ui.Pill
import com.refluxcare.app.ui.SectionCard
import com.refluxcare.app.ui.repo
import kotlinx.coroutines.launch

@Composable
fun RoutineScreen(onBack: () -> Unit) {
    Page(title = "Daily routine", subtitle = "Sleep 9:30–10 pm · Wake 5:45–6 am", onBack = onBack) {
        Content.routine.forEach { item ->
            SectionCard(title = null) {
                Text(item.time, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                item.details.forEach { Bullet(it) }
            }
        }
        SectionCard(title = "Weekly exercise plan") {
            Content.weeklyPlan.forEach { (day, plan) ->
                Row {
                    Text(day, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(110.dp))
                    Text(plan)
                }
            }
        }
        SectionCard(title = "How to start") {
            Content.startPlan.forEachIndexed { i, s -> Bullet(s, prefix = "${i + 1}.") }
        }
    }
}

@Composable
fun GuideScreen(onBack: () -> Unit) {
    Page(title = "Reflux guide", subtitle = "Why each rule matters", onBack = onBack) {
        Content.guide.forEachIndexed { i, section ->
            ExpandableCard(title = section.title, startExpanded = i == 0) {
                section.points.forEach { Bullet(it) }
            }
        }
        ExpandableCard(title = "All tracked rules", subtitle = "These are detected automatically from your logs") {
            Content.rules.forEach { r ->
                Column {
                    Text(r.title, fontWeight = FontWeight.SemiBold)
                    Text(r.why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun DietScreen() {
    val repo = repo()
    val scope = rememberCoroutineScope()
    val custom by remember { repo.dao.customFoods() }.collectAsState(initial = emptyList())
    var showAdd by remember { mutableStateOf(false) }

    Page(
        title = "Diet",
        subtitle = "Reflux-friendly foods for each meal",
        actions = {
            TextButton(onClick = { showAdd = true }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("Add food")
            }
        }
    ) {
        SectionCard(title = "Meal timing") {
            Bullet("Breakfast 8:00–8:30 am · Lunch 1:00–1:30 pm · Snack 4:30 pm · Dinner 6:30–7:00 pm")
            Bullet("Lunch is the biggest meal; dinner is the lightest.")
            Bullet("Eat slowly and stop at 80% full. Vajrasana 5–10 min after meals.")
        }

        Content.foods.forEach { (type, list) ->
            val mine = custom.filter { it.mealType == type }
            ExpandableCard(
                title = MealTypes.label(type),
                subtitle = "${list.size + mine.size} options",
                startExpanded = false
            ) {
                list.forEach { f ->
                    Column {
                        Text(f.name, fontWeight = FontWeight.Medium)
                        if (f.note.isNotBlank()) {
                            Text(f.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                mine.forEach { f -> CustomFoodRow(f.name, f.note) { scope.launch { repo.deleteFood(f) } } }
            }
        }

        val anyMeal = custom.filter { it.mealType == "ANY" || it.mealType == MealTypes.OTHER }
        SectionCard(
            title = "Your added foods",
            action = { TextButton(onClick = { showAdd = true }) { Text("Add") } }
        ) {
            if (custom.isEmpty()) {
                Text("Add your own healthy foods. They also appear as quick picks when you log a meal.")
            } else {
                Text("${custom.size} added in total. Foods for a specific meal show in that meal's list above.",
                    style = MaterialTheme.typography.bodySmall)
            }
            anyMeal.forEach { f -> CustomFoodRow(f.name, f.note) { scope.launch { repo.deleteFood(f) } } }
        }

        SectionCard(title = "Foods to limit") {
            Content.limitFoods.forEach { Bullet(it, prefix = "✕") }
        }
    }

    if (showAdd) {
        AddFoodDialog(
            onDismiss = { showAdd = false },
            onSave = { f -> showAdd = false; scope.launch { repo.addFood(f) } }
        )
    }
}

@Composable
private fun CustomFoodRow(name: String, note: String, onDelete: () -> Unit) {
    HorizontalDivider()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(name, fontWeight = FontWeight.Medium)
                Pill("Yours")
            }
            if (note.isNotBlank()) {
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Remove food") }
    }
}
