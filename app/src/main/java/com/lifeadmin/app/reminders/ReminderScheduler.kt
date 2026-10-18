package com.lifeadmin.app.reminders

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Schedules the daily reminder check with WorkManager. WorkManager keeps the schedule
 * across app restarts and phone reboots, and needs no special alarm permissions.
 */
object ReminderScheduler {
    private const val DAILY_WORK = "daily_reminders"
    private const val NOW_WORK = "reminders_now"

    fun scheduleDaily(context: Context, hour: Int, reschedule: Boolean) {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(hour, 0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delayMinutes = Duration.between(now, next).toMinutes()

        val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            DAILY_WORK,
            if (reschedule) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE
            else ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /** Runs a check right away (used for the settings "test" flow). */
    fun checkNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            NOW_WORK,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ReminderWorker>().build()
        )
    }
}
