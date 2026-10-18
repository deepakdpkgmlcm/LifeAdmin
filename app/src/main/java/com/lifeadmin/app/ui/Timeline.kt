package com.lifeadmin.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeadmin.app.data.Cycle
import com.lifeadmin.app.data.Item
import com.lifeadmin.app.data.daysLeft
import com.lifeadmin.app.data.dueDate
import com.lifeadmin.app.ui.theme.DmMono
import com.lifeadmin.app.ui.theme.Extra
import com.lifeadmin.app.util.Format
import com.lifeadmin.app.data.AlertStyle
import com.lifeadmin.app.data.Kind
import com.lifeadmin.app.reminders.Schedule
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

// Timeline geometry (shared by the row and its drawn rail line).
private val DATE_W = 44.dp
private val GAP = 10.dp
private val RAIL_W = 12.dp
private val DOT = 12.dp
private val DOT_TOP = 20.dp

/** Left gutter width, so section labels can line up with the cards. */
val TimelineGutter = DATE_W + GAP + RAIL_W + GAP

/**
 * One entry on the timeline: date label, rail dot and a card that can be tapped (edit),
 * swiped right (mark as paid / renewed, repeating items only) or swiped left (delete).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineRow(
    item: Item,
    today: LocalDate,
    isFirst: Boolean,
    isLast: Boolean,
    onOpen: () -> Unit,
    onRenew: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    now: LocalDateTime = LocalDateTime.now(),
    defaultMinute: Int = 9 * 60,
    completing: Boolean = false,
) {
    val occurrence = Schedule.occurrence(item, defaultMinute)
    val urgency = itemUrgency(item, occurrence, now)
    val badge = Format.badge(item, occurrence, now)
    val uc = urgency.colors()
    val extra = Extra.colors
    val primary = MaterialTheme.colorScheme.primary

    Row(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val x = (DATE_W + GAP + RAIL_W / 2).toPx()
                val dotCenter = (DOT_TOP + DOT / 2).toPx()
                val top = if (isFirst) dotCenter else 0f
                val bottom = if (isLast) dotCenter else size.height
                if (bottom > top) {
                    drawLine(extra.line, Offset(x, top), Offset(x, bottom), strokeWidth = 2.dp.toPx())
                }
            },
        horizontalArrangement = Arrangement.spacedBy(GAP)
    ) {
        Text(
            text = Format.timelineDate(item.dueDate(), today),
            fontFamily = DmMono,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            textAlign = TextAlign.End,
            color = if (urgency == Urgency.OVERDUE) uc.fg else extra.muted,
            modifier = Modifier
                .width(DATE_W)
                .padding(top = 15.dp)
        )

        Box(Modifier.width(RAIL_W).padding(top = DOT_TOP)) {
            Box(
                Modifier
                    .size(DOT)
                    .drawBehind {
                        val r = size.minDimension / 2
                        when (urgency) {
                            Urgency.OVERDUE -> {
                                drawCircle(uc.bg, radius = r + 4.dp.toPx())
                                drawCircle(uc.dot, radius = r)
                            }
                            Urgency.SOON -> drawCircle(uc.dot, radius = r)
                            Urgency.LATER -> {
                                drawCircle(extra.card, radius = r)
                                drawCircle(primary, radius = r - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
                            }
                        }
                    }
            )
        }

        Box(
            Modifier
                .weight(1f)
                .padding(top = 4.dp, bottom = 8.dp)
        ) {
            SwipeableCard(
                item = item,
                completing = completing,
                badge = badge,
                urgency = urgency,
                onOpen = onOpen,
                onRenew = onRenew,
                onDelete = onDelete
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableCard(
    item: Item,
    completing: Boolean,
    badge: String,
    urgency: Urgency,
    onOpen: () -> Unit,
    onRenew: () -> Unit,
    onDelete: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val canRenew = true
    val renew by rememberUpdatedState(onRenew)
    val delete by rememberUpdatedState(onDelete)
    val state = rememberSwipeToDismissBoxState(positionalThreshold = { it * 0.4f })

    LaunchedEffect(state.currentValue) {
        when (state.currentValue) {
            SwipeToDismissBoxValue.StartToEnd -> {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                renew()
                state.reset()
            }
            SwipeToDismissBoxValue.EndToStart -> {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                delete()
            }
            SwipeToDismissBoxValue.Settled -> Unit
        }
    }

    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = canRenew,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val direction = state.dismissDirection
            val bg = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primary
                SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.error
                SwipeToDismissBoxValue.Settled -> Color.Transparent
            }
            val fg = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.onPrimary
                else -> MaterialTheme.colorScheme.onError
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(18.dp))
                    .background(bg)
                    .padding(horizontal = 18.dp),
                contentAlignment = if (direction == SwipeToDismissBoxValue.StartToEnd) {
                    Alignment.CenterStart
                } else {
                    Alignment.CenterEnd
                }
            ) {
                if (direction == SwipeToDismissBoxValue.StartToEnd) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = fg)
                        Spacer(Modifier.width(6.dp))
                        Text(item.kind.doneVerb, color = fg, fontWeight = FontWeight.Bold)
                    }
                } else if (direction == SwipeToDismissBoxValue.EndToStart) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Delete", color = fg, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Outlined.Delete, contentDescription = null, tint = fg)
                    }
                }
            }
        }
    ) {
        ItemCard(item = item, completing = completing, badge = badge, urgency = urgency, onOpen = onOpen, onRenew = onRenew)
    }
}

@Composable
private fun ItemCard(
    item: Item,
    completing: Boolean,
    badge: String,
    urgency: Urgency,
    onOpen: () -> Unit,
    onRenew: () -> Unit,
) {
    val extra = Extra.colors
    val uc = urgency.colors()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        when {
            completing -> 0.96f
            pressed -> 0.97f
            else -> 1f
        },
        label = "press"
    )
    val contentAlpha by animateFloatAsState(if (completing) 0.55f else 1f, label = "doneAlpha")

    val subtitle = itemSubtitle(item)

    Surface(
        onClick = onOpen,
        interactionSource = interaction,
        shape = RoundedCornerShape(18.dp),
        color = extra.card,
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = contentAlpha
            }
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            when {
                                completing -> MaterialTheme.colorScheme.primary
                                urgency == Urgency.OVERDUE -> uc.bg
                                else -> extra.laterBg
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (completing) Icons.Filled.Check else item.category.icon(),
                        contentDescription = item.category.label,
                        tint = when {
                            completing -> MaterialTheme.colorScheme.onPrimary
                            urgency == Urgency.OVERDUE -> uc.fg
                            else -> extra.later
                        },
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        textDecoration = if (completing) TextDecoration.LineThrough else null,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = extra.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (completing) item.kind.doneVerb else badge,
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
            if (urgency == Urgency.OVERDUE && !completing) {
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onRenew,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = extra.ink,
                        contentColor = extra.onInk
                    ),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(item.kind.doneLabel, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** Seven days starting today; dots mark days with something due. Tap to filter. */
