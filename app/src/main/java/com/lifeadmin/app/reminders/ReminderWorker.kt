package com.lifeadmin.app.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lifeadmin.app.data.AppDatabase
import com.lifeadmin.app.data.daysLeft
import com.lifeadmin.app.data.dueDate
import com.lifeadmin.app.util.Format
import java.time.LocalDate

/** Runs once a day: rolls renewals forward and sends any reminders that are due. */
class ReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val dao = AppDatabase.get(applicationContext).itemDao()
        val today = LocalDate.now()
        val items = ReminderEngine.rollForwardAll(dao, today)

        // If notifications are off, don't mark anything as sent, so reminders
        // still arrive once the person turns notifications back on.
        if (!Notifier.canNotify(applicationContext)) return Result.success()

        for (item in items) {
            val stage = ReminderEngine.stageFor(item, today) ?: continue
            if (!ReminderEngine.shouldNotify(item, stage)) continue

            val days = item.daysLeft(today)
            val parts = mutableListOf(
                Format.dueSentence(item.category.dueVerb, days),
                Format.date(item.dueDate())
            )
            item.amount?.let { parts += Format.money(it, item.currency) }

            Notifier.show(
                context = applicationContext,
                id = item.id.toInt(),
                title = item.title,
                text = parts.joinToString(" · ")
            )
            dao.update(item.copy(lastNotifiedStage = stage))
        }
        return Result.success()
    }
}
