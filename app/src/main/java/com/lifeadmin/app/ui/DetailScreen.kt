package com.lifeadmin.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeadmin.app.data.Cycle
import com.lifeadmin.app.data.Item
import com.lifeadmin.app.data.daysLeft
import com.lifeadmin.app.data.dueDate
import com.lifeadmin.app.data.reminderDayList
import com.lifeadmin.app.ui.theme.DmMono
import com.lifeadmin.app.ui.theme.Extra
import com.lifeadmin.app.util.Format
import kotlinx.coroutines.launch
import java.time.Instant
import com.lifeadmin.app.data.Kind
import com.lifeadmin.app.data.checklistEntries
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Checkbox
import androidx.compose.ui.text.style.TextDecoration
import com.lifeadmin.app.reminders.Schedule
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.time.ZoneId
import kotlin.math.abs

@Composable
fun DetailScreen(
    vm: AppViewModel,
    itemId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
) {
    val itemFlow = remember(itemId) { vm.observeItem(itemId) }
    val historyFlow = remember(itemId) { vm.observeHistory(itemId) }
    val item by itemFlow.collectAsStateWithLifecycle(initialValue = null)
    val history by historyFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val today = remember { LocalDate.now() }
    val extra = Extra.colors
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val context = androidx.compose.ui.platform.LocalContext.current
    UndoSnackbarEffect(vm, snackbar)
    var confirmDelete by remember { mutableStateOf(false) }

    // If the item disappears (deleted from the edit screen), leave this screen.
    var seen by remember { mutableStateOf(false) }
    LaunchedEffect(item) {
        if (item != null) seen = true else if (seen) onBack()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        val current = item ?: return@Scaffold
        val now = LocalDateTime.now()
        val occurrence = Schedule.occurrence(current, vm.defaultMinute)
        val days = ChronoUnit.DAYS.between(today, occurrence.toLocalDate())
        val urgency = if (current.done) Urgency.LATER else itemUrgency(current, occurrence, now)
        val uc = urgency.colors()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HeaderIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
                Spacer(Modifier.weight(1f))
                HeaderIconButton(
                    if (current.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    if (current.pinned) "Unpin" else "Pin to top"
                ) { vm.togglePin(current) }
                Spacer(Modifier.width(8.dp))
                HeaderIconButton(Icons.Outlined.Share, "Share") {
                    context.startActivity(
                        android.content.Intent.createChooser(
                            android.content.Intent(android.content.Intent.ACTION_SEND)
                                .setType("text/plain")
                                .putExtra(android.content.Intent.EXTRA_TEXT, shareText(current, vm.defaultMinute)),
                            "Share"
                        )
                    )
                }
                Spacer(Modifier.width(8.dp))
                HeaderIconButton(Icons.Outlined.Edit, "Edit", onEdit)
            }

            // Hero: category, title, countdown ring
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (urgency == Urgency.OVERDUE) uc.bg else extra.laterBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            current.category.icon(), null,
                            tint = if (urgency == Urgency.OVERDUE) uc.fg else extra.later,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "${current.category.label} · ${current.kind.label}",
                        color = extra.muted,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(current.title, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 30.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CountdownRing(item = current, days = days, urgency = urgency)
                    Spacer(Modifier.width(18.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            when {
                                current.done -> "Completed"
                                current.dueMinute != null -> Format.badge(current, occurrence, now)
                                else -> Format.dueSentence(current.category.dueVerb, days)
                            },
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = uc.fg
                        )
                        Text(
                            Format.date(current.dueDate()) +
                                (current.dueMinute?.let { " · " + Format.time(it) } ?: ""),
                            color = extra.muted,
                            fontSize = 14.sp
                        )
                        current.amount?.takeIf { current.kind != Kind.REMINDER }?.let {
                            Text(
                                Format.money(it, current.currency),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }

            if (current.done) {
                AppCard {
                    Text(
                        "Completed" + (current.doneAt?.let {
                            " on " + Format.date(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate())
                        } ?: ""),
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = { vm.reopen(current) }) { Text("Reopen") }
                }
            } else {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        vm.completeWithFeedback(current)
                        onBack()
                    },
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = extra.ink, contentColor = extra.onInk),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(current.kind.doneLabel, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            val steps = current.checklistEntries()
            if (steps.isNotEmpty()) {
                SectionLabel("Checklist · ${steps.count { it.done }}/${steps.size}", extra.muted)
                AppCard {
                    steps.forEachIndexed { index, step ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val updated = steps.toMutableList()
                                    updated[index] = step.copy(done = !step.done)
                                    vm.updateChecklist(current, updated)
                                }
                        ) {
                            Checkbox(checked = step.done, onCheckedChange = null)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                step.text,
                                fontSize = 15.sp,
                                color = if (step.done) extra.muted else extra.ink,
                                textDecoration = if (step.done) TextDecoration.LineThrough else null
                            )
                        }
                    }
                }
            }

            val streak = Schedule.streak(
                history.map { Instant.ofEpochMilli(it.paidAt).atZone(ZoneId.systemDefault()).toLocalDate() },
                today
            )
            if (current.cycle != Cycle.NONE && streak >= 2) {
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("$streak", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("day streak", fontWeight = FontWeight.Bold)
                            Text("Done every day in a row. Keep it going!", color = extra.muted, fontSize = 13.sp)
                        }
                    }
                }
            }

            SectionLabel("Details", extra.muted)
            AppCard {
                if (current.priority > 0) InfoRow("Priority", "High")
                InfoRow("Time", current.dueMinute?.let { Format.time(it) } ?: "All day")
                InfoRow("Repeats", Format.repeatText(current))
                if (current.cycle != Cycle.NONE) {
                    InfoRow("Moves on automatically", if (current.autoRenew) "Yes" else "No")
                }
                if (current.kind != Kind.REMINDER) current.amount?.let { amount ->
                    InfoRow("Amount", Format.money(amount, current.currency))
                    if (current.cycle != Cycle.NONE) {
                        InfoRow(
                            "Per year",
                            Format.money(
                                Math.round(amount * current.cycle.perMonthFor(current.repeatEvery) * 12).toDouble(),
                                current.currency
                            )
                        )
                    }
                }
                if (current.paymentMethod.isNotBlank()) InfoRow("Paid by", current.paymentMethod)
                if (current.reference.isNotBlank()) {
                    InfoRow(if (current.kind == Kind.PAYMENT) "Account no." else "Policy / doc no.", current.reference)
                }
                InfoRow("Alerts", Format.alertsText(current))
                InfoRow("Style", current.alertStyle.label)
                if (current.nagEveryMin > 0 && current.nagCount > 0) {
                    InfoRow("Keep reminding", "Every ${Format.interval(current.nagEveryMin)}, up to ${current.nagCount}×")
                }
                InfoRow(
                    "Added",
                    Format.date(Instant.ofEpochMilli(current.createdAt).atZone(ZoneId.systemDefault()).toLocalDate())
                )
            }

            if (current.notes.isNotBlank()) {
                SectionLabel("Notes", extra.muted)
                AppCard { Text(current.notes, fontSize = 15.sp, lineHeight = 22.sp) }
            }

            SectionLabel(if (current.kind == Kind.PAYMENT) "Payment history" else "History", extra.muted)
            AppCard {
                if (history.isEmpty()) {
                    Text(
                        if (current.cycle != Cycle.NONE) {
                            "Each time you mark this as ${current.kind.doneVerb.lowercase()}, it's recorded here."
                        } else {
                            "Nothing recorded yet."
                        },
                        color = extra.muted,
                        fontSize = 14.sp
                    )
                } else {
                    history.forEachIndexed { index, h ->
                        if (index > 0) HorizontalDivider(color = extra.line)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                Format.timelineDate(LocalDate.ofEpochDay(h.dueEpochDay), today).replace("\n", " "),
                                fontFamily = DmMono,
                                fontSize = 12.sp,
                                color = extra.muted,
                                modifier = Modifier.width(96.dp)
                            )
                            Text(
                                if (h.amount != null) "Paid" else current.kind.doneVerb,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            h.amount?.let { Text(Format.money(it, h.currency), fontWeight = FontWeight.Bold) }
                        }
                    }
                    val total = history.mapNotNull { it.amount }.sum()
                    if (total > 0) {
                        HorizontalDivider(color = extra.line)
                        InfoRow("Total paid", Format.money(total, current.currency))
                    }
                }
            }

            TextButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Delete item", color = extra.overdue, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(24.dp))
        }

        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("Delete \"${current.title}\"?") },
                text = { Text("This removes the item and its reminders.") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDelete = false
                        vm.delete(current.id) // the screen closes itself once the item is gone
                    }) { Text("Delete", color = extra.overdue) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
                }
            )
        }
    }
}