@Composable
fun WeekStrip(
    today: LocalDate,
    selected: LocalDate?,
    busyDays: Set<LocalDate>,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val extra = Extra.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (i in 0..6) {
            val day = today.plusDays(i.toLong())
            val active = if (selected == null) i == 0 else day == selected
            val bg by animateColorAsState(if (active) extra.ink else extra.card, label = "dayBg")
            val fg by animateColorAsState(if (active) extra.onInk else extra.ink, label = "dayFg")
            val shape = RoundedCornerShape(16.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(60.dp)
                    .clip(shape)
                    .background(bg)
                    .then(if (active) Modifier else Modifier.border(1.dp, extra.line, shape))
                    .clickable(role = Role.Button) { onSelect(day) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = day.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, Locale.getDefault()),
                    fontSize = 11.sp,
                    color = if (active) extra.onInk else extra.muted
                )
                Text(
                    text = day.dayOfMonth.toString(),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = fg
                )
                if (day in busyDays) {
                    Box(
                        Modifier
                            .padding(top = 2.dp)
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(if (active) extra.onInk else extra.soonDot)
                    )
                }
            }
        }
    }
}

/** The teal "N things need you this week" card with three stats. */
@Composable
fun SummaryCard(overdue: Int, nextWeek: Int, perMonth: String, modifier: Modifier = Modifier) {
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val need = overdue + nextWeek
    val headline = when (need) {
        0 -> "All clear this week"
        1 -> "1 thing needs you this week"
        else -> "$need things need you this week"
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.primary)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RollingText(headline) {
            Text(it, color = onPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat(overdue.toString(), "Overdue", Modifier.weight(1f))
            Stat(nextWeek.toString(), "Next 7 days", Modifier.weight(1f))
            Stat(perMonth, "Per month", Modifier.weight(1.15f))
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(onPrimary.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        RollingText(value) {
            Text(
                it,
                color = onPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(label, color = onPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** Text that rolls up to its new value when it changes. */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun RollingText(value: String, content: @Composable (String) -> Unit) {
    AnimatedContent(
        targetState = value,
        transitionSpec = {
            (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
        },
        label = "rolling"
    ) { content(it) }
}

/** Overdue/soon/later, using the exact time for timed items. */
fun itemUrgency(item: Item, occurrence: LocalDateTime, now: LocalDateTime): Urgency {
    if (item.dueMinute != null && occurrence.isBefore(now)) return Urgency.OVERDUE
    val days = java.time.temporal.ChronoUnit.DAYS.between(now.toLocalDate(), occurrence.toLocalDate())
    return urgencyOf(days)
}

/** "10:30 · Every 2 hours · Alarm", "Monthly · $35", "Expires". */
fun itemSubtitle(item: Item): String {
    val parts = mutableListOf<String>()
    item.dueMinute?.let { parts += Format.time(it) }
    if (item.cycle != Cycle.NONE) parts += Format.repeatText(item)
    if (item.kind != Kind.REMINDER) item.amount?.let { parts += Format.money(it, item.currency) }
    if (item.alertStyle == AlertStyle.ALARM) parts += "Alarm"
    if (parts.isEmpty()) parts += if (item.kind == Kind.REMINDER) item.category.label else item.category.dueVerb
    return parts.joinToString(" · ")
}
