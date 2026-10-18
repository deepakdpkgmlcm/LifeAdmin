package com.lifeadmin.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.lifeadmin.app.data.AlertStyle
import com.lifeadmin.app.data.Category
import com.lifeadmin.app.data.Cycle
import com.lifeadmin.app.data.Item
import com.lifeadmin.app.data.Kind
import com.lifeadmin.app.data.Templates
import com.lifeadmin.app.data.alertOffsetList
import com.lifeadmin.app.reminders.AlarmScheduler
import com.lifeadmin.app.reminders.Notifier
import com.lifeadmin.app.ui.theme.Extra
import com.lifeadmin.app.util.Format
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

private val TIMED_ALERTS = listOf(0, 5, 10, 15, 30, 60, 120, 1440, 10080)
private val ALL_DAY_ALERTS = listOf(0, 1440, 2880, 10080, 20160, 43200, 129600)
private val REPEAT_UNITS = listOf(
    Cycle.NONE, Cycle.MINUTES, Cycle.HOURS, Cycle.DAILY, Cycle.WEEKLY, Cycle.MONTHLY, Cycle.YEARLY
)
private val NAG_OPTIONS = listOf(0, 1, 2, 5, 10, 15, 30)

private fun unitName(c: Cycle): String = when (c) {
    Cycle.NONE -> "Never"
    Cycle.MINUTES -> "Minutes"
    Cycle.HOURS -> "Hours"
    Cycle.DAILY -> "Days"
    Cycle.WEEKLY -> "Weeks"
    Cycle.MONTHLY -> "Months"
    Cycle.QUARTERLY -> "Quarters"
    Cycle.YEARLY -> "Years"
}

private fun firstCategory(kind: Kind): Category = when (kind) {
    Kind.REMINDER -> Category.TASK
    Kind.PAYMENT -> Category.BILL
    Kind.RENEWAL -> Category.DOCUMENT
}

