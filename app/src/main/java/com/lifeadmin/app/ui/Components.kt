package com.lifeadmin.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeadmin.app.data.Category
import com.lifeadmin.app.ui.theme.Extra

fun Category.icon(): ImageVector = when (this) {
    Category.TASK -> Icons.Outlined.TaskAlt
    Category.PERSONAL -> Icons.Outlined.Person
    Category.WORK -> Icons.Outlined.Work
    Category.HEALTH -> Icons.Outlined.FavoriteBorder
    Category.DOCUMENT -> Icons.Outlined.Badge
    Category.SUBSCRIPTION -> Icons.Outlined.Subscriptions
    Category.BILL -> Icons.Outlined.Receipt
    Category.INSURANCE -> Icons.Outlined.Shield
    Category.VEHICLE -> Icons.Outlined.DirectionsCar
    Category.WARRANTY -> Icons.Outlined.VerifiedUser
    Category.HOME -> Icons.Outlined.Home
    Category.OTHER -> Icons.Outlined.Category
}

/** How close an item is: drives the dot, tile and pill colors. */
enum class Urgency { OVERDUE, SOON, LATER }

fun urgencyOf(daysLeft: Long): Urgency = when {
    daysLeft < 0 -> Urgency.OVERDUE
    daysLeft <= 7 -> Urgency.SOON
    else -> Urgency.LATER
}

data class UrgencyColors(val fg: Color, val bg: Color, val dot: Color)

@Composable
fun Urgency.colors(): UrgencyColors {
    val c = Extra.colors
    return when (this) {
        Urgency.OVERDUE -> UrgencyColors(c.overdue, c.overdueBg, c.overdue)
        Urgency.SOON -> UrgencyColors(c.soon, c.soonBg, c.soonDot)
        Urgency.LATER -> UrgencyColors(c.later, c.laterBg, c.later)
    }
}

/** Small uppercase section label, e.g. "OVERDUE". */
@Composable
fun SectionLabel(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = modifier.padding(top = 10.dp, bottom = 6.dp)
    )
}

/** A read-only text field that opens a menu of options when tapped. */
@Composable
fun <T> SelectField(
    label: String,
    selected: T,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = optionLabel(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Outlined.ArrowDropDown, contentDescription = null) },
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        )
        // Transparent layer on top so a tap anywhere opens the menu.
        Box(
            Modifier
                .matchParentSize()
                .clickable { expanded = true }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

/** A read-only text field that runs [onClick] when tapped (e.g. to open a date picker). */
@Composable
fun ClickableField(
    label: String,
    value: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(icon, contentDescription = null) },
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        )
        Box(
            Modifier
                .matchParentSize()
                .clickable(onClick = onClick)
        )
    }
}
