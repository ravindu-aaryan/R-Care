@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.refluxcare.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.refluxcare.app.domain.Content
import com.refluxcare.app.ui.BarChart
import com.refluxcare.app.ui.ExpandableCard
import com.refluxcare.app.ui.Page
import com.refluxcare.app.ui.Pill
import com.refluxcare.app.ui.SectionCard
import com.refluxcare.app.ui.repo
import com.refluxcare.app.util.T
import kotlinx.coroutines.launch

@Composable
fun RuleBreaksScreen() {
    val repo = repo()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var range by rememberSaveable { mutableIntStateOf(7) }
    var showAdd by remember { mutableStateOf(false) }
    val today = T.today()

    val breaks by remember(range) {
        if (range == 0) repo.dao.allBreaksFlow()
        else repo.dao.breaksBetween(T.key(today.minusDays((range - 1).toLong())), T.key(today))
    }.collectAsState(initial = emptyList())

    Page(
        title = "Rule breaks",
        subtitle = "Every time the routine was broken",
        actions = { TextButton(onClick = { showAdd = true }) { Text("Log one") } }
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(7 to "7 days", 30 to "30 days", 0 to "All").forEach { (r, label) ->
                FilterChip(selected = range == r, onClick = { range = r }, label = { Text(label) })
            }
        }

        SectionCard(title = "Summary") {
            val auto = breaks.count { it.source == "auto" }
            Text("${breaks.size} rule breaks ($auto auto-detected, ${breaks.size - auto} logged by you)", fontWeight = FontWeight.Medium)
            if (range in 1..30) {
                val days = (0 until range).map { today.minusDays((range - 1 - it).toLong()) }
                val byDate = breaks.groupBy { it.date }
                BarChart(
                    values = days.map { (byDate[T.key(it)]?.size ?: 0).toFloat() },
                    labels = days.map { it.dayOfMonth.toString() },
                    maxValue = maxOf(4f, days.maxOfOrNull { (byDate[T.key(it)]?.size ?: 0).toFloat() } ?: 4f),
                    color = MaterialTheme.colorScheme.error,
                    height = 110.dp
                )
            }
            val counts = breaks.groupBy { it.title }.map { it.key to it.value.size }.sortedByDescending { it.second }
            counts.take(8).forEach { (title, n) ->
                Row {
                    Text(title, modifier = Modifier.weight(1f))
                    Text("$n×", fontWeight = FontWeight.Bold)
                }
            }
        }

        val grouped = breaks.groupBy { it.date }.toSortedMap(compareByDescending { it })
        if (grouped.isEmpty()) {
            SectionCard(title = null) { Text("No rule breaks in this period. Keep it up!") }
        }
        grouped.forEach { (date, list) ->
            SectionCard(title = T.day(T.parse(date))) {
                list.forEach { b ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(b.title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                                Pill(if (b.source == "auto") "Auto" else "Manual")
                            }
                            if (b.detail.isNotBlank()) Text(b.detail, style = MaterialTheme.typography.bodySmall)
                        }
                        if (b.source == "manual") {
                            IconButton(onClick = { scope.launch { repo.deleteBreak(b) } }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete")
                            }
                        }
                    }
                }
            }
        }

        ExpandableCard(title = "What counts as a rule break?", subtitle = "Auto-detected ones update when you edit your logs") {
            Content.rules.forEach { r ->
                Column {
                    Text(r.title, fontWeight = FontWeight.SemiBold)
                    Text(r.why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (showAdd) {
        RuleBreakDialog(
            defaultDate = T.todayKey(),
            onDismiss = { showAdd = false },
            onSave = { d, id, detail ->
                showAdd = false
                scope.launch {
                    repo.addManualBreak(d, id, detail)
                    Toast.makeText(ctx, "Rule break noted", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}
