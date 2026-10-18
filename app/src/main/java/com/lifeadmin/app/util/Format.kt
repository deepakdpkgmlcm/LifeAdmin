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

    /** Short badge text, e.g. "Today", "In 5 days", "3 days late", "6 months". */
    fun daysBadge(days: Long): String = when {
        days < -1 -> "${-days} days late"
        days == -1L -> "1 day late"
        days == 0L -> "Today"
        days == 1L -> "Tomorrow"
        days < 60 -> "In $days days"
        days < 730 -> "${Math.round(days / 30.44)} months"
        else -> "${days / 365} years"
    }

    private val shortDateFmt = DateTimeFormatter.ofPattern("EEE, d MMM")
    private val headerDateFmt = DateTimeFormatter.ofPattern("EEE, d MMMM")
    private val monthShortFmt = DateTimeFormatter.ofPattern("MMM")

    /** "Mon, 19 Oct" */
    fun shortDate(date: LocalDate): String = date.format(shortDateFmt)

    /** "SUN, 18 OCTOBER" for the home header. */
    fun headerDate(date: LocalDate): String = date.format(headerDateFmt).uppercase(Locale.getDefault())

    /**
     * Two-line timeline label: "16\nOCT" this year, "03 MAY\n2027" for other years.
     */
    fun timelineDate(date: LocalDate, today: LocalDate): String {
        val month = date.format(monthShortFmt).uppercase(Locale.getDefault()).take(3)
        val day = date.dayOfMonth.toString().padStart(2, '0')
        return if (date.year == today.year) "$day\n$month" else "$day $month\n${date.year}"
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