/** Plain-text version of an item for sharing with someone. */
private fun shareText(item: Item, defaultMinute: Int): String {
    val occ = Schedule.occurrence(item, defaultMinute)
    val lines = mutableListOf(item.title)
    lines += (if (item.kind == Kind.RENEWAL) "Expires: " else "Due: ") + Format.date(occ.toLocalDate()) +
        (item.dueMinute?.let { " at " + Format.time(it) } ?: "")
    if (item.cycle != Cycle.NONE) lines += Format.repeatText(item)
    if (item.kind != Kind.REMINDER) item.amount?.let { lines += "Amount: " + Format.money(it, item.currency) }
    if (item.reference.isNotBlank()) lines += "Ref: " + item.reference
    val steps = item.checklistEntries()
    if (steps.isNotEmpty()) lines += steps.joinToString("\n") { (if (it.done) "[x] " else "[ ] ") + it.text }
    if (item.notes.isNotBlank()) lines += item.notes
    lines += "— shared from LifeAdmin"
    return lines.joinToString("\n")
}

private fun reminderSummary(item: Item): String {
    val days = item.reminderDayList().sortedDescending()
    if (days.isEmpty()) return "Off"
    return days.joinToString(", ") { d ->
        when (d) {
            0 -> "on the day"
            1 -> "1 day"
            else -> "$d days"
        }
    }
}

