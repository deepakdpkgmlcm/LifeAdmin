package com.lifeadmin.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeadmin.app.data.AppSettings
import com.lifeadmin.app.data.Cycle
import com.lifeadmin.app.data.Item
import com.lifeadmin.app.data.daysLeft
import com.lifeadmin.app.util.Format
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: AppViewModel,
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit,
    onSettings: () -> Unit,
) {
    val items by vm.items.collectAsStateWithLifecycle()
    val today = remember { LocalDate.now() }
    var filter by rememberSaveable { mutableStateOf<String?>(null) }

    NotificationPermissionRequest(vm.settings, trigger = !items.isNullOrEmpty())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LifeAdmin", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            if (!items.isNullOrEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onAdd,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Add") }
                )
            }
        }
    ) { padding ->
        val all = items
        when {
            all == null -> Unit // still loading
            all.isEmpty() -> EmptyState(Modifier.padding(padding), onAdd)
            else -> {
                val shown = all.filter { filter == null || it.category.name == filter }
                val categories = all.map { it.category }.distinct().sortedBy { it.ordinal }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = padding.calculateTopPadding() + 4.dp,
                        bottom = padding.calculateBottomPadding() + 96.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item(key = "summary") { SummaryRow(all, today, vm.settings.currency) }

                    if (categories.size > 1) {
                        item(key = "filters") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                item {
                                    FilterChip(
                                        selected = filter == null,
                                        onClick = { filter = null },
                                        label = { Text("All") }
                                    )
                                }
                                items(categories) { category ->
                                    FilterChip(
                                        selected = filter == category.name,
                                        onClick = {
                                            filter = if (filter == category.name) null else category.name
                                        },
                                        label = { Text(category.label) },
                                        leadingIcon = {
                                            Icon(category.icon(), null, Modifier.size(18.dp))
                                        }
                                    )
                                }
                            }
                        }
                    }

                    section("Overdue", shown.filter { it.daysLeft(today) < 0 }, today, onOpen)
                    section("Next 7 days", shown.filter { it.daysLeft(today) in 0..7 }, today, onOpen)
                    section("This month", shown.filter { it.daysLeft(today) in 8..30 }, today, onOpen)
                    section("Later", shown.filter { it.daysLeft(today) > 30 }, today, onOpen)
                }
            }
        }
    }
}

private fun LazyListScope.section(
    title: String,
    list: List<Item>,
    today: LocalDate,
    onOpen: (Long) -> Unit,
) {
    if (list.isEmpty()) return
    item(key = "header_$title") { SectionHeader(title, list.size) }
    items(list, key = { it.id }) { item ->
        ItemCard(item = item, today = today, onClick = { onOpen(item.id) })
    }
}

@Composable
private fun SummaryRow(items: List<Item>, today: LocalDate, currency: String) {
    val overdue = items.count { it.daysLeft(today) < 0 }
    val nextWeek = items.count { it.daysLeft(today) in 0..7 }
    val monthly = items
        .filter { it.cycle != Cycle.NONE && it.amount != null && it.currency.equals(currency, ignoreCase = true) }
        .sumOf { it.amount!! * it.cycle.perMonth }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        StatCard(
            value = overdue.toString(),
            label = "Overdue",
            highlight = if (overdue > 0) MaterialTheme.colorScheme.error else null,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            value = nextWeek.toString(),
            label = "Next 7 days",
            highlight = null,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            value = if (monthly > 0) Format.money(Math.round(monthly).toDouble(), currency) else "—",
            label = "Per month",
            highlight = null,
            modifier = Modifier.weight(1.2f)
        )
    }
}

@Composable
private fun StatCard(value: String, label: String, highlight: Color?, modifier: Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = highlight ?: MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier, onAdd: () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Outlined.EventAvailable,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Nothing to remember yet",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Add passports, subscriptions, bills, insurance and warranties. " +
                "You'll get a reminder before anything expires or renews.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAdd) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Add your first item")
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
