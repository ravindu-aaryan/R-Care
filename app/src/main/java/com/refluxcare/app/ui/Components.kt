package com.refluxcare.app.ui

import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.refluxcare.app.RefluxApp
import com.refluxcare.app.data.Repository
import com.refluxcare.app.util.T
import java.time.LocalDateTime

@Composable
fun repo(): Repository = (LocalContext.current.applicationContext as RefluxApp).repo

/** Scrollable page with a header. */
@Composable
fun Page(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            actions()
        }
        content()
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun SectionCard(
    title: String?,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null || action != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    action?.invoke()
                }
            }
            content()
        }
    }
}

/** Card whose body is shown when tapped. */
@Composable
fun ExpandableCard(
    title: String,
    subtitle: String? = null,
    startExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    var open by rememberSaveable(title) { mutableStateOf(startExpanded) }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { open = !open },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (subtitle != null) {
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Icon(
                    if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (open) "Collapse" else "Expand"
                )
            }
            if (open) content()
        }
    }
}

@Composable
fun Bullet(text: String, prefix: String = "•") {
    Row {
        Text("$prefix  ", style = MaterialTheme.typography.bodyMedium)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun Pill(text: String, color: Color = MaterialTheme.colorScheme.secondaryContainer) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .background(color, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

@Composable
fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Simple bar chart drawn on a Canvas. null values are drawn as empty slots. */
@Composable
fun BarChart(
    values: List<Float?>,
    labels: List<String>,
    maxValue: Float,
    color: Color = MaterialTheme.colorScheme.primary,
    target: Float? = null,
    height: Dp = 140.dp
) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val targetColor = MaterialTheme.colorScheme.tertiary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val paint = remember(labelColor) {
        android.graphics.Paint().apply {
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
            this.color = labelColor.toArgb()
        }
    }
    Canvas(Modifier.fillMaxWidth().height(height)) {
        val labelSpace = 16.dp.toPx()
        val chartH = size.height - labelSpace
        val n = values.size.coerceAtLeast(1)
        val slot = size.width / n
        val barW = slot * 0.65f
        paint.textSize = 10.sp.toPx()
        val step = if (n > 10) (n + 6) / 7 else 1
        values.forEachIndexed { i, v ->
            val x = i * slot + (slot - barW) / 2f
            drawRect(track, topLeft = Offset(x, 0f), size = Size(barW, chartH), alpha = 0.35f)
            if (v != null && maxValue > 0f) {
                val h = (v / maxValue).coerceIn(0f, 1f) * chartH
                drawRect(color, topLeft = Offset(x, chartH - h), size = Size(barW, h))
            }
            if (i % step == 0 && i < labels.size) {
                drawContext.canvas.nativeCanvas.drawText(labels[i], x + barW / 2f, size.height - 2.dp.toPx(), paint)
            }
        }
        if (target != null && maxValue > 0f) {
            val y = chartH - (target / maxValue).coerceIn(0f, 1f) * chartH
            drawLine(
                targetColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
            )
        }
    }
}

/** Shows the Android time picker. */
fun pickTime(context: Context, initial: Long?, title: String? = null, onPicked: (Int, Int) -> Unit) {
    val t = initial?.let { T.toLdt(it) } ?: LocalDateTime.now()
    val dialog = TimePickerDialog(context, { _, h, m -> onPicked(h, m) }, t.hour, t.minute, false)
    if (title != null) dialog.setTitle(title)
    dialog.show()
}
