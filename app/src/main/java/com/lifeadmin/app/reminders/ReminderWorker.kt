package com.lifeadmin.app.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Daily safety net: re-checks every item and re-sets its alarm, in case the system
 * dropped one (some phones clear alarms when an app is force-stopped).
 */
class ReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        AlarmScheduler.rescheduleAll(applicationContext)
        return Result.success()
    }
}
