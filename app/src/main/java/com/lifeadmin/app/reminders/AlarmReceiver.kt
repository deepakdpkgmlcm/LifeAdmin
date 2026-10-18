package com.lifeadmin.app.reminders

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.lifeadmin.app.data.AppDatabase
import com.lifeadmin.app.data.History
import com.lifeadmin.app.data.Item
import com.lifeadmin.app.data.Kind
import com.lifeadmin.app.data.isRepeating
import com.lifeadmin.app.util.Format
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Handles our own alarms and the Done / Snooze buttons on reminders. Not reachable by other apps. */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        val occurrence = intent.getLongExtra(EXTRA_OCCURRENCE, 0L)
        val minutes = intent.getIntExtra(EXTRA_MINUTES, 10)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (id > 0) {
                    when (intent.action) {
                        ACTION_FIRE -> fire(app, id)
                        ACTION_DONE -> ItemOps.completeFromAlert(app, id, occurrence)
                        ACTION_SNOOZE -> ItemOps.snooze(app, id, minutes)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun fire(context: Context, id: Long) {
        val dao = AppDatabase.get(context).itemDao()
        val item = dao.get(id) ?: return
        val zone = ZoneId.systemDefault()
        val defMin = AlarmScheduler.defaultMinute(context)
        val nowMs = System.currentTimeMillis()
        val now = LocalDateTime.now()
        val due = Schedule.nextAlertMillis(item, defMin, zone) ?: return
        if (due > nowMs + 60_000) {
            // Woke up early (e.g. the item was edited); just set the right time.
            AlarmScheduler.schedule(context, item)
            return
        }
        val occurrence = Schedule.occurrence(item, defMin)
        val snoozed = item.snoozedUntil != null && item.snoozedUntil <= nowMs + 60_000
        Notifier.alert(context, item, alertText(item, occurrence, now, snoozed), Schedule.toMillis(occurrence, zone))

        var updated = item.copy(
            lastFiredAt = nowMs,
            snoozedUntil = item.snoozedUntil?.takeIf { it > nowMs + 60_000 },
        )
        updated = Schedule.rollForward(updated, now, defMin, zone)
        dao.update(updated)
        AlarmScheduler.schedule(context, updated)
    }

    companion object {
        const val ACTION_FIRE = "com.lifeadmin.app.action.FIRE"
        const val ACTION_DONE = "com.lifeadmin.app.action.DONE"
        const val ACTION_SNOOZE = "com.lifeadmin.app.action.SNOOZE"
        const val EXTRA_ID = "itemId"
        const val EXTRA_OCCURRENCE = "occurrence"
        const val EXTRA_MINUTES = "minutes"

        fun fireIntent(context: Context, itemId: Long): PendingIntent =
            PendingIntent.getBroadcast(
                context, itemId.toInt(),
                Intent(context, AlarmReceiver::class.java)
                    .setAction(ACTION_FIRE)
                    .setData(Uri.parse("lifeadmin://item/$itemId/fire"))
                    .putExtra(EXTRA_ID, itemId),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

        fun actionIntent(
            context: Context,
            action: String,
            itemId: Long,
            occurrence: Long,
            minutes: Int = 0,
        ): PendingIntent =
            PendingIntent.getBroadcast(
                context, itemId.toInt(),
                actionBroadcast(context, action, itemId, occurrence, minutes),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

        fun actionBroadcast(context: Context, action: String, itemId: Long, occurrence: Long, minutes: Int): Intent =
            Intent(context, AlarmReceiver::class.java)
                .setAction(action)
                .setData(Uri.parse("lifeadmin://item/$itemId/$action/$minutes"))
                .putExtra(EXTRA_ID, itemId)
                .putExtra(EXTRA_OCCURRENCE, occurrence)
                .putExtra(EXTRA_MINUTES, minutes)

        /** The line under the title in a reminder, e.g. "In 15 min · 10:30 · Every 2 hours". */
        fun alertText(item: Item, occurrence: LocalDateTime, now: LocalDateTime, snoozed: Boolean): String {
            val parts = mutableListOf<String>()
            if (snoozed) parts += "Snoozed reminder"
            if (item.dueMinute != null) {
                parts += Format.badge(item, occurrence, now)
                parts += Format.time(occurrence)
            } else {
                val days = java.time.temporal.ChronoUnit.DAYS.between(now.toLocalDate(), occurrence.toLocalDate())
                parts += Format.dueSentence(item.category.dueVerb, days)
                parts += Format.shortDate(occurrence.toLocalDate())
            }
            if (item.isRepeating) parts += Format.repeatText(item)
            if (item.kind != Kind.REMINDER) item.amount?.let { parts += Format.money(it, item.currency) }
            return parts.joinToString(" · ")
        }
    }
}

/** Shared "done" / "snooze" logic used by the app screens, notifications and the alarm screen. */
object ItemOps {

    /** Completes an item and returns what happened (for Undo). */
    suspend fun complete(context: Context, item: Item): Schedule.Completion {
        val db = AppDatabase.get(context)
        val c = Schedule.complete(item, System.currentTimeMillis(), AlarmScheduler.defaultMinute(context))
        val historyId = db.historyDao().insert(c.history)
        db.itemDao().update(c.updated)
        Notifier.cancel(context, item.id.toInt())
        AlarmScheduler.schedule(context, c.updated)
        return c.copy(history = c.history.copy(id = historyId))
    }

    /**
     * "Done" pressed on a reminder. If the item already moved on to its next occurrence
     * (auto-repeat), only record it; otherwise complete it normally.
     */
    suspend fun completeFromAlert(context: Context, id: Long, occurrenceMillis: Long) {
        val db = AppDatabase.get(context)
        val item = db.itemDao().get(id) ?: return
        Notifier.cancel(context, id.toInt())
        val zone = ZoneId.systemDefault()
        val current = Schedule.toMillis(Schedule.occurrence(item, AlarmScheduler.defaultMinute(context)), zone)
        if (occurrenceMillis != 0L && current != occurrenceMillis) {
            db.historyDao().insert(
                History(
                    itemId = item.id,
                    itemTitle = item.title,
                    category = item.category,
                    dueEpochDay = Instant.ofEpochMilli(occurrenceMillis).atZone(zone).toLocalDate().toEpochDay(),
                    amount = if (item.kind == Kind.REMINDER) null else item.amount,
                    currency = item.currency,
                    kind = item.kind.name,
                )
            )
            return
        }
        complete(context, item)
    }

    suspend fun snooze(context: Context, id: Long, minutes: Int) {
        val dao = AppDatabase.get(context).itemDao()
        val item = dao.get(id) ?: return
        Notifier.cancel(context, id.toInt())
        val updated = item.copy(snoozedUntil = System.currentTimeMillis() + minutes * 60_000L)
        dao.update(updated)
        AlarmScheduler.schedule(context, updated)
    }
}

/** Re-sets all alarms after a reboot, app update, clock/time-zone change or permission change. */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                AlarmScheduler.rescheduleAll(app)
            } finally {
                pending.finish()
            }
        }
    }
}
