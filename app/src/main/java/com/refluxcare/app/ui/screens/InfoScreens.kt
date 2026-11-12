package com.refluxcare.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.refluxcare.app.ui.Glass
import com.refluxcare.app.ui.GlassCard
import com.refluxcare.app.ui.IconBadge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
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
private fun TitleWithBadge(emoji: String, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        IconBadge(emoji)
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun GradientPill(text: String) {
    Text(
        text,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Glass.accent)
            .padding(horizontal = 8.dp, vertical = 5.dp)
    )
}

@Composable
fun RoutineScreen(onBack: () -> Unit) {
    Page(title = "Daily routine", subtitle = "Sleep 9:30–10 pm · Wake 5:45–6 am", onBack = onBack) {
        val lineColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        Column {
            Content.routine.forEach { item ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    Column(
                        Modifier
                            .width(84.dp)
                            .fillMaxHeight()
                            .drawBehind {
                                val x = size.width / 2f
                                drawLine(
                                    lineColor,
                                    start = Offset(x, 16.dp.toPx()),
                                    end = Offset(x, size.height),
                                    strokeWidth = 2.dp.toPx()
                                )
                            },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        GradientPill(item.time)
                    }
                    GlassCard(Modifier.weight(1f).padding(bottom = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("⏰", fontSize = 16.sp)
                            Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }
                        item.details.forEach { Bullet(it) }
                    }
                }
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

private val guideEmojis = listOf("📖", "🌿", "🍽️", "🌙", "💧", "🧘", "🏃", "⚠️")

@Composable
fun GuideScreen(onBack: () -> Unit) {
    Page(title = "Reflux guide", subtitle = "Why each rule matters", onBack = onBack) {
        Content.guide.forEachIndexed { i, section ->
            GuideCard(guideEmojis[i % guideEmojis.size], section.title, null, i == 0) {
                section.points.forEach { Bullet(it) }
            }
        }
        GuideCard("📋", "All tracked rules", "These are detected automatically from your logs", false) {
            Content.rules.forEach { r ->
                Column {
                    Text(r.title, fontWeight = FontWeight.SemiBold)
                    Text(r.why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Glass card with an emoji badge that expands on tap. */
@Composable
private fun GuideCard(
    emoji: String,
    title: String,
    subtitle: String?,
    startExpanded: Boolean,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    var open by androidx.compose.runtime.saveable.rememberSaveable(title) { mutableStateOf(startExpanded) }
    GlassCard {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IconBadge(emoji)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(
                if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (open) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.primary
            )
        }
        if (open) content()
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
        SectionCard(title = "⏱️  Meal timing") {
            Bullet("Breakfast 8:00–8:30 am · Lunch 1:00–1:30 pm · Snack 4:30 pm · Dinner 6:30–7:00 pm")
            Bullet("Lunch is the biggest meal; dinner is the lightest.")
            Bullet("Eat slowly and stop at 80% full. Vajrasana 5–10 min after meals.")
        }

        Content.foods.forEach { (type, list) ->
            val mine = custom.filter { it.mealType == type }
            GuideCard(
                emoji = mealEmoji(type),
                title = MealTypes.label(type),
                subtitle = "${list.size + mine.size} options",
                startExpanded = false
            ) {
                list.forEach { f ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Glass.surface)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
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
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Glass.surface)
            .padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(name, fontWeight = FontWeight.Medium)
                Text(
                    "Yours",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Glass.accent)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            if (note.isNotBlank()) {
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Remove food") }
    }
}

private fun mealEmoji(type: String): String = when (type) {
    MealTypes.EARLY -> "🌅"
    MealTypes.BREAKFAST -> "🥣"
    MealTypes.MID -> "🍎"
    MealTypes.LUNCH -> "🍛"
    MealTypes.SNACK -> "🥜"
    MealTypes.DINNER -> "🌙"
    else -> "🍽️"
}
