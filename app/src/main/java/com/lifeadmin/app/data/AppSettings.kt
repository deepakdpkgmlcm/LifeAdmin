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

    /** Alerts new items of this kind start with (minutes before, comma separated, or "none"). */
    fun defaultAlerts(kind: Kind): String =
        prefs.getString("alerts_${kind.name}", null) ?: when (kind) {
            Kind.REMINDER -> "0"
            Kind.PAYMENT -> "4320,1440,0"
            Kind.RENEWAL -> "43200,10080,1440,0"
        }

    fun setDefaultAlerts(kind: Kind, value: String) =
        prefs.edit().putString("alerts_${kind.name}", value).apply()

    fun defaultStyle(kind: Kind): AlertStyle =
        prefs.getString("style_${kind.name}", null)
            ?.let { name -> AlertStyle.entries.firstOrNull { it.name == name } }
            ?: AlertStyle.NOTIFICATION

    fun setDefaultStyle(kind: Kind, style: AlertStyle) =
        prefs.edit().putString("style_${kind.name}", style.name).apply()

    /** True once the long first-launch animation has played. */
    var splashSeen: Boolean
        get() = prefs.getBoolean("splash_seen", false)
        set(value) = prefs.edit().putBoolean("splash_seen", value).apply()

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
