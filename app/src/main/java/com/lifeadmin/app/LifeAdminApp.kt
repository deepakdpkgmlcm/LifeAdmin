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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import com.lifeadmin.app.data.AppDatabase
import com.lifeadmin.app.reminders.WidgetUpdater

class LifeAdminApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        com.lifeadmin.app.ui.theme.ThemeState.load(this)
        Notifier.createChannels(this)
        ReminderScheduler.scheduleDaily(this, AppSettings(this).reminderHour, reschedule = false)
        appScope.launch {
            AlarmScheduler.rescheduleAll(this@LifeAdminApp)
            com.lifeadmin.app.util.Attachments.cleanUp(this@LifeAdminApp)
        }
        com.lifeadmin.app.data.AutoBackup.schedule(this)
        // Keep home-screen widgets in step with any change made in the app.
        appScope.launch {
            AppDatabase.get(this@LifeAdminApp).itemDao().observeAll().drop(1).collectLatest {
                delay(400)
                WidgetUpdater.updateNow(this@LifeAdminApp)
            }
        }
    }
}