/** Ring that fills as the due date approaches; animates in when the screen opens. */
@Composable
private fun CountdownRing(item: Item, days: Long, urgency: Urgency) {
    val extra = Extra.colors
    val uc = urgency.colors()
    val window = when (item.cycle) {
        Cycle.MINUTES, Cycle.HOURS -> 1.0
        Cycle.DAILY -> 1.0 * item.repeatEvery.coerceAtLeast(1)
        Cycle.WEEKLY -> 7.0
        Cycle.MONTHLY -> 30.0
        Cycle.QUARTERLY -> 91.0
        Cycle.YEARLY, Cycle.NONE -> 365.0
    }
    val target = if (days < 0) 1f else (1.0 - days / window).coerceIn(0.03, 1.0).toFloat()
    val progress = remember { Animatable(0f) }
    LaunchedEffect(target) {
        progress.animateTo(target, tween(durationMillis = 900, easing = FastOutSlowInEasing))
    }
    val ringColor = if (urgency == Urgency.LATER) MaterialTheme.colorScheme.primary else uc.dot

    Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 10.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = extra.line,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(stroke)
            )
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * progress.value,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                abs(days).toString(),
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 30.sp
            )
            Text(
                when {
                    days < 0 -> "days late"
                    days == 0L -> "today"
                    days == 1L -> "day left"
                    else -> "days left"
                },
                fontSize = 11.sp,
                color = extra.muted
            )
        }
    }
}
