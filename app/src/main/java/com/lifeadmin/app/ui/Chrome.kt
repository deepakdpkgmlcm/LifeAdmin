package com.lifeadmin.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeadmin.app.ui.theme.DmMono
import com.lifeadmin.app.ui.theme.Extra

/** The four main tabs. */
enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Timeline", Icons.Outlined.Timeline),
    CALENDAR("calendar", "Calendar", Icons.Outlined.CalendarMonth),
    ITEMS("items", "Items", Icons.Outlined.Inventory2),
    DONE("done", "Done", Icons.Outlined.TaskAlt),
    INSIGHTS("insights", "Insights", Icons.Outlined.Insights),
}

@Composable
fun AppBottomBar(current: Tab, onSelect: (Tab) -> Unit) {
    val extra = Extra.colors
    NavigationBar(containerColor = extra.card, tonalElevation = 0.dp) {
        Tab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == current,
                onClick = { if (tab != current) onSelect(tab) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(tab.label, fontWeight = FontWeight.SemiBold) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = extra.later,
                    selectedTextColor = extra.ink,
                    indicatorColor = extra.laterBg,
                    unselectedIconColor = extra.muted,
                    unselectedTextColor = extra.muted
                )
            )
        }
    }
}

/** The page heading used across screens: a small mono line over a big bold title. */
@Composable
fun PageHeader(
    eyebrow: String,
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val extra = Extra.colors
    Row(verticalAlignment = Alignment.Top, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(
                eyebrow.uppercase(),
                fontFamily = DmMono,
                fontSize = 12.sp,
                letterSpacing = 0.7.sp,
                color = extra.muted
            )
            Spacer(Modifier.height(4.dp))
            Text(title, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
        }
        trailing?.invoke()
    }
}

/** Square-ish outlined icon button used in headers (settings, back, edit...). */
@Composable
fun HeaderIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    val extra = Extra.colors
    val shape = RoundedCornerShape(14.dp)
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(44.dp)
            .clip(shape)
            .background(extra.card)
            .border(1.dp, extra.line, shape)
    ) {
        Icon(icon, contentDescription = description, tint = extra.ink)
    }
}

/** White rounded card with a soft shadow: the basic surface of the design. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Extra.colors.card,
        shadowElevation = 1.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

/** Section label + card as one block (lazy list items must have a single root). */
@Composable
fun LabeledCard(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        SectionLabel(label, Extra.colors.muted)
        AppCard(content = content)
    }
}

/** A key/value line inside a card, e.g. "Repeats · Monthly". */
@Composable
fun InfoRow(label: String, value: String) {
    val extra = Extra.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = extra.muted, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Shows the app-wide Undo bar (done / deleted) on whichever screen is open. */
@Composable
fun UndoSnackbarEffect(vm: AppViewModel, snackbar: androidx.compose.material3.SnackbarHostState) {
    val event by vm.undoEvent.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(event) {
        val e = event ?: return@LaunchedEffect
        vm.consumeUndo(e)
        snackbar.currentSnackbarData?.dismiss()
        val result = snackbar.showSnackbar(
            message = e.message,
            actionLabel = if (e.restore != null) "Undo" else null,
            duration = androidx.compose.material3.SnackbarDuration.Short
        )
        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) e.restore?.let { vm.restore(it) }
    }
}
