package com.refluxcare.app.ui

import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.style.TextAlign
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

/** Full-screen gradient background with soft colour blobs; place the whole app inside. */
@Composable
fun AppBackground(content: @Composable () -> Unit) {
    val blobs = Glass.blobColors
    Box(Modifier.fillMaxSize().background(Glass.background)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val r1 = w * 0.9f
            val r2 = w * 0.8f
            val r3 = w * 0.7f
            val c1 = Offset(w * 0.1f, h * 0.12f)
            val c2 = Offset(w * 0.95f, h * 0.45f)
            val c3 = Offset(w * 0.25f, h * 0.92f)
            drawCircle(Brush.radialGradient(listOf(blobs[0], Color.Transparent), center = c1, radius = r1), radius = r1, center = c1)
            drawCircle(Brush.radialGradient(listOf(blobs[1], Color.Transparent), center = c2, radius = r2), radius = r2, center = c2)
            drawCircle(Brush.radialGradient(listOf(blobs[2], Color.Transparent), center = c3, radius = r3), radius = r3, center = c3)
        }
        content()
    }
}

/** Translucent rounded glass card. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    val highlight = Glass.highlight
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Glass.surface)
            .background(Brush.verticalGradient(listOf(highlight, Color.Transparent)))
            .border(1.dp, Glass.border, shape)
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

/** Pill-shaped button with the accent gradient. */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(50))
            .background(Glass.accent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
    }
}

/** Selectable pill chip. */
@Composable
fun GlassChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val base = Modifier.clip(shape)
    val styled = if (selected) {
        base.background(Glass.accent)
    } else {
        base.background(Glass.surface).border(1.dp, Glass.border, shape)
    }
    Box(
        styled.clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

/** Small circular gradient badge holding an emoji or 1-2 characters. */
@Composable
fun IconBadge(text: String) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Glass.accent),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 18.sp, color = Color.White, textAlign = TextAlign.Center)
    }
}

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
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
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
    GlassCard(modifier) {
        if (title != null || action != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (title != null) {
                    Box(
                        Modifier
                            .size(width = 4.dp, height = 18.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                Brush.verticalGradient(
                                    listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                                )
                            )
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    title ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                action?.invoke()
            }
        }
        content()
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
    GlassCard {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open },
            verticalAlignment = Alignment.CenterVertically
        ) {
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
fun Bullet(text: String, prefix: String = "•") {
    Row {
        Text("$prefix  ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun Pill(text: String, color: Color = MaterialTheme.colorScheme.secondaryContainer) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .background(color.copy(alpha = 0.75f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    )
}

@Composable
fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )
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
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
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
        val radius = (barW / 2f).coerceAtMost(6.dp.toPx())
        paint.textSize = 10.sp.toPx()
        val step = if (n > 10) (n + 6) / 7 else 1
        values.forEachIndexed { i, v ->
            val x = i * slot + (slot - barW) / 2f
            drawRoundRect(
                track,
                topLeft = Offset(x, 0f),
                size = Size(barW, chartH),
                cornerRadius = CornerRadius(radius, radius)
            )
            if (v != null && maxValue > 0f) {
                val h = (v / maxValue).coerceIn(0f, 1f) * chartH
                if (h > 0f) {
                    val top = chartH - h
                    val r = radius.coerceAtMost(h)
                    val path = Path().apply {
                        addRoundRect(
                            RoundRect(
                                left = x,
                                top = top,
                                right = x + barW,
                                bottom = chartH,
                                topLeftCornerRadius = CornerRadius(r, r),
                                topRightCornerRadius = CornerRadius(r, r),
                                bottomRightCornerRadius = CornerRadius.Zero,
                                bottomLeftCornerRadius = CornerRadius.Zero
                            )
                        )
                    }
                    drawPath(
                        path,
                        Brush.verticalGradient(
                            listOf(color, color.copy(alpha = 0.5f)),
                            startY = top,
                            endY = chartH
                        )
                    )
                }
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
