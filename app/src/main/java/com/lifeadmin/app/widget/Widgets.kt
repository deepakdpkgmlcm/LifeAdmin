package com.lifeadmin.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.lifeadmin.app.MainActivity
import com.lifeadmin.app.data.AppDatabase
import com.lifeadmin.app.data.Kind
import com.lifeadmin.app.reminders.AlarmScheduler
import com.lifeadmin.app.reminders.ItemOps
import com.lifeadmin.app.reminders.Schedule
import com.lifeadmin.app.reminders.WidgetUpdater
import com.lifeadmin.app.util.Format
import java.time.LocalDateTime

/** One line on a widget. */
data class WidgetRow(val id: Long, val title: String, val whenText: String, val overdue: Boolean, val kind: Kind)

object WidgetData {
    /** Overdue and today's items (Today widget), or everything upcoming (Next up), soonest first. */
    suspend fun load(context: Context, todayOnly: Boolean, limit: Int): List<WidgetRow> {
        val defMin = AlarmScheduler.defaultMinute(context)
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        return AppDatabase.get(context).itemDao().getAll()
            .filter { !it.done }
            .map { it to Schedule.occurrence(it, defMin) }
            .filter { (_, at) -> !todayOnly || !at.toLocalDate().isAfter(today) }
            .sortedBy { it.second }
            .take(limit)
            .map { (item, at) ->
                val date = at.toLocalDate()
                val overdue = date.isBefore(today) || (item.dueMinute != null && at.isBefore(now))
                val time = item.dueMinute?.let { Format.time(it) }
                val whenText = when {
                    date.isBefore(today) -> "Overdue · " + Format.shortDate(date)
                    date == today -> time ?: "Today"
                    date == today.plusDays(1) -> "Tomorrow" + (time?.let { " · $it" } ?: "")
                    else -> Format.shortDate(date) + (time?.let { " · $it" } ?: "")
                }
                WidgetRow(item.id, item.title, whenText, overdue, item.kind)
            }
    }
}

private fun openApp(context: Context) =
    Intent(context, MainActivity::class.java)
        .putExtra(MainActivity.EXTRA_SKIP_SPLASH, true)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

private fun addReminder(context: Context) =
    Intent(context, MainActivity::class.java)
        .setAction(MainActivity.SHORTCUT_PREFIX + "ADD_REMINDER")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

/** "Done" / "Paid" / "Renewed" tapped on a widget. */
class WidgetDoneAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[ITEM] ?: return
        val item = AppDatabase.get(context).itemDao().get(id) ?: return
        if (!item.done) ItemOps.complete(context, item)
        WidgetUpdater.updateNow(context)
    }

    companion object {
        val ITEM = ActionParameters.Key<Long>("itemId")
    }
}

@Composable
private fun DoneButton(row: WidgetRow) {
    Box(
        GlanceModifier
            .size(34.dp)
            .cornerRadius(17.dp)
            .background(GlanceTheme.colors.secondaryContainer)
            .clickable(actionRunCallback<WidgetDoneAction>(actionParametersOf(WidgetDoneAction.ITEM to row.id))),
        contentAlignment = Alignment.Center
    ) {
        Text("✓", style = TextStyle(color = GlanceTheme.colors.onSecondaryContainer, fontSize = 16.sp, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun ItemLine(context: Context, row: WidgetRow) {
    Row(
        GlanceModifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(GlanceModifier.defaultWeight().clickable(actionStartActivity(openApp(context)))) {
            Text(
                row.title,
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            )
            Text(
                row.whenText + if (row.kind != Kind.REMINDER) " · " + row.kind.label else "",
                maxLines = 1,
                style = TextStyle(
                    color = if (row.overdue) GlanceTheme.colors.error else GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 12.sp
                )
            )
        }
        Spacer(GlanceModifier.width(8.dp))
        DoneButton(row)
    }
}

/** Today's and overdue items, each with a Done button, plus a quick "+" to add one. */
class TodayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val rows = WidgetData.load(context, todayOnly = true, limit = 8)
        provideContent {
            GlanceTheme {
                Column(
                    GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.widgetBackground)
                        .cornerRadius(20.dp)
                        .padding(14.dp)
                ) {
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(GlanceModifier.defaultWeight().clickable(actionStartActivity(openApp(context)))) {
                            Text("Today", style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold))
                            Text(
                                when (rows.size) {
                                    0 -> "Nothing due"
                                    1 -> "1 thing to do"
                                    else -> "${rows.size} things to do"
                                },
                                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp)
                            )
                        }
                        Box(
                            GlanceModifier
                                .size(36.dp)
                                .cornerRadius(18.dp)
                                .background(GlanceTheme.colors.primary)
                                .clickable(actionStartActivity(addReminder(context))),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("+", style = TextStyle(color = GlanceTheme.colors.onPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold))
                        }
                    }
                    Spacer(GlanceModifier.height(6.dp))
                    if (rows.isEmpty()) {
                        Text(
                            "All clear for today 🎉",
                            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                            modifier = GlanceModifier.padding(top = 8.dp)
                        )
                    } else {
                        LazyColumn {
                            items(rows, itemId = { it.id }) { row -> ItemLine(context, row) }
                        }
                    }
                }
            }
        }
    }
}

/** The single next thing coming up. */
class NextUpWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val next = WidgetData.load(context, todayOnly = false, limit = 1).firstOrNull()
        provideContent {
            GlanceTheme {
                Column(
                    GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.widgetBackground)
                        .cornerRadius(20.dp)
                        .padding(14.dp)
                        .clickable(actionStartActivity(openApp(context)))
                ) {
                    Text("NEXT UP", style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold))
                    Spacer(GlanceModifier.height(4.dp))
                    if (next == null) {
                        Text("Nothing coming up", style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold))
                    } else {
                        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(GlanceModifier.defaultWeight()) {
                                Text(next.title, maxLines = 2, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold))
                                Text(
                                    next.whenText, maxLines = 1,
                                    style = TextStyle(color = if (next.overdue) GlanceTheme.colors.error else GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp)
                                )
                            }
                            Spacer(GlanceModifier.width(8.dp))
                            DoneButton(next)
                        }
                    }
                }
            }
        }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

class NextUpWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextUpWidget()
}
