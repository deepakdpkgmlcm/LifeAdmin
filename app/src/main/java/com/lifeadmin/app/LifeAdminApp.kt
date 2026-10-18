package com.lifeadmin.app

import android.app.Application
import com.lifeadmin.app.data.AppSettings
import com.lifeadmin.app.reminders.AlarmScheduler
import com.lifeadmin.app.reminders.Notifier
import com.lifeadmin.app.reminders.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LifeAdminApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        ReminderScheduler.scheduleDaily(this, AppSettings(this).reminderHour, reschedule = false)
        appScope.launch { AlarmScheduler.rescheduleAll(this@LifeAdminApp) }
    }
}
