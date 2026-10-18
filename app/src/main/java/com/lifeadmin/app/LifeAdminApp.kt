package com.lifeadmin.app

import android.app.Application
import com.lifeadmin.app.data.AppDatabase
import com.lifeadmin.app.data.AppSettings
import com.lifeadmin.app.reminders.Notifier
import com.lifeadmin.app.reminders.ReminderEngine
import com.lifeadmin.app.reminders.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

class LifeAdminApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Notifier.createChannel(this)
        ReminderScheduler.scheduleDaily(this, AppSettings(this).reminderHour, reschedule = false)
        // Keep auto-renewing items' dates current as soon as the app opens.
        appScope.launch {
            ReminderEngine.rollForwardAll(AppDatabase.get(this@LifeAdminApp).itemDao(), LocalDate.now())
        }
    }
}