private fun nextRoundHour(): Int = ((LocalTime.now().hour + 1) % 24) * 60

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(vm: AppViewModel, itemId: Long?, onDone: () -> Unit) {
    val isNew = itemId == null
    val extra = Extra.colors
    val context = LocalContext.current

    var loaded by rememberSaveable { mutableStateOf(isNew) }
    var title by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(Kind.REMINDER) }
    var category by rememberSaveable { mutableStateOf(Category.TASK) }
    var dueEpochDay by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var allDay by rememberSaveable { mutableStateOf(false) }
    var minute by rememberSaveable { mutableIntStateOf(nextRoundHour()) }
    var cycle by rememberSaveable { mutableStateOf(Cycle.NONE) }
    var every by rememberSaveable { mutableIntStateOf(1) }
    var weekDays by rememberSaveable { mutableStateOf("") }
    var endMode by rememberSaveable { mutableIntStateOf(0) } // 0 never, 1 after N times, 2 on date
    var limit by rememberSaveable { mutableIntStateOf(10) }
    var untilEpochDay by rememberSaveable { mutableLongStateOf(LocalDate.now().plusMonths(3).toEpochDay()) }
    var autoRenew by rememberSaveable { mutableStateOf(true) }
    var amountText by rememberSaveable { mutableStateOf("") }
    var currency by rememberSaveable { mutableStateOf(vm.settings.currency) }
    var notes by rememberSaveable { mutableStateOf("") }
    var alerts by rememberSaveable { mutableStateOf("0") }
    var alertStyle by rememberSaveable { mutableStateOf(AlertStyle.NOTIFICATION) }
    var nagEvery by rememberSaveable { mutableIntStateOf(0) }
    var nagCount by rememberSaveable { mutableIntStateOf(3) }

    var titleError by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    var pickUntil by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var exactOk by remember { mutableStateOf(AlarmScheduler.canScheduleExact(context)) }
    var fullScreenOk by remember { mutableStateOf(Notifier.canUseFullScreen(context)) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        exactOk = AlarmScheduler.canScheduleExact(context)
        fullScreenOk = Notifier.canUseFullScreen(context)
    }

    LaunchedEffect(itemId) {
        if (itemId != null && !loaded) {
            val s = vm.get(itemId)
            if (s != null) {
                title = s.title
                kind = s.kind
                category = s.category
                dueEpochDay = s.dueEpochDay
                allDay = s.dueMinute == null
                minute = s.dueMinute ?: vm.defaultMinute
                cycle = s.cycle
                every = s.repeatEvery
                weekDays = s.repeatDays
                endMode = when {
                    s.repeatLimit != null -> 1
                    s.repeatUntilEpochDay != null -> 2
                    else -> 0
                }
                s.repeatLimit?.let { limit = it }
                s.repeatUntilEpochDay?.let { untilEpochDay = it }
                autoRenew = s.autoRenew
                amountText = s.amount?.let { a -> if (a % 1.0 == 0.0) a.toLong().toString() else a.toString() } ?: ""
                currency = s.currency.ifBlank { vm.settings.currency }
                notes = s.notes
                alerts = s.alertOffsetList().joinToString(",").ifEmpty { Item.NO_ALERTS }
                alertStyle = s.alertStyle
                nagEvery = s.nagEveryMin
                if (s.nagCount > 0) nagCount = s.nagCount
            }
            loaded = true
        }
    }

    val dueDate = LocalDate.ofEpochDay(dueEpochDay)
    val selectedAlerts: Set<Int> = if (alerts == Item.NO_ALERTS) emptySet()
    else alerts.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
    val subDaily = cycle == Cycle.MINUTES || cycle == Cycle.HOURS
    val timed = !allDay || subDaily

    fun save() {
        if (title.isBlank()) {
            titleError = true
            return
        }
        vm.save(
            itemId,
            Draft(
                title = title,
                kind = kind,
                category = category,
                dueEpochDay = dueEpochDay,
                dueMinute = if (timed) minute else null,
                cycle = cycle,
                repeatEvery = every,
                repeatDays = weekDays,
                repeatLimit = if (endMode == 1) limit else null,
                repeatUntilEpochDay = if (endMode == 2) untilEpochDay else null,
                autoRenew = autoRenew,
                amount = amountText.replace(",", ".").toDoubleOrNull(),
                currency = currency.trim().uppercase(Locale.ROOT),
                notes = notes,
                alertOffsets = if (selectedAlerts.isEmpty()) Item.NO_ALERTS
                else selectedAlerts.sortedDescending().joinToString(","),
                alertStyle = alertStyle,
                nagEveryMin = nagEvery,
                nagCount = nagCount,
            )
        )
        onDone()
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        if (!loaded) return@Scaffold

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HeaderIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onDone)
                Spacer(Modifier.weight(1f))
                if (!isNew) HeaderIconButton(Icons.Outlined.Delete, "Delete") { confirmDelete = true }
            }
            PageHeader(
                eyebrow = if (isNew) "New ${kind.label.lowercase()}" else category.label,
                title = if (isNew) "Add" else "Edit"
            )

            // ---- What kind of thing ----
            ChoicePills(
                options = Kind.entries.toList(),
                selected = kind,
                label = { it.label },
                onSelect = { k ->
                    kind = k
                    if (category.defaultKind != k && category != Category.OTHER) category = firstCategory(k)
                    if (isNew && k != Kind.REMINDER && !subDaily) {
                        allDay = true
                        alerts = "10080,1440,0"
                    }
                    if (isNew && k == Kind.REMINDER) {
                        allDay = false
                        alerts = "0"
                    }
                }
            )

            SectionLabel("Category", extra.muted)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Category.entries.filter { it.defaultKind == kind || it == Category.OTHER }) { c ->
                    FilterChip(
                        selected = category == c,
                        onClick = { category = c },
                        label = { Text(c.label) },
                        leadingIcon = { Icon(c.icon(), null, Modifier.size(18.dp)) }
                    )
                }
            }

            if (isNew) {
                val suggestions = Templates.forCategory(category)
                if (suggestions.isNotEmpty()) {
                    SectionLabel("Quick add", extra.muted)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(suggestions) { t ->
                            SuggestionChip(
                                onClick = {
                                    title = t.title
                                    titleError = false
                                    cycle = t.cycle
                                    every = t.every
                                    weekDays = t.days
                                    autoRenew = t.autoRenew || kind == Kind.REMINDER
                                    if (t.minute != null) {
                                        allDay = false
                                        minute = t.minute
                                    } else {
                                        allDay = true
                                    }
                                    if (t.alarm) alertStyle = AlertStyle.ALARM
                                    alerts = if (t.minute != null || kind == Kind.REMINDER) "0" else "10080,1440,0"
                                },
                                label = { Text(t.title) }
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    if (it.isNotBlank()) titleError = false
                },
                label = { Text("Name") },
                placeholder = {
                    Text(
                        when (kind) {
                            Kind.REMINDER -> "e.g. Call the bank, Take medicine"
                            Kind.PAYMENT -> "e.g. Rent, Netflix, Electricity"
                            Kind.RENEWAL -> "e.g. Passport, Car insurance"
                        }
                    )
                },
                isError = titleError,
                supportingText = { if (titleError) Text("Please enter a name") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth()
            )

            // ---- When ----
            LabeledCard("When") {
                ClickableField(
                    label = if (kind == Kind.RENEWAL) "Expiry date" else "Date",
                    value = Format.date(dueDate),
                    icon = Icons.Outlined.CalendarMonth,
                    onClick = { pickDate = true },
                    modifier = Modifier.fillMaxWidth()
                )
                if (!subDaily) {
                    SwitchRow("All day", "Uses your default reminder time (Settings)", allDay) { allDay = it }
                }
                if (timed) {
                    ClickableField(
                        label = "Time",
                        value = Format.time(minute),
                        icon = Icons.Outlined.Schedule,
                        onClick = { pickTime = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // ---- Repeat ----
            LabeledCard("Repeat") {
                SelectField(
                    label = "Repeat",
                    selected = cycle,
                    options = REPEAT_UNITS + if (cycle == Cycle.QUARTERLY) listOf(Cycle.QUARTERLY) else emptyList(),
                    optionLabel = { if (it == Cycle.NONE) "Never" else "Every … ${unitName(it).lowercase()}" },
                    onSelect = {
                        cycle = it
                        if (it == Cycle.MINUTES && every < 5) every = 15
                        if (it != Cycle.MINUTES && every > 12) every = 1
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                if (cycle != Cycle.NONE) {
                    Stepper(
                        label = "Every",
                        value = every,
                        range = if (cycle == Cycle.MINUTES) 1..720 else 1..99,
                        step = if (cycle == Cycle.MINUTES && every >= 5) 5 else 1,
                        suffix = if (every == 1) cycle.unitSingular else cycle.unitPlural,
                        onChange = { every = it }
                    )
                    if (cycle == Cycle.WEEKLY) {
                        WeekdayPicker(weekDays) { weekDays = it }
                    }
                    Text("Ends", color = extra.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    ChoicePills(
                        options = listOf(0, 1, 2),
                        selected = endMode,
                        label = { listOf("Never", "After", "On date")[it] },
                        onSelect = { endMode = it }
                    )
                    if (endMode == 1) {
                        Stepper("After", limit, 1..999, 1, if (limit == 1) "more time" else "more times") { limit = it }
                    }
                    if (endMode == 2) {
                        ClickableField(
                            label = "Last date",
                            value = Format.date(LocalDate.ofEpochDay(untilEpochDay)),
                            icon = Icons.Outlined.CalendarMonth,
                            onClick = { pickUntil = true },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    SwitchRow(
                        title = when (kind) {
                            Kind.REMINDER -> "Move on automatically"
                            Kind.PAYMENT -> "Pays automatically"
                            Kind.RENEWAL -> "Renews automatically"
                        },
                        subtitle = when (kind) {
                            Kind.REMINDER -> "Goes to the next time once this one passes"
                            else -> "Off = stays overdue until you mark it"
                        },
                        checked = autoRenew,
                        onChange = { autoRenew = it }
                    )
                }
            }

            // ---- Alerts ----
            LabeledCard("Alerts") {
                ChoicePills(
                    options = AlertStyle.entries.toList(),
                    selected = alertStyle,
                    label = { it.label },
                    onSelect = { alertStyle = it }
                )
                Text(
                    if (alertStyle == AlertStyle.ALARM) {
                        "Rings with your alarm sound and shows a full-screen alarm, even on the lock screen."
                    } else {
                        "A notification with Done and Snooze buttons."
                    },
                    color = extra.muted,
                    fontSize = 13.sp
                )
                val options = if (timed) TIMED_ALERTS else ALL_DAY_ALERTS
                val shownOptions = (options + selectedAlerts).distinct().sorted()
                Text("Remind me", color = extra.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(shownOptions) { m ->
                        FilterChip(
                            selected = m in selectedAlerts,
                            onClick = {
                                val updated = if (m in selectedAlerts) selectedAlerts - m else selectedAlerts + m
                                alerts = if (updated.isEmpty()) Item.NO_ALERTS
                                else updated.sortedDescending().joinToString(",")
                            },
                            label = { Text(if (m == 0 && !timed) "On the day" else Format.offsetText(m)) }
                        )
                    }
                }
                SelectField(
                    label = "Repeat the alert",
                    selected = nagEvery,
                    options = NAG_OPTIONS,
                    optionLabel = { if (it == 0) "Off" else "Every $it min until handled" },
                    onSelect = { nagEvery = it },
                    modifier = Modifier.fillMaxWidth()
                )
                if (nagEvery > 0) {
                    Stepper("Up to", nagCount, 1..20, 1, if (nagCount == 1) "time" else "times") { nagCount = it }
                }
                if (!exactOk) {
                    PermissionHint("Exact timing is off, so alerts may arrive a few minutes late.", "Allow") {
                        if (Build.VERSION.SDK_INT >= 31) {
                            context.startActivity(
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                }
                if (alertStyle == AlertStyle.ALARM && !fullScreenOk) {
                    PermissionHint("Full-screen alarms are blocked; you'll get a loud notification instead.", "Allow") {
                        if (Build.VERSION.SDK_INT >= 34) {
                            context.startActivity(
                                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                }
            }

            // ---- Money (payments & renewals) ----
            if (kind != Kind.REMINDER) {
                LabeledCard("Amount") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = amountText,
                            onValueChange = { new ->
                                if (new.all { it.isDigit() || it == '.' || it == ',' }) amountText = new
                            },
                            label = { Text("Amount (optional)") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = currency,
                            onValueChange = { new ->
                                if (new.length <= 3 && new.all { it.isLetter() }) currency = new.uppercase(Locale.ROOT)
                            },
                            label = { Text("Currency") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                            modifier = Modifier.width(110.dp)
                        )
                    }
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (optional)") },
                minLines = 2,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = { save() },
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = extra.ink, contentColor = extra.onInk),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Text("Save", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (pickDate || pickUntil) {
        val initial = if (pickDate) dueEpochDay else untilEpochDay
        val state = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.ofEpochDay(initial).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = {
                pickDate = false
                pickUntil = false
            },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val day = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                        if (pickDate) dueEpochDay = day else untilEpochDay = day
                    }
                    pickDate = false
                    pickUntil = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = {
                    pickDate = false
                    pickUntil = false
                }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = state)
        }
    }

    if (pickTime) {
        val state = rememberTimePickerState(
            initialHour = minute / 60,
            initialMinute = minute % 60,
            is24Hour = DateFormat.is24HourFormat(context)
        )
        AlertDialog(
            onDismissRequest = { pickTime = false },
            confirmButton = {
                TextButton(onClick = {
                    minute = state.hour * 60 + state.minute
                    pickTime = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("Cancel") } },
            text = { TimePicker(state = state) }
        )
    }

    if (confirmDelete && itemId != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete \"$title\"?") },
            text = { Text("This removes it and its reminders.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete(itemId)
                    onDone()
                }) { Text("Delete", color = extra.overdue) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun <T> ChoicePills(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    val extra = Extra.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(extra.line.copy(alpha = 0.5f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { option ->
            val active = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (active) extra.card else Color.Transparent)
                    .clickable(role = Role.RadioButton) { onSelect(option) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label(option),
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    color = if (active) extra.ink else extra.muted,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, fontSize = 12.sp, color = Extra.colors.muted)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Stepper(
    label: String,
    value: Int,
    range: IntRange,
    step: Int,
    suffix: String,
    onChange: (Int) -> Unit,
) {
    val extra = Extra.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(56.dp))
        IconButton(
            onClick = { onChange((value - step).coerceIn(range)) },
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .border(1.dp, extra.line, CircleShape)
        ) { Icon(Icons.Outlined.Remove, contentDescription = "Less") }
        Text(
            value.toString(),
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(52.dp)
        )
        IconButton(
            onClick = { onChange((value + step).coerceIn(range)) },
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .border(1.dp, extra.line, CircleShape)
        ) { Icon(Icons.Filled.Add, contentDescription = "More") }
        Spacer(Modifier.width(10.dp))
        Text(suffix, fontSize = 15.sp, color = extra.muted)
    }
}

@Composable
private fun WeekdayPicker(selected: String, onChange: (String) -> Unit) {
    val extra = Extra.colors
    val set = selected.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            if (set.isEmpty()) "On the same weekday as the date" else "On these days",
            color = extra.muted,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            (1..7).forEach { d ->
                val on = d in set
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(CircleShape)
                        .background(if (on) extra.ink else extra.card)
                        .border(1.dp, if (on) extra.ink else extra.line, CircleShape)
                        .clickable(role = Role.Checkbox) {
                            val updated = if (on) set - d else set + d
                            onChange(updated.sorted().joinToString(","))
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        DayOfWeek.of(d).getDisplayName(JavaTextStyle.NARROW, Locale.getDefault()),
                        color = if (on) extra.onInk else extra.ink,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionHint(text: String, action: String, onClick: () -> Unit) {
    val extra = Extra.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(extra.soonBg)
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, color = extra.soon, fontSize = 13.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = onClick) { Text(action, fontWeight = FontWeight.Bold) }
    }
}
