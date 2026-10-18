package com.lifeadmin.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeadmin.app.data.Item
import com.lifeadmin.app.data.daysLeft
import com.lifeadmin.app.data.dueDate
import com.lifeadmin.app.ui.theme.Extra
import com.lifeadmin.app.util.Format
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.time.temporal.WeekFields
import java.util.Locale

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun CalendarScreen(
    vm: AppViewModel,
    onOpen: (Long) -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    val items by vm.items.collectAsStateWithLifecycle()
    val all = items.orEmpty().filterNot { it.done }
    val today = remember { LocalDate.now() }
    var monthOffset by rememberSaveable { mutableIntStateOf(0) }
    var selectedEpochDay by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    val selected = LocalDate.ofEpochDay(selectedEpochDay)
    val month = YearMonth.from(today).plusMonths(monthOffset.toLong())
    val extra = Extra.colors
    val haptic = LocalHapticFeedback.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val byDate: Map<LocalDate, List<Item>> = all.groupBy { it.dueDate() }
    val completing by vm.completing.collectAsStateWithLifecycle()
    UndoSnackbarEffect(vm, snackbar)

    fun changeMonth(delta: Int) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        monthOffset += delta
        val m = YearMonth.from(today).plusMonths(monthOffset.toLong())
        selectedEpochDay = (if (m == YearMonth.from(today)) today else m.atDay(1)).toEpochDay()
    }

    fun undoable(message: String, undo: () -> Unit) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val r = snackbar.showSnackbar(message, actionLabel = "Undo", duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) undo()
        }
    }

    val dayItems = byDate[selected].orEmpty()
    val monthItems = all.filter { YearMonth.from(it.dueDate()) == month }
    val monthTotal = monthItems
        .filter { it.kind != com.lifeadmin.app.data.Kind.REMINDER && it.amount != null && it.currency.equals(vm.settings.currency, ignoreCase = true) }
        .sumOf { it.amount!! }

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
                        HeaderIconButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month") { changeMonth(-1) }
                        HeaderIconButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month") { changeMonth(1) }
                    }
                }
            }

            item(key = "monthSummary") {
                val parts = mutableListOf(
                    when (monthItems.size) {
                        0 -> "Nothing due this month"
                        1 -> "1 thing due this month"
                        else -> "${monthItems.size} things due this month"
                    }
                )
                if (monthTotal > 0) parts += Format.money(monthTotal, vm.settings.currency)
                Text(
                    parts.joinToString(" · "),
                    color = extra.muted,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            item(key = "grid") {
                AppCard(Modifier.padding(top = 14.dp)) {
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
                            onSelect = { day ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedEpochDay = day.toEpochDay()
                            }
                        )
                    }
                }
            }

            item(key = "dayLabel") {
                SectionLabel(
                    if (selected == today) "Today · ${Format.shortDate(selected)}" else Format.shortDate(selected),
                    extra.ink,
                    Modifier.padding(start = TimelineGutter, top = 10.dp)
                )
            }

            if (dayItems.isEmpty()) {
                item(key = "dayEmpty") {
                    Text(
                        "Nothing due on this day.",
                        color = extra.muted,
                        modifier = Modifier.padding(start = TimelineGutter)
                    )
                }
            } else {
                itemsIndexed(dayItems, key = { _, it -> it.id }) { index, item ->
                    TimelineRow(
                        item = item,
                        today = today,
                        isFirst = index == 0,
                        isLast = index == dayItems.lastIndex,
                        onOpen = { onOpen(item.id) },
                        onRenew = { vm.completeWithFeedback(item) },
                        onDelete = { vm.deleteItem(item) },
                        modifier = Modifier.animateItem(),
                        now = java.time.LocalDateTime.now(),
                        defaultMinute = vm.defaultMinute,
                        completing = item.id in completing
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    today: LocalDate,
    selected: LocalDate,
    byDate: Map<LocalDate, List<Item>>,
    onSelect: (LocalDate) -> Unit,
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
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
        weeks.forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { day ->
                    Box(Modifier.weight(1f).aspectRatio(1f)) {
                        if (day != null) {
                            DayCell(
                                day = day,
                                isToday = day == today,
                                isSelected = day == selected,
                                items = byDate[day].orEmpty(),
                                today = today,
                                onClick = { onSelect(day) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: LocalDate,
    isToday: Boolean,
    isSelected: Boolean,
    items: List<Item>,
    today: LocalDate,
    onClick: () -> Unit,
) {
    val extra = Extra.colors
    val shape = RoundedCornerShape(12.dp)
    val bg by animateColorAsState(if (isSelected) extra.ink else extra.card, label = "cellBg")
    val fg = if (isSelected) extra.onInk else if (day.isBefore(today)) extra.muted else extra.ink
    val todayBorder = MaterialTheme.colorScheme.primary
    val dots = items.take(3).map { urgencyOf(it.daysLeft(today)) }
    val urgencyColors = dots.map { it.colors().dot }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(shape)
            .background(bg)
            .then(if (isToday && !isSelected) Modifier.border(1.5.dp, todayBorder, shape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick),
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
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.height(5.dp)) {
            urgencyColors.forEach { c ->
                Box(
                    Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) extra.onInk else c)
                )
            }
        }
    }
}
