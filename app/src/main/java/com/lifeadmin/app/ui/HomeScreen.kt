package com.lifeadmin.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeadmin.app.data.AppSettings
import com.lifeadmin.app.data.Cycle
import com.lifeadmin.app.data.Item
import com.lifeadmin.app.data.daysLeft
import com.lifeadmin.app.data.dueDate
import com.lifeadmin.app.ui.theme.DmMono
import com.lifeadmin.app.ui.theme.Extra
import com.lifeadmin.app.util.Format
import kotlinx.coroutines.launch
import com.lifeadmin.app.data.Kind
import com.lifeadmin.app.reminders.Schedule
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: AppViewModel,
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit,
    onSettings: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
) {
    val items by vm.items.collectAsStateWithLifecycle()
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = LocalDateTime.now()
        }
    }
    val today = now.toLocalDate()
    val defMin = vm.defaultMinute
    fun isOverdue(item: Item) = itemUrgency(item, Schedule.occurrence(item, defMin), now) == Urgency.OVERDUE
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var typeFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedEpochDay by rememberSaveable { mutableStateOf<Long?>(null) }
    val selectedDay = selectedEpochDay?.let { LocalDate.ofEpochDay(it) }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val extra = Extra.colors
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex < 2 } }

    fun showUndo(message: String, undo: () -> Unit) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(
                message = message,
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) undo()
        }
    }

    val onDelete: (Item) -> Unit = { vm.deleteItem(it) }
    val onRenew: (Item) -> Unit = { vm.completeWithFeedback(it) }
    val completing by vm.completing.collectAsStateWithLifecycle()
    UndoSnackbarEffect(vm, snackbar)

    NotificationPermissionRequest(vm.settings, trigger = !items.isNullOrEmpty())

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = bottomBar,
        floatingActionButton = {
            if (!items.isNullOrEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onAdd,
                    expanded = fabExpanded,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Add", fontWeight = FontWeight.Bold) },
                    containerColor = extra.ink,
                    contentColor = extra.onInk,
                    shape = RoundedCornerShape(28.dp)
                )
            }
        }
    ) { padding ->
        val all = items?.filterNot { it.done }
        when {
            all == null -> Unit // first load
            all.isEmpty() -> EmptyState(today, Modifier.padding(padding), onAdd, onSettings)
            else -> {
                val ofType = all.filter { typeFilter == null || it.kind.name == typeFilter }
                val shown = ofType.filter { filter == null || it.category.name == filter }
                val categories = ofType.map { it.category }.distinct().sortedBy { it.ordinal }
                val kindCounts = all.groupingBy { it.kind }.eachCount()
                val weekEnd = today.plusDays(6)
                val busyDays = all.map { it.dueDate() }
                    .filter { !it.isBefore(today) && !it.isAfter(weekEnd) }
                    .toSet()
                val overdueCount = all.count { isOverdue(it) }
                val weekCount = all.count { !isOverdue(it) && it.daysLeft(today) in 0..7 }
                val currency = vm.settings.currency
                val monthly = all
                    .filter {
                        it.kind != Kind.REMINDER && it.cycle != Cycle.NONE && it.amount != null &&
                            it.currency.equals(currency, ignoreCase = true)
                    }
                    .sumOf { it.amount!! * it.cycle.perMonthFor(it.repeatEvery) }
                val perMonth = if (monthly > 0) Format.money(Math.round(monthly).toDouble(), currency) else "—"

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = padding.calculateTopPadding() + 20.dp,
                        bottom = padding.calculateBottomPadding() + 90.dp
                    )
                ) {
                    item(key = "header") { Header(today, onSettings) }

                    item(key = "quickadd") {
                        QuickAddBar(vm, onEditDetails = onAdd, modifier = Modifier.padding(top = 14.dp))
                    }

                    item(key = "summary") {
                        SummaryCard(
                            overdue = overdueCount,
                            nextWeek = weekCount,
                            perMonth = perMonth,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }

                    item(key = "week") {
                        WeekStrip(
                            today = today,
                            selected = selectedDay,
                            busyDays = busyDays,
                            onSelect = { day ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedEpochDay = if (day == selectedDay) null else day.toEpochDay()
                            },
                            modifier = Modifier.padding(top = 14.dp)
                        )
                    }

                    if (kindCounts.size > 1) {
                        item(key = "types") {
                            TypeFilterRow(
                                counts = kindCounts,
                                selected = typeFilter,
                                onSelect = {
                                    typeFilter = it
                                    filter = null
                                },
                                modifier = Modifier.padding(top = 12.dp)
                            )
                        }
                    }

                    if (categories.size > 1) {
                        item(key = "filters") {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 12.dp)
                            ) {
                                item {
                                    CategoryChip("All", filter == null) { filter = null }
                                }
                                items(categories) { c ->
                                    CategoryChip(c.label, filter == c.name) {
                                        filter = if (filter == c.name) null else c.name
                                    }
                                }
                            }
                        }
                    }

                    item(key = "spacer") { Spacer(Modifier.height(6.dp)) }

                    if (selectedDay != null) {
                        item(key = "dayHeader") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .padding(start = TimelineGutter)
                                    .animateItem()
                            ) {
                                SectionLabel(
                                    "Due ${Format.shortDate(selectedDay)}",
                                    extra.ink,
                                    Modifier.weight(1f)
                                )
                                TextButton(onClick = { selectedEpochDay = null }) { Text("Show all") }
                            }
                        }
                        val dayItems = shown.filter { it.dueDate() == selectedDay }
                        if (dayItems.isEmpty()) {
                            item(key = "dayEmpty") {
                                Text(
                                    "Nothing due on this day.",
                                    color = extra.muted,
                                    modifier = Modifier
                                        .padding(start = TimelineGutter, top = 4.dp)
                                        .animateItem()
                                )
                            }
                        } else {
                            timeline("day", null, extra.muted, dayItems, today, now, defMin, completing, onOpen, onRenew, onDelete)
                        }
                    } else {
                        val rest = shown.filterNot { it.pinned }
                        timeline(
                            "pinned", "Pinned", extra.ink,
                            shown.filter { it.pinned }, today, now, defMin, completing, onOpen, onRenew, onDelete
                        )
                        timeline(
                            "overdue", "Overdue", extra.overdue,
                            rest.filter { isOverdue(it) }, today, now, defMin, completing, onOpen, onRenew, onDelete
                        )
                        timeline(
                            "week", "Next 7 days", extra.muted,
                            rest.filter { !isOverdue(it) && it.daysLeft(today) in 0..7 }, today, now, defMin, completing, onOpen, onRenew, onDelete
                        )
                        timeline(
                            "month", "This month", extra.muted,
                            rest.filter { it.daysLeft(today) in 8..30 }, today, now, defMin, completing, onOpen, onRenew, onDelete
                        )
                        timeline(
                            "later", "Later", extra.muted,
                            rest.filter { it.daysLeft(today) > 30 }, today, now, defMin, completing, onOpen, onRenew, onDelete
                        )
                    }
                }
            }
        }
    }
}

