package com.lifeadmin.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeadmin.app.data.Category
import com.lifeadmin.app.data.Cycle
import com.lifeadmin.app.data.Item
import com.lifeadmin.app.data.daysLeft
import com.lifeadmin.app.data.dueDate
import com.lifeadmin.app.ui.theme.Extra
import com.lifeadmin.app.util.Format
import java.time.LocalDate
import java.util.Locale

private enum class Sort(val label: String) { DUE("Due soonest"), NAME("A–Z"), COST("Highest cost") }

@Composable
fun ItemsScreen(
    vm: AppViewModel,
    onOpen: (Long) -> Unit,
    onAdd: () -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    val items by vm.items.collectAsStateWithLifecycle()
    val all = items.orEmpty()
    val today = remember { LocalDate.now() }
    val extra = Extra.colors
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var sort by rememberSaveable { mutableStateOf(Sort.DUE) }

    val counts = all.groupingBy { it.category }.eachCount()
    val q = query.trim().lowercase(Locale.getDefault())
    val shown = all
        .filter { category == null || it.category.name == category }
        .filter {
            q.isEmpty() || it.title.lowercase(Locale.getDefault()).contains(q) ||
                it.notes.lowercase(Locale.getDefault()).contains(q)
        }
        .let { list ->
            when (sort) {
                Sort.DUE -> list.sortedBy { it.dueEpochDay }
                Sort.NAME -> list.sortedBy { it.title.lowercase(Locale.getDefault()) }
                Sort.COST -> list.sortedByDescending { yearlyCost(it) }
            }
        }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = bottomBar
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp, end = 20.dp,
                top = padding.calculateTopPadding() + 20.dp,
                bottom = padding.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "header") {
                PageHeader(
                    eyebrow = if (all.size == 1) "1 item tracked" else "${all.size} items tracked",
                    title = "All items"
                ) {
                    HeaderIconButton(Icons.Filled.Add, "Add item", onAdd)
                }
            }

            item(key = "search") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search by name or notes") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = extra.card,
                        unfocusedContainerColor = extra.card,
                        unfocusedBorderColor = extra.line
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }

            if (counts.isNotEmpty()) {
                item(key = "categories") {
                    val categories = Category.entries.filter { (counts[it] ?: 0) > 0 }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        categories.chunked(2).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                pair.forEach { c ->
                                    CategoryTile(
                                        category = c,
                                        count = counts[c] ?: 0,
                                        selected = category == c.name,
                                        onClick = { category = if (category == c.name) null else c.name },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            item(key = "sort") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(Sort.entries.toList()) { s ->
                        SortPill(s.label, sort == s) { sort = s }
                    }
                }
            }

            if (shown.isEmpty()) {
                item(key = "empty") {
                    Text(
                        if (all.isEmpty()) "Nothing tracked yet. Tap + to add your first item."
                        else "No items match.",
                        color = extra.muted,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            } else {
                items(shown, key = { it.id }) { item ->
                    ItemListRow(item, today, onClick = { onOpen(item.id) }, modifier = Modifier.animateItem())
                }
            }
        }
    }
}

private fun yearlyCost(item: Item): Double =
    if (item.cycle == Cycle.NONE) item.amount ?: 0.0 else (item.amount ?: 0.0) * item.cycle.perMonth * 12

@Composable
private fun CategoryTile(
    category: Category,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val extra = Extra.colors
    val shape = RoundedCornerShape(16.dp)
    val bg by animateColorAsState(if (selected) extra.ink else extra.card, label = "tileBg")
    val fg = if (selected) extra.onInk else extra.ink
    Row(
        modifier = modifier
            .clip(shape)
            .background(bg)
            .then(if (selected) Modifier else Modifier.border(1.dp, extra.line, shape))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (selected) extra.onInk.copy(alpha = 0.15f) else extra.laterBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(category.icon(), null, tint = if (selected) extra.onInk else extra.later, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(category.label, color = fg, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
            Text(
                if (count == 1) "1 item" else "$count items",
                color = if (selected) extra.onInk.copy(alpha = 0.8f) else extra.muted,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun SortPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val extra = Extra.colors
    val shape = RoundedCornerShape(18.dp)
    Text(
        label,
        color = if (selected) extra.onInk else extra.ink,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) extra.ink else extra.card)
            .then(if (selected) Modifier else Modifier.border(1.dp, extra.line, shape))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    )
}

@Composable
fun ItemListRow(item: Item, today: LocalDate, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val extra = Extra.colors
    val days = item.daysLeft(today)
    val urgency = urgencyOf(days)
    val uc = urgency.colors()
    val subtitle = listOfNotNull(
        Format.shortDate(item.dueDate()),
        item.cycle.short.takeIf { item.cycle != Cycle.NONE },
        item.amount?.let { Format.money(it, item.currency) },
    ).joinToString(" · ")

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = extra.card,
        shadowElevation = 1.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (urgency == Urgency.OVERDUE) uc.bg else extra.laterBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    item.category.icon(), item.category.label,
                    tint = if (urgency == Urgency.OVERDUE) uc.fg else extra.later,
                    modifier = Modifier.size(19.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, fontSize = 12.sp, color = extra.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                Format.daysBadge(days),
                color = uc.fg,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(uc.bg)
                    .padding(horizontal = 9.dp, vertical = 5.dp)
            )
        }
    }
}
