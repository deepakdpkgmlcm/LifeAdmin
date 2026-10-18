package com.lifeadmin.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeadmin.app.data.History
import com.lifeadmin.app.data.Kind
import com.lifeadmin.app.data.resolvedKind
import com.lifeadmin.app.ui.theme.DmMono
import com.lifeadmin.app.ui.theme.Extra
import com.lifeadmin.app.util.Format
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private val TABS: List<Kind?> = listOf(null, Kind.REMINDER, Kind.PAYMENT, Kind.RENEWAL)

private fun tabName(kind: Kind?): String = when (kind) {
    null -> "All"
    Kind.REMINDER -> "Reminders"
    Kind.PAYMENT -> "Payments"
    Kind.RENEWAL -> "Expiry"
}

/** Everything you've completed, newest first, with a tab per type and category chips. */
@Composable
fun DoneScreen(
    vm: AppViewModel,
    onOpen: (Long) -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    val history by vm.history.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val existingIds = items.orEmpty().map { it.id }.toSet()
    val extra = Extra.colors
    val snackbar = remember { SnackbarHostState() }
    UndoSnackbarEffect(vm, snackbar)

    var tabIndex by rememberSaveable { mutableStateOf(0) }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    val kind = TABS[tabIndex]
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()

    val ofKind = history.filter { kind == null || it.resolvedKind() == kind }
    val categories = ofKind.map { it.category }.distinct().sortedBy { it.ordinal }
    val shown = ofKind.filter { category == null || it.category.name == category }

    fun dayOf(h: History): LocalDate = Instant.ofEpochMilli(h.paidAt).atZone(zone).toLocalDate()
    val groups = shown.groupBy { h ->
        val days = ChronoUnit.DAYS.between(dayOf(h), today)
        when {
            days <= 0L -> "Today"
            days == 1L -> "Yesterday"
            days < 7 -> "This week"
            days < 31 -> "This month"
            else -> "Earlier"
        }
    }
    val weekCount = ofKind.count { ChronoUnit.DAYS.between(dayOf(it), today) < 7 }
    val currency = vm.settings.currency
    val conv = remember(currency) { vm.settings.converter() }
    val paidThisMonth = ofKind
        .filter { dayOf(it).month == today.month && dayOf(it).year == today.year }
        .sumOf { h -> h.amount?.let { conv(it, h.currency) } ?: 0.0 }

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
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "header") {
                PageHeader(
                    eyebrow = buildString {
                        append(if (weekCount == 1) "1 done this week" else "$weekCount done this week")
                        if (paidThisMonth > 0) append(" · " + Format.money(paidThisMonth, currency) + " paid this month")
                    },
                    title = "Done"
                )
            }

            item(key = "tabs") {
                TabRow(
                    selectedTabIndex = tabIndex,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = extra.ink,
                    indicator = { positions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(positions[tabIndex]),
                            color = extra.ink
                        )
                    },
                    divider = {},
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    TABS.forEachIndexed { index, k ->
                        val count = history.count { k == null || it.resolvedKind() == k }
                        Tab(
                            selected = tabIndex == index,
                            onClick = {
                                tabIndex = index
                                category = null
                            },
                            text = {
                                Text(
                                    "${tabName(k)}\n$count",
                                    fontWeight = if (tabIndex == index) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp,
                                    lineHeight = 16.sp,
                                    maxLines = 2,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            },
                            selectedContentColor = extra.ink,
                            unselectedContentColor = extra.muted
                        )
                    }
                }
            }

            if (categories.size > 1) {
                item(key = "categories") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = category == null,
                                onClick = { category = null },
                                label = { Text("All categories") }
                            )
                        }
                        items(categories) { c ->
                            FilterChip(
                                selected = category == c.name,
                                onClick = { category = if (category == c.name) null else c.name },
                                label = { Text(c.label) },
                                leadingIcon = { Icon(c.icon(), null, Modifier.size(18.dp)) }
                            )
                        }
                    }
                }
            }

            if (shown.isEmpty()) {
                item(key = "empty") {
                    Text(
                        when (kind) {
                            null -> "Nothing done yet. Swipe an item right or tap Done, and it lands here."
                            Kind.REMINDER -> "No reminders done yet."
                            Kind.PAYMENT -> "No payments marked as paid yet."
                            Kind.RENEWAL -> "No renewals recorded yet."
                        },
                        color = extra.muted,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            } else {
                listOf("Today", "Yesterday", "This week", "This month", "Earlier").forEach { label ->
                    val list = groups[label].orEmpty()
                    if (list.isNotEmpty()) {
                        item(key = "label_$label") {
                            SectionLabel(label, extra.muted, Modifier.animateItem())
                        }
                        items(list, key = { "h_${it.id}" }) { h ->
                            DoneRow(
                                h = h,
                                zone = zone,
                                canOpen = h.itemId in existingIds,
                                onClick = { onOpen(h.itemId) },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DoneRow(h: History, zone: ZoneId, canOpen: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val extra = Extra.colors
    val kind = h.resolvedKind()
    val at = LocalDateTime.ofInstant(Instant.ofEpochMilli(h.paidAt), zone)
    val subtitle = listOfNotNull(
        kind.doneVerb + " " + Format.time(at),
        h.amount?.let { Format.money(it, h.currency) },
        h.category.label,
    ).joinToString(" · ")

    Surface(
        onClick = onClick,
        enabled = canOpen,
        shape = RoundedCornerShape(18.dp),
        color = extra.card,
        shadowElevation = 1.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(h.itemTitle, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, fontSize = 12.sp, color = extra.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text("DUE", fontFamily = DmMono, fontSize = 10.sp, color = extra.muted)
                Text(
                    Format.timelineDate(LocalDate.ofEpochDay(h.dueEpochDay), LocalDate.now()).replace("\n", " "),
                    fontFamily = DmMono,
                    fontSize = 12.sp,
                    color = extra.ink
                )
            }
        }
    }
}
