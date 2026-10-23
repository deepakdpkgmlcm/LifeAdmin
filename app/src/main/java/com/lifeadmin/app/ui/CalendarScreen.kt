package com.lifeadmin.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeadmin.app.data.History
import com.lifeadmin.app.data.Item
import com.lifeadmin.app.data.Kind
import com.lifeadmin.app.data.dueDate
import com.lifeadmin.app.data.resolvedKind
import com.lifeadmin.app.reminders.Schedule
import com.lifeadmin.app.ui.theme.Extra
import com.lifeadmin.app.util.Format
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle as JavaTextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/** One item on one calendar day. [repeat] = a future repeat, not the date it's due now. */
private data class DayEntry(val item: Item, val repeat: Boolean)

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    vm: AppViewModel,
    onOpen: (Long) -> Unit,
    onAdd: () -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    val items by vm.items.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val all = items.orEmpty().filterNot { it.done }
    val today = remember { LocalDate.now() }
    var monthOffset by rememberSaveable { mutableIntStateOf(0) }
    var selectedEpochDay by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    val selected = LocalDate.ofEpochDay(selectedEpochDay)
    val month = YearMonth.from(today).plusMonths(monthOffset.toLong())
    val extra = Extra.colors
    val haptic = LocalHapticFeedback.current
    val snackbar = remember { SnackbarHostState() }
    val completing by vm.completing.collectAsStateWithLifecycle()
    var addSheetFor by remember { mutableStateOf<LocalDate?>(null) }
    UndoSnackbarEffect(vm, snackbar)

    val currency = vm.settings.currency
    val conv = remember(currency) { vm.settings.converter() }
    val defMin = vm.defaultMinute

    // Every item on every day of the visible month, repeats included.
    val byDate: Map<LocalDate, List<DayEntry>> = remember(all, month, defMin) {
        val from = month.atDay(1)
        val to = month.atEndOfMonth()
        val map = HashMap<LocalDate, MutableList<DayEntry>>()
        all.forEach { item ->
            val due = Schedule.occurrence(item, defMin).toLocalDate()
            Schedule.datesInRange(item, from, to, defMin).forEach { d ->
                map.getOrPut(d) { mutableListOf() } += DayEntry(item, repeat = d != due)
            }
        }
        map.mapValues { (_, list) ->
            list.sortedWith(compareBy<DayEntry> { it.repeat }.thenBy { it.item.dueMinute ?: defMin })
        }
    }
    val zone = remember { ZoneId.systemDefault() }
    val doneByDate: Map<LocalDate, List<History>> = remember(history, month) {
        history.groupBy { Instant.ofEpochMilli(it.paidAt).atZone(zone).toLocalDate() }
            .filterKeys { YearMonth.from(it) == month }
    }

    fun select(day: LocalDate) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        selectedEpochDay = day.toEpochDay()
    }

    fun changeMonth(delta: Int) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        monthOffset += delta
        val m = YearMonth.from(today).plusMonths(monthOffset.toLong())
        selectedEpochDay = (if (m == YearMonth.from(today)) today else m.atDay(1)).toEpochDay()
    }

    fun addOn(kind: Kind, day: LocalDate) {
        addSheetFor = null
        vm.prefill.value = vm.draftFor(kind, day)
        onAdd()
    }

    val dayEntries = byDate[selected].orEmpty()
    val dueEntries = dayEntries.filterNot { it.repeat }
    val repeatEntries = dayEntries.filter { it.repeat }
    val doneToday = doneByDate[selected].orEmpty()
    val isPast = selected.isBefore(today)

    val monthEntries = byDate.values.flatten()
    val monthTotal = monthEntries
        .filter { it.item.kind != Kind.REMINDER && it.item.amount != null }
        .sumOf { conv(it.item.amount!!, it.item.currency) ?: 0.0 }
    val monthDone = doneByDate.values.sumOf { it.size }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = bottomBar
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp, end = 20.dp,
                top = padding.calculateTopPadding() + 20.dp,
                bottom = padding.calculateBottomPadding() + 24.dp
            )
        ) {
            item(key = "header") {
                PageHeader(
                    eyebrow = month.year.toString(),
                    title = month.month.getDisplayName(JavaTextStyle.FULL, Locale.getDefault())
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (month != YearMonth.from(today) || selected != today) {
                            HeaderIconButton(Icons.Outlined.Today, "Go to today") {
                                monthOffset = 0
                                select(today)
                            }
                        }
                        HeaderIconButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month") { changeMonth(-1) }
                        HeaderIconButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month") { changeMonth(1) }
                    }
                }
            }

            item(key = "monthSummary") {
                val parts = mutableListOf(
                    when (monthEntries.size) {
                        0 -> "Nothing planned this month"
                        1 -> "1 thing this month"
                        else -> "${monthEntries.size} things this month"
                    }
                )
                if (monthTotal > 0) parts += Format.money(Math.round(monthTotal).toDouble(), currency)
                if (monthDone > 0) parts += "$monthDone done"
                Text(
                    parts.joinToString(" · "),
                    color = extra.muted,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            item(key = "grid") {
                val swipe = with(LocalDensity.current) { 64.dp.toPx() }
                AppCard(
                    Modifier
                        .padding(top = 14.dp)
                        .pointerInput(Unit) {
                            var dx = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { dx = 0f },
                                onDragEnd = {
                                    if (dx > swipe) changeMonth(-1) else if (dx < -swipe) changeMonth(1)
                                },
                                onHorizontalDrag = { _, amount -> dx += amount }
                            )
                        }
                ) {
                    AnimatedContent(
                        targetState = month,
                        transitionSpec = {
                            val forward = targetState > initialState
                            (slideInHorizontally { if (forward) it / 3 else -it / 3 } + fadeIn()) togetherWith
                                (slideOutHorizontally { if (forward) -it / 3 else it / 3 } + fadeOut())
                        },
                        label = "month"
                    ) { m ->
                        MonthGrid(
                            month = m,
                            today = today,
                            selected = selected,
                            byDate = byDate,
                            doneDays = doneByDate.keys,
                            onSelect = { select(it) },
                            onLongPress = { day ->
                                select(day)
                                if (!day.isBefore(today)) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    addSheetFor = day
                                }
                            }
                        )
                    }
                    Legend()
                }
            }

            item(key = "dayLabel") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                selected == today -> "Today"
                                selected == today.plusDays(1) -> "Tomorrow"
                                selected == today.minusDays(1) -> "Yesterday"
                                else -> selected.dayOfWeek.getDisplayName(JavaTextStyle.FULL, Locale.getDefault())
                            },
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(Format.date(selected), color = extra.muted, fontSize = 13.sp)
                    }
                    if (!isPast) {
                        TextButton(onClick = { addSheetFor = selected }) {
                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (!isPast) {
                item(key = "addRow") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AddChip(Icons.Outlined.NotificationsNone, "Reminder", Modifier.weight(1f)) { addOn(Kind.REMINDER, selected) }
                            AddChip(Icons.Outlined.Payments, "Bill", Modifier.weight(1f)) { addOn(Kind.PAYMENT, selected) }
                            AddChip(Icons.Outlined.EventBusy, "Expiry", Modifier.weight(1f)) { addOn(Kind.RENEWAL, selected) }
                        }
                        QuickAddBar(
                            vm = vm,
                            onEditDetails = onAdd,
                            baseDate = selected,
                            placeholder = if (selected == today) "Quick add for today… “Call mom 7pm”"
                            else "Quick add on ${Format.shortDate(selected)}… “Dentist 4pm”"
                        )
                    }
                }
            }

            // Due on this day (can be marked done).
            if (dueEntries.isNotEmpty()) {
                item(key = "dueLabel") {
                    SectionLabel("Due", extra.muted, Modifier.padding(start = TimelineGutter, top = 10.dp))
                }
                itemsIndexed(dueEntries, key = { _, e -> "due-" + e.item.id }) { index, e ->
                    TimelineRow(
                        item = e.item,
                        today = today,
                        isFirst = index == 0,
                        isLast = index == dueEntries.lastIndex,
                        onOpen = { onOpen(e.item.id) },
                        onRenew = { vm.completeWithFeedback(e.item) },
                        onDelete = { vm.deleteItem(e.item) },
                        modifier = Modifier.animateItem(),
                        now = LocalDateTime.now(),
                        defaultMinute = defMin,
                        completing = e.item.id in completing
                    )
                }
            }

            // Later repeats of repeating items: shown so the month looks complete.
            if (repeatEntries.isNotEmpty()) {
                item(key = "repeatLabel") {
                    SectionLabel("Repeats", extra.muted, Modifier.padding(top = 12.dp, bottom = 6.dp))
                }
                items(repeatEntries, key = { "rep-" + it.item.id }) { e ->
                    RepeatRow(e.item, defMin, Modifier.animateItem()) { onOpen(e.item.id) }
                }
            }

            if (doneToday.isNotEmpty()) {
                item(key = "doneLabel") {
                    SectionLabel("Done on this day", extra.muted, Modifier.padding(top = 12.dp, bottom = 6.dp))
                }
                items(doneToday, key = { "done-" + it.id }) { h ->
                    DoneRow(h, Modifier.animateItem()) { onOpen(h.itemId) }
                }
            }

            if (dayEntries.isEmpty() && doneToday.isEmpty()) {
                item(key = "dayEmpty") {
                    Text(
                        if (isPast) "Nothing was due on this day."
                        else "Nothing planned yet. Add a reminder, bill or expiry above, or long-press any date.",
                        color = extra.muted,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
        }
    }

    addSheetFor?.let { day ->
        ModalBottomSheet(
            onDismissRequest = { addSheetFor = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = extra.card
        ) {
            Column(
                Modifier
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 16.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Add on ${Format.date(day)}", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(4.dp))
                SheetOption(Icons.Outlined.NotificationsNone, "Reminder", "A task or something to do, at a time you pick") {
                    addOn(Kind.REMINDER, day)
                }
                SheetOption(Icons.Outlined.Payments, "Bill or payment", "Rent, card bill, EMI, subscription — you can scan the bill") {
                    addOn(Kind.PAYMENT, day)
                }
                SheetOption(Icons.Outlined.EventBusy, "Expiry or renewal", "Passport, insurance, licence, warranty") {
                    addOn(Kind.RENEWAL, day)
                }
            }
        }
    }
}

@Composable
private fun AddChip(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val extra = Extra.colors
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(extra.laterBg)
            .clickable(role = Role.Button, onClickLabel = "Add $label", onClick = onClick)
            .padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = extra.later, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = extra.later, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
private fun SheetOption(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val extra = Extra.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(extra.laterBg),
            contentAlignment = Alignment.Center
        ) { Icon(icon, contentDescription = null, tint = extra.later) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(subtitle, color = extra.muted, fontSize = 13.sp)
        }
    }
}