/** Adds one labelled group of timeline rows to the list. */
private fun LazyListScope.timeline(
    key: String,
    label: String?,
    labelColor: Color,
    list: List<Item>,
    today: LocalDate,
    now: LocalDateTime,
    defaultMinute: Int,
    completing: Set<Long>,
    onOpen: (Long) -> Unit,
    onRenew: (Item) -> Unit,
    onDelete: (Item) -> Unit,
) {
    if (list.isEmpty()) return
    if (label != null) {
        item(key = "label_$key") {
            SectionLabel(label, labelColor, Modifier.padding(start = TimelineGutter).animateItem())
        }
    }
    itemsIndexed(list, key = { _, item -> item.id }) { index, item ->
        TimelineRow(
            item = item,
            today = today,
            isFirst = index == 0,
            isLast = index == list.lastIndex,
            onOpen = { onOpen(item.id) },
            onRenew = { onRenew(item) },
            onDelete = { onDelete(item) },
            modifier = Modifier.animateItem(),
            now = now,
            defaultMinute = defaultMinute,
            completing = item.id in completing
        )
    }
}

@Composable
private fun Header(today: LocalDate, onSettings: () -> Unit) {
    PageHeader(eyebrow = Format.headerDate(today), title = "Coming up") {
        HeaderIconButton(Icons.Outlined.Tune, "Settings", onSettings)
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val extra = Extra.colors
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontWeight = FontWeight.SemiBold) },
        shape = RoundedCornerShape(18.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = extra.card,
            labelColor = extra.ink,
            selectedContainerColor = extra.ink,
            selectedLabelColor = extra.onInk
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = extra.line,
            selectedBorderColor = extra.ink
        )
    )
}

@Composable
private fun EmptyState(today: LocalDate, modifier: Modifier, onAdd: () -> Unit, onSettings: () -> Unit) {
    val extra = Extra.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        Header(today, onSettings)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(extra.laterBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.EventAvailable,
                    contentDescription = null,
                    tint = extra.later,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(Modifier.height(20.dp))
            Text("Nothing to remember yet", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Add reminders, tasks, bills, subscriptions and documents. " +
                    "Set a time, repeat it every few minutes or every year, and get a nudge or a full alarm.",
                fontSize = 14.sp,
                color = extra.muted,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onAdd,
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = extra.ink, contentColor = extra.onInk),
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 14.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add your first item", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Asks for notification permission (Android 13+) once, after the first item exists. */
@Composable
private fun NotificationPermissionRequest(settings: AppSettings, trigger: Boolean) {
    if (Build.VERSION.SDK_INT < 33) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(trigger) {
        if (!trigger || settings.askedNotificationPermission) return@LaunchedEffect
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            settings.askedNotificationPermission = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
