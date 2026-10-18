package com.lifeadmin.app.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.lifeadmin.app.MainActivity
import com.lifeadmin.app.data.AlertStyle
import com.lifeadmin.app.data.AppDatabase
import com.lifeadmin.app.data.AppSettings
import com.lifeadmin.app.data.Item
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Keeps one system alarm per item, set for that item's next alert.
 * Exact when the "Alarms & reminders" permission is granted, otherwise as close as Android allows.
 */
object AlarmScheduler {

    fun defaultMinute(context: Context): Int = AppSettings(context).reminderHour * 60

    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun schedule(context: Context, item: Item) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = AlarmReceiver.fireIntent(context, item.id)
        val next = Schedule.nextAlertMillis(item, defaultMinute(context), ZoneId.systemDefault())
        if (next == null) {
            am.cancel(pi)
            return
        }
        val at = maxOf(next, System.currentTimeMillis() + 2_000)
        try {
            when {
                canScheduleExact(context) && item.alertStyle == AlertStyle.ALARM ->
                    am.setAlarmClock(AlarmManager.AlarmClockInfo(at, showIntent(context, item.id)), pi)
                canScheduleExact(context) ->
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                else ->
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    /** The once-a-day summary notification, at the time chosen in Settings. */
    fun scheduleDigest(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            context, DIGEST_REQUEST,
            Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_DIGEST),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val settings = AppSettings(context)
        if (!settings.digestEnabled) {
            am.cancel(pi)
            return
        }
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atStartOfDay().plusMinutes(settings.digestMinute.toLong())
        if (!next.isAfter(now)) next = next.plusDays(1)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, Schedule.toMillis(next, ZoneId.systemDefault()), pi)
    }

    private const val DIGEST_REQUEST = 0x0D16E5

    fun cancel(context: Context, itemId: Long) {
        context.getSystemService(AlarmManager::class.java).cancel(AlarmReceiver.fireIntent(context, itemId))
    }

    /**
     * Brings every item up to date (auto-repeats rolled forward, long-missed alerts skipped)
     * and sets its alarm. Safe to call any time: app start, reboot, time change, after edits.
     */
    suspend fun rescheduleAll(context: Context) {
        val dao = AppDatabase.get(context).itemDao()
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now()
        val nowMs = System.currentTimeMillis()
        val defMin = defaultMinute(context)
        dao.getAll().forEach { original ->
            var item = Schedule.rollForward(original, now, defMin, zone)
            val next = Schedule.nextAlertMillis(item, defMin, zone)
            if (next != null && next < nowMs - Schedule.CATCH_UP_WINDOW_MS) {
                item = item.copy(lastFiredAt = nowMs)
            }
            if (item != original) dao.update(item)
            schedule(context, item)
        }
        scheduleDigest(context)
        WidgetUpdater.update(context)
    }

    /** What the system shows when someone taps the alarm icon in the status bar. */
    private fun showIntent(context: Context, itemId: Long): PendingIntent =
        PendingIntent.getActivity(
            context, itemId.toInt(),
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_SKIP_SPLASH, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
}