/** A later repeat of a repeating item (it can be marked done only on its current date). */
@Composable
private fun RepeatRow(item: Item, defaultMinute: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val extra = Extra.colors
    Row(
        modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(extra.card)
            .border(1.dp, extra.line, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(item.category.icon(), contentDescription = null, tint = extra.muted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1)
            val parts = mutableListOf<String>()
            item.dueMinute?.let { parts += Format.time(it) }
            parts += Format.repeatText(item)
            if (item.kind != Kind.REMINDER) item.amount?.let { parts += Format.money(it, item.currency) }
            Text(parts.joinToString(" · "), color = extra.muted, fontSize = 12.sp, maxLines = 1)
        }
        Icon(Icons.Outlined.Repeat, contentDescription = "Repeat", tint = extra.later.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
    }
}

/** Something marked done (or paid / renewed) on this day. */
@Composable
private fun DoneRow(h: History, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val extra = Extra.colors
    val kind = h.resolvedKind()
    Row(
        modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = extra.later, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(h.itemTitle, fontSize = 15.sp, color = extra.muted, maxLines = 1)
            val parts = mutableListOf(kind.doneVerb)
            h.amount?.let { parts += Format.money(it, h.currency) }
            Text(parts.joinToString(" · "), color = extra.muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Legend() {
    val extra = Extra.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendDot(Urgency.OVERDUE.colors().dot, "Overdue")
        LegendDot(Urgency.SOON.colors().dot, "Soon")
        LegendDot(Urgency.LATER.colors().dot, "Later")
        LegendDot(extra.muted.copy(alpha = 0.5f), "Repeat")
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 11.sp, color = Extra.colors.muted)
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    today: LocalDate,
    selected: LocalDate,
    byDate: Map<LocalDate, List<DayEntry>>,
    doneDays: Set<LocalDate>,
    onSelect: (LocalDate) -> Unit,
    onLongPress: (LocalDate) -> Unit,
) {
    val extra = Extra.colors
    val firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    val leading = (month.atDay(1).dayOfWeek.value - firstDayOfWeek.value + 7) % 7
    val cells: List<LocalDate?> = List(leading) { null } +
        (1..month.lengthOfMonth()).map { month.atDay(it) }
    val weeks = cells.chunked(7).map { week -> week + List(7 - week.size) { null } }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            for (i in 0 until 7) {
                val dow = firstDayOfWeek.plus(i.toLong())
                Text(
                    dow.getDisplayName(JavaTextStyle.NARROW, Locale.getDefault()),
                    color = extra.muted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }
        }
        weeks.forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { day ->
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                    ) {
                        if (day != null) {
                            DayCell(
                                day = day,
                                isToday = day == today,
                                isSelected = day == selected,
                                entries = byDate[day].orEmpty(),
                                hasDone = day in doneDays,
                                today = today,
                                onClick = { onSelect(day) },
                                onLongClick = { onLongPress(day) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DayCell(
    day: LocalDate,
    isToday: Boolean,
    isSelected: Boolean,
    entries: List<DayEntry>,
    hasDone: Boolean,
    today: LocalDate,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val extra = Extra.colors
    val shape = RoundedCornerShape(12.dp)
    val bg by animateColorAsState(if (isSelected) extra.ink else extra.card, label = "cellBg")
    val fg = if (isSelected) extra.onInk else if (day.isBefore(today)) extra.muted else extra.ink
    val todayBorder = MaterialTheme.colorScheme.primary
    val dotColors = entries.take(3).map { e ->
        if (e.repeat) extra.muted.copy(alpha = 0.5f)
        else urgencyOf(java.time.temporal.ChronoUnit.DAYS.between(today, day)).colors().dot
    }
    val label = buildString {
        append(Format.date(day))
        if (entries.isNotEmpty()) append(", ${entries.size} item" + if (entries.size == 1) "" else "s")
        if (hasDone) append(", something done")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(shape)
            .background(bg)
            .then(if (isToday && !isSelected) Modifier.border(1.5.dp, todayBorder, shape) else Modifier)
            .combinedClickable(
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = "Add on this day"
            )
            .semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            day.dayOfMonth.toString(),
            color = fg,
            fontSize = 14.sp,
            fontWeight = if (isToday || isSelected) FontWeight.ExtraBold else FontWeight.Medium
        )
        Spacer(Modifier.height(3.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(5.dp)
        ) {
            dotColors.forEach { c ->
                Box(
                    Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) extra.onInk else c)
                )
            }
            if (entries.size > 3) {
                Text("+", fontSize = 8.sp, color = if (isSelected) extra.onInk else extra.muted, fontWeight = FontWeight.Bold)
            } else if (entries.isEmpty() && hasDone) {
                Box(
                    Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .border(1.dp, if (isSelected) extra.onInk else extra.later, CircleShape)
                )
            }
        }
    }
}
