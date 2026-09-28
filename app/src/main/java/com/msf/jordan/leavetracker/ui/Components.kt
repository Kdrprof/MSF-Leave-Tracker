package com.msf.jordan.leavetracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msf.jordan.leavetracker.logic.Issue
import com.msf.jordan.leavetracker.logic.IssueLevel
import com.msf.jordan.leavetracker.logic.LeaveType
import com.msf.jordan.leavetracker.logic.Rules
import com.msf.jordan.leavetracker.logic.Tr
import com.msf.jordan.leavetracker.logic.tr
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

val Navy = Color(0xFF1E3A8A)
val Amber = Color(0xFFF59E0B)
val BalanceGreen = Color(0xFF34D399)

private val LightColors = lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE4FF),
    onPrimaryContainer = Color(0xFF0B1A4A),
    secondary = Color(0xFF10B981),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3F1FF),
    onSecondaryContainer = Color(0xFF0B2A4A),
    background = Color(0xFFF6F7FB),
    surface = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9BCFF),
    onPrimary = Color(0xFF0B1A4A),
    primaryContainer = Color(0xFF26356B),
    onPrimaryContainer = Color(0xFFDCE4FF),
    secondary = Color(0xFF34D399),
    secondaryContainer = Color(0xFF1C3350),
    onSecondaryContainer = Color(0xFFD6E8FF),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}

fun LeaveType.color(): Color = Color(colorHex)

fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

fun Long.utcMillisToLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/** Small grey helper line under a field. */
@Composable
fun Hint(text: String, modifier: Modifier = Modifier) {
    Row(modifier.padding(top = 4.dp), verticalAlignment = Alignment.Top) {
        Icon(Icons.Filled.Info, null, Modifier.size(16.dp).padding(top = 2.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SectionCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
fun IssueBox(issue: Issue) {
    val (bg, fg, tint) = when (issue.level) {
        IssueLevel.ERROR -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            MaterialTheme.colorScheme.error,
        )
        IssueLevel.WARNING -> Triple(Amber.copy(alpha = 0.18f), MaterialTheme.colorScheme.onSurface, Amber)
        IssueLevel.INFO -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(bg, RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            if (issue.level == IssueLevel.INFO) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            null,
            Modifier.size(18.dp),
            tint = tint,
        )
        Spacer(Modifier.width(8.dp))
        Text(issue.text, color = fg, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun IssuesList(issues: List<Issue>) {
    Column {
        issues.sortedBy { it.level.ordinal }.forEach { IssueBox(it) }
    }
}

/** Colored dot; gets a thin outline so the black «Compassionate» dot is visible in dark mode. */
@Composable
fun TypeDot(type: LeaveType, size: Int = 12) {
    Box(
        Modifier
            .size(size.dp)
            .background(type.color(), CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
    )
}

@Composable
fun TypeSelector(selected: LeaveType, onSelect: (LeaveType) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LeaveType.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { t ->
                    val isSel = t == selected
                    Row(
                        Modifier
                            .weight(1f)
                            .border(
                                width = if (isSel) 2.dp else 1.dp,
                                color = if (isSel) t.color() else MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(12.dp),
                            )
                            .background(if (isSel) t.color().copy(alpha = 0.12f) else Color.Transparent, RoundedCornerShape(12.dp))
                            .clickable { onSelect(t) }
                            .padding(horizontal = 10.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TypeDot(t)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(t.title, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp)
                            if (Tr.arabic) {
                                Text(t.en, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Read-only field that opens a calendar when tapped. */
@Composable
fun DateField(label: String, date: LocalDate, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${Rules.fmtDate(date)}  •  ${Tr.dayName(date)}", style = MaterialTheme.typography.bodyLarge)
        }
        Icon(Icons.Filled.DateRange, null, tint = MaterialTheme.colorScheme.primary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickDialog(initial: LocalDate, onDismiss: () -> Unit, onPicked: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.toUtcMillis())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val ms = state.selectedDateMillis
                if (ms != null) onPicked(ms.utcMillisToLocalDate())
                onDismiss()
            }) { Text(tr("تم", "OK")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("إلغاء", "Cancel")) } },
    ) {
        DatePicker(state = state)
    }
}

/** Day amount for the UI, e.g. «2.5 يوم» / «2.5 days». */
fun daysText(x100: Int): String = Rules.fmtDays(x100) + tr(" يوم", if (x100 == 100) " day" else " days")
