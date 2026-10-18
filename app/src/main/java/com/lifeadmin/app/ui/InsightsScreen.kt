package com.lifeadmin.app.ui

import androidx.compose.ui.semantics.semantics

import androidx.compose.ui.semantics.contentDescription

import androidx.compose.runtime.mutableStateOf

import androidx.compose.runtime.setValue

import androidx.compose.animation.core.animateFloatAsState

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeadmin.app.data.Category
import com.lifeadmin.app.data.Cycle
import com.lifeadmin.app.data.Item
import com.lifeadmin.app.data.Kind
import com.lifeadmin.app.data.daysLeft
import com.lifeadmin.app.ui.theme.Extra
import com.lifeadmin.app.util.Format
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun InsightsScreen(
    vm: AppViewModel,
    onOpen: (Long) -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    val items by vm.items.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val all = items.orEmpty().filter { !it.done && it.kind != Kind.REMINDER }
    val today = remember { LocalDate.now() }
    val extra = Extra.colors
    val currency = vm.settings.currency
    val conv = remember(currency) { vm.settings.converter() }
    // Everything with an amount, converted into the main currency where a rate is known.
    val inCurrency = all.mapNotNull { i ->
        i.amount?.let { conv(it, i.currency) }?.let { i.copy(amount = it, currency = currency) }
    }
    val otherCurrencies = all.count { i -> i.amount != null && conv(i.amount, i.currency) == null }

    val recurring = inCurrency.filter { it.cycle != Cycle.NONE }
    val perMonth = recurring.sumOf { it.amount!! * it.cycle.perMonthFor(it.repeatEvery) }
    val byCategory: List<Pair<Category, Double>> = recurring
        .groupBy { it.category }
        .map { (c, list) -> c to list.sumOf { it.amount!! * it.cycle.perMonthFor(it.repeatEvery) } }
        .sortedByDescending { it.second }
    val maxCategory = byCategory.maxOfOrNull { it.second } ?: 0.0

    val next30 = all.filter { it.daysLeft(today) in 0..30 }
    val next30Total = next30.sumOf { i -> i.amount?.let { conv(it, i.currency) } ?: 0.0 }

    val thisYear = history.filter {
        Instant.ofEpochMilli(it.paidAt).atZone(ZoneId.systemDefault()).year == today.year
    }
    val paidThisYear = thisYear.sumOf { h -> h.amount?.let { conv(it, h.currency) } ?: 0.0 }

    val biggest = inCurrency
        .sortedByDescending { if (it.cycle == Cycle.NONE) it.amount!! else it.amount!! * it.cycle.perMonthFor(it.repeatEvery) * 12 }
        .take(3)

    fun money(v: Double) = Format.money(Math.round(v).toDouble(), currency)

    // Paid per month, last six months (spending trend).
    val trend: List<Pair<java.time.YearMonth, Double>> = remember(history, currency) {
        val now = java.time.YearMonth.from(today)
        val months = (5 downTo 0).map { now.minusMonths(it.toLong()) }
        val sums = history
            .filter { it.amount != null }
            .groupBy { java.time.YearMonth.from(Instant.ofEpochMilli(it.paidAt).atZone(ZoneId.systemDefault())) }
            .mapValues { (_, list) -> list.sumOf { h -> conv(h.amount!!, h.currency) ?: 0.0 } }
        months.map { it to (sums[it] ?: 0.0) }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, bottomBar = bottomBar) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp, end = 20.dp,
                top = padding.calculateTopPadding() + 20.dp,
                bottom = padding.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "header") { PageHeader(eyebrow = "Your money", title = "Insights") }

            item(key = "hero") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val on = MaterialTheme.colorScheme.onPrimary
                    Text("Recurring costs", color = on, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    RollingText(if (perMonth > 0) money(perMonth) else "—") {
                        Text(it, color = on, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Text(
                        if (perMonth > 0) "per month · ${money(perMonth * 12)} per year" else "Add amounts to repeating items to see this",
                        color = on.copy(alpha = 0.9f),
                        fontSize = 13.sp
                    )
                }
            }

            item(key = "stats") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MiniStat("Due next 30 days", if (next30Total > 0) money(next30Total) else "${next30.size}",
                        if (next30Total > 0) "${next30.size} items" else "items", Modifier.weight(1f))
                    MiniStat("Paid this year", if (paidThisYear > 0) money(paidThisYear) else "${thisYear.size}",
                        if (paidThisYear > 0) "${thisYear.size} payments" else "payments", Modifier.weight(1f))
                }
            }

            if (trend.any { it.second > 0 }) {
                item(key = "trend") {
                    LabeledCard("Spending trend · last 6 months") {
                        TrendChart(trend, ::money)
                    }
                }
            }

            if (byCategory.isNotEmpty()) {
                item(key = "byCategory") {
                    LabeledCard("Monthly cost by category") {
                        byCategory.forEach { (c, v) ->
                            CategoryBar(c, money(v), if (maxCategory > 0) (v / maxCategory).toFloat() else 0f)
                        }
                    }
                }
            }

            if (biggest.isNotEmpty()) {
                item(key = "biggest") {
                    LabeledCard("Biggest commitments (per year)") {
                        biggest.forEachIndexed { index, item ->
                            if (index > 0) HorizontalDivider(color = extra.line)
                            BigRow(item, money(yearly(item)), onClick = { onOpen(item.id) })
                        }
                    }
                }
            }

            if (thisYear.isNotEmpty()) {
                item(key = "recent") {
                    LabeledCard("Recent payments") {
                        thisYear.take(5).forEachIndexed { index, h ->
                            if (index > 0) HorizontalDivider(color = extra.line)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(h.itemTitle, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(Format.date(LocalDate.ofEpochDay(h.dueEpochDay)), color = extra.muted, fontSize = 12.sp)
                                }
                                h.amount?.let { Text(Format.money(it, h.currency), fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                }
            }

            if (otherCurrencies > 0) {
                item(key = "note") {
                    Text(
                        "$otherCurrencies item(s) in other currencies aren't included in these totals. " +
                            "Add an exchange rate into $currency in Settings › Money to include them.",
                        color = extra.muted,
                        fontSize = 12.sp
                    )
                }
            }

            if (all.isEmpty()) {
                item(key = "empty") {
                    Text("Add payments or renewals with amounts and your spending summary will appear here.", color = extra.muted)
                }
            }
        }
    }
}

private fun yearly(item: Item): Double =
    if (item.cycle == Cycle.NONE) item.amount ?: 0.0 else (item.amount ?: 0.0) * item.cycle.perMonthFor(item.repeatEvery) * 12

@Composable
private fun MiniStat(label: String, value: String, caption: String, modifier: Modifier) {
    val extra = Extra.colors
    AppCard(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, color = extra.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            RollingText(value) {
                Text(it, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(caption, color = extra.muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun CategoryBar(category: Category, value: String, fraction: Float) {
    val extra = Extra.colors
    val progress = remember { Animatable(0f) }
    LaunchedEffect(fraction) { progress.animateTo(fraction, tween(800, easing = FastOutSlowInEasing)) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(category.icon(), null, tint = extra.later, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(category.label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(value, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(extra.laterBg)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress.value.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
private fun BigRow(item: Item, yearly: String, onClick: () -> Unit) {
    val extra = Extra.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(extra.laterBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(item.category.icon(), null, tint = extra.later, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(
            item.title,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        androidx.compose.material3.TextButton(onClick = onClick) {
            Text(yearly, fontWeight = FontWeight.Bold, color = extra.ink)
        }
    }
}


/** Bars for money paid each month; the current month is highlighted. */
@Composable
private fun TrendChart(data: List<Pair<java.time.YearMonth, Double>>, money: (Double) -> String) {
    val extra = Extra.colors
    val max = data.maxOf { it.second }.takeIf { it > 0 } ?: 1.0
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val last = data.last()
    val prev = data[data.size - 2]
    Text(
        when {
            prev.second <= 0 -> "${money(last.second)} paid this month"
            else -> {
                val change = ((last.second - prev.second) / prev.second * 100).toInt()
                "${money(last.second)} this month · " + when {
                    change > 0 -> "$change% more than last month"
                    change < 0 -> "${-change}% less than last month"
                    else -> "same as last month"
                }
            }
        },
        color = extra.muted, fontSize = 13.sp
    )
    Row(
        Modifier
            .fillMaxWidth()
            .height(150.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        data.forEachIndexed { i, (month, value) ->
            val fraction by animateFloatAsState(
                targetValue = if (shown) (value / max).toFloat() else 0f,
                animationSpec = tween(600, delayMillis = i * 60),
                label = "bar"
            )
            val current = i == data.lastIndex
            Column(
                Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) {
                        contentDescription = month.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault()) + ": " + money(value)
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .fillMaxHeight(fraction.coerceAtLeast(0.02f))
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                        .background(if (current) MaterialTheme.colorScheme.primary else extra.laterBg)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    month.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()),
                    fontSize = 11.sp,
                    color = if (current) extra.ink else extra.muted,
                    fontWeight = if (current) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}
