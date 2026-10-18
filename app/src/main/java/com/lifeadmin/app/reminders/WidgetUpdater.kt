package com.lifeadmin.app.reminders

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.lifeadmin.app.widget.NextUpWidget
import com.lifeadmin.app.widget.TodayWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Refreshes the home-screen widgets after data changes. */
object WidgetUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun update(context: Context) {
        val app = context.applicationContext
        scope.launch { updateNow(app) }
    }

    suspend fun updateNow(context: Context) {
        runCatching { TodayWidget().updateAll(context) }
        runCatching { NextUpWidget().updateAll(context) }
    }
}
