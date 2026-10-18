package com.lifeadmin.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import com.lifeadmin.app.ui.theme.Extra
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lifeadmin.app.data.Category
import com.lifeadmin.app.data.Cycle
import com.lifeadmin.app.data.Item
import com.lifeadmin.app.data.Templates
import com.lifeadmin.app.util.Format
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

private val REMINDER_OPTIONS = listOf(90, 30, 7, 1, 0)

private fun reminderLabel(days: Int): String = when (days) {
    0 -> "On the day"
    1 -> "1 day before"
    else -> "$days days before"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(vm: AppViewModel, itemId: Long?, onDone: () -> Unit) {
    val isNew = itemId == null

    var loaded by rememberSaveable { mutableStateOf(isNew) }
    var title by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(Category.DOCUMENT) }
    var dueEpochDay by rememberSaveable { mutableLongStateOf(LocalDate.now().plusMonths(1).toEpochDay()) }
    var cycle by rememberSaveable { mutableStateOf(Cycle.NONE) }
    var autoRenew by rememberSaveable { mutableStateOf(false) }
    var amountText by rememberSaveable { mutableStateOf("") }
    var currency by rememberSaveable { mutableStateOf(vm.settings.currency) }
    var notes by rememberSaveable { mutableStateOf("") }
    var reminders by rememberSaveable { mutableStateOf(Item.DEFAULT_REMINDERS) }

    var titleError by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(itemId) {
        if (itemId != null && !loaded) {
            vm.get(itemId)?.let { item ->
                title = item.title
                category = item.category
                dueEpochDay = item.dueEpochDay
                cycle = item.cycle
                autoRenew = item.autoRenew
                amountText = item.amount?.let { a ->
                    if (a % 1.0 == 0.0) a.toLong().toString() else a.toString()
                } ?: ""
                currency = item.currency.ifBlank { vm.settings.currency }
                notes = item.notes
                reminders = item.reminderDays
            }
            loaded = true
        }
    }

    val dueDate = LocalDate.ofEpochDay(dueEpochDay)
    val selectedReminders = reminders.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()

    fun save() {
        if (title.isBlank()) {
            titleError = true
            return
        }
        vm.save(
            itemId,
            Draft(
                title = title,
                category = category,
                dueEpochDay = dueEpochDay,
                cycle = cycle,
                autoRenew = autoRenew,
                amount = amountText.replace(",", ".").toDoubleOrNull(),
                currency = currency.trim().uppercase(Locale.ROOT),
                notes = notes,
                reminderDays = reminders,
            )
        )
        onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "Add item" else "Edit item") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!isNew) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete")
                        }
                    }
                    TextButton(onClick = { save() }) { Text("Save") }
                }
            )
        }
    ) { padding ->
        if (!loaded) return@Scaffold

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Category
            Text("Category", style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Category.entries.toList()) { c ->
                    FilterChip(
                        selected = category == c,
                        onClick = { category = c },
                        label = { Text(c.label) },
                        leadingIcon = { Icon(c.icon(), null, Modifier.size(18.dp)) }
                    )
                }
            }

            // Quick-add suggestions (new items only)
            if (isNew) {
                val suggestions = Templates.forCategory(category)
                if (suggestions.isNotEmpty()) {
                    Text("Suggestions", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(suggestions) { t ->
                            SuggestionChip(
                                onClick = {
                                    title = t.title
                                    titleError = false
                                    cycle = t.cycle
                                    autoRenew = t.autoRenew
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
                placeholder = { Text("e.g. Passport, Netflix, Car insurance") },
                isError = titleError,
                supportingText = {
                    if (titleError) Text("Please enter a name")
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth()
            )

            ClickableField(
                label = if (category.dueVerb == "Expires") "Expiry date" else "Due date",
                value = Format.date(dueDate),
                icon = Icons.Outlined.CalendarMonth,
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth()
            )

            SelectField(
                label = "Repeats",
                selected = cycle,
                options = Cycle.entries.toList(),
                optionLabel = { it.label },
                onSelect = { cycle = it },
                modifier = Modifier.fillMaxWidth()
            )

            if (cycle != Cycle.NONE) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Renews automatically", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "The date moves forward on its own after each renewal",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = autoRenew, onCheckedChange = { autoRenew = it })
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { new ->
                        if (new.all { it.isDigit() || it == '.' || it == ',' }) amountText = new
                    },
                    label = { Text("Amount (optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = currency,
                    onValueChange = { new ->
                        if (new.length <= 3 && new.all { it.isLetter() }) {
                            currency = new.uppercase(Locale.ROOT)
                        }
                    },
                    label = { Text("Currency") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.width(110.dp)
                )
            }

            Text("Remind me", style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(REMINDER_OPTIONS) { days ->
                    FilterChip(
                        selected = days in selectedReminders,
                        onClick = {
                            val updated =
                                if (days in selectedReminders) selectedReminders - days
                                else selectedReminders + days
                            reminders = updated.sortedDescending().joinToString(",")
                        },
                        label = { Text(reminderLabel(days)) }
                    )
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (optional)") },
                placeholder = { Text("Policy number, where to renew, etc.") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            if (!isNew && cycle != Cycle.NONE && itemId != null) {
                OutlinedButton(
                    onClick = {
                        vm.markRenewed(itemId)
                        onDone()
                    },
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(Icons.Outlined.Autorenew, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Mark as paid · next ${Format.shortDate(cycle.next(dueDate))}")
                }
            }

            Button(
                onClick = { save() },
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Extra.colors.ink,
                    contentColor = Extra.colors.onInk
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = dueDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        dueEpochDay = Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (confirmDelete && itemId != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete \"$title\"?") },
            text = { Text("This removes the item and its reminders.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete(itemId)
                    onDone()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }
}
