package com.lifeadmin.app.util

import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

object Format {
    private val dateFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")

    fun date(date: LocalDate): String = date.format(dateFormatter)

    fun money(amount: Double, currencyCode: String): String {
        val whole = amount % 1.0 == 0.0
        return try {
            val f = NumberFormat.getCurrencyInstance()
            f.currency = Currency.getInstance(currencyCode.uppercase(Locale.ROOT))
            f.minimumFractionDigits = if (whole) 0 else 2
            f.maximumFractionDigits = if (whole) 0 else 2
            f.format(amount)
        } catch (e: Exception) {
            val number = if (whole) amount.toLong().toString() else String.format(Locale.getDefault(), "%.2f", amount)
            if (currencyCode.isBlank()) number else "$currencyCode $number"
        }
    }

    /** Short badge text, e.g. "Today", "In 5 days", "3 days late". */
    fun daysBadge(days: Long): String = when {
        days < -1 -> "${-days} days late"
        days == -1L -> "1 day late"
        days == 0L -> "Today"
        days == 1L -> "Tomorrow"
        else -> "In $days days"
    }

    /** Sentence fragment for reminders, e.g. "Expires in 7 days". */
    fun dueSentence(verb: String, days: Long): String = when {
        days < -1 -> "$verb ${-days} days ago"
        days == -1L -> "$verb yesterday"
        days == 0L -> "$verb today"
        days == 1L -> "$verb tomorrow"
        else -> "$verb in $days days"
    }
}
