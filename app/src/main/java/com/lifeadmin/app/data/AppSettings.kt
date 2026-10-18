package com.lifeadmin.app.data

import android.content.Context
import java.util.Currency
import java.util.Locale

class AppSettings(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Hour of the day (0-23) when the daily reminder check runs. */
    var reminderHour: Int
        get() = prefs.getInt(KEY_HOUR, 9)
        set(value) = prefs.edit().putInt(KEY_HOUR, value).apply()

    var currency: String
        get() = prefs.getString(KEY_CURRENCY, null) ?: defaultCurrency()
        set(value) = prefs.edit().putString(KEY_CURRENCY, value.uppercase(Locale.ROOT)).apply()

    var askedNotificationPermission: Boolean
        get() = prefs.getBoolean(KEY_ASKED, false)
        set(value) = prefs.edit().putBoolean(KEY_ASKED, value).apply()

    /** True once the welcome screens have been seen. */
    var onboarded: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDED, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDED, value).apply()

    private fun defaultCurrency(): String =
        try {
            Currency.getInstance(Locale.getDefault()).currencyCode
        } catch (e: Exception) {
            "USD"
        }

    private companion object {
        const val KEY_HOUR = "reminder_hour"
        const val KEY_CURRENCY = "currency"
        const val KEY_ASKED = "asked_notification_permission"
        const val KEY_ONBOARDED = "onboarded"
    }
}
