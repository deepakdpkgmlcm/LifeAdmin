package com.lifeadmin.app.util

import com.lifeadmin.app.data.Cycle
import com.lifeadmin.app.data.Item
import com.lifeadmin.app.data.alertOffsetList
import com.lifeadmin.app.data.isRepeating
import com.lifeadmin.app.data.repeatDayList
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Currency
import java.util.Locale

object Format {
    private val dateFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")
    private val shortDateFmt = DateTimeFormatter.ofPattern("EEE, d MMM")
    private val headerDateFmt = DateTimeFormatter.ofPattern("EEE, d MMMM")
    private val monthShortFmt = DateTimeFormatter.ofPattern("MMM")
    private val timeFmt = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

    fun date(date: LocalDate): String = date.format(dateFormatter)

    /** "Mon, 19 Oct" */
    fun shortDate(date: LocalDate): String = date.format(shortDateFmt)

    /** "SUN, 18 OCTOBER" for the home header. */
    fun headerDate(date: LocalDate): String = date.format(headerDateFmt).uppercase(Locale.getDefault())

    /** Time of day from minutes after midnight, in the phone's 12/24-hour style. */
    fun time(minuteOfDay: Int): String =
        LocalTime.of((minuteOfDay / 60) % 24, minuteOfDay % 60).format(timeFmt)

    fun time(t: LocalDateTime): String = t.toLocalTime().format(timeFmt)

    /** Two-line timeline label: "16\nOCT" this year, "03 MAY\n2027" for other years. */
    fun timelineDate(date: LocalDate, today: LocalDate): String {
        val month = date.format(monthShortFmt).uppercase(Locale.getDefault()).take(3)
        val day = date.dayOfMonth.toString().padStart(2, '0')
        return if (date.year == today.year) "$day\n$month" else "$day $month\n${date.year}"
    }

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

    /**
     * Badge for an item: timed items within a day count down in minutes/hours
     * ("In 25 min", "In 3 h", "10 min late"); everything else uses [daysBadge].
     */
    fun badge(item: Item, occurrence: LocalDateTime, now: LocalDateTime): String {
        if (item.dueMinute != null) {
            val mins = ChronoUnit.MINUTES.between(now, occurrence)
            when {
                mins in 0..0 -> return "Now"
                mins in 1..59 -> return "In $mins min"
                mins in 60 until 24 * 60 -> return "In ${mins / 60} h"
                mins in -59..-1 -> return "${-mins} min late"
                mins in -(24 * 60 - 1)..-60 -> return "${-mins / 60} h late"
            }
        }
        return daysBadge(ChronoUnit.DAYS.between(now.toLocalDate(), occurrence.toLocalDate()))
    }

    /** Sentence fragment for reminders, e.g. "Expires in 7 days". */
    fun dueSentence(verb: String, days: Long): String = when {
        days < -1 -> "$verb ${-days} days ago"
        days == -1L -> "$verb yesterday"
        days == 0L -> "$verb today"
        days == 1L -> "$verb tomorrow"
        else -> "$verb in $days days"
    }

    /** "Every 15 minutes", "Every 2 weeks on Mon, Wed", "Every month · 5 more times". */
    fun repeatText(item: Item): String {
        if (!item.isRepeating) return "Doesn't repeat"
        val n = item.repeatEvery.coerceAtLeast(1)
        val base = when (item.cycle) {
            Cycle.NONE -> "Doesn't repeat"
            Cycle.QUARTERLY -> if (n == 1) "Every 3 months" else "Every ${3 * n} months"
            Cycle.DAILY -> if (n == 1) "Every day" else "Every $n days"
            else -> if (n == 1) "Every ${item.cycle.unitSingular}" else "Every $n ${item.cycle.unitPlural}"
        }
        val days = item.repeatDayList()
        val onDays = if (item.cycle == Cycle.WEEKLY && days.isNotEmpty()) {
            when (days) {
                setOf(1, 2, 3, 4, 5) -> " on weekdays"
                setOf(6, 7) -> " on weekends"
                else -> " on " + days.sorted().joinToString(", ") {
                    DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, Locale.getDefault())
                }
            }
        } else {
            ""
        }
        val end = when {
            item.repeatLimit != null -> " · ${item.repeatLimit} more " + if (item.repeatLimit == 1) "time" else "times"
            item.repeatUntilEpochDay != null -> " · until " + shortDate(LocalDate.ofEpochDay(item.repeatUntilEpochDay))
            else -> ""
        }
        return base + onDays + end
    }

    fun offsetText(minutes: Int): String = when {
        minutes == 0 -> "At the time"
        minutes < 60 -> "$minutes min before"
        minutes < 1440 && minutes % 60 == 0 -> if (minutes == 60) "1 hour before" else "${minutes / 60} hours before"
        minutes % 10080 == 0 && minutes < 43200 -> if (minutes == 10080) "1 week before" else "${minutes / 10080} weeks before"
        minutes % 1440 == 0 -> if (minutes == 1440) "1 day before" else "${minutes / 1440} days before"
        else -> "$minutes min before"
    }

    /** "5 min", "2 h", "1 day", "3 days" for repeat-alert intervals. */
    fun interval(minutes: Int): String = when {
        minutes < 60 -> "$minutes min"
        minutes < 1440 -> if (minutes % 60 == 0) "${minutes / 60} h" else "${minutes / 60} h ${minutes % 60} min"
        minutes == 1440 -> "1 day"
        minutes % 1440 == 0 -> "${minutes / 1440} days"
        else -> "${minutes / 60} h"
    }

    /** "At the time, 15 min before, 1 day before" */
    fun alertsText(item: Item): String {
        val offsets = item.alertOffsetList().sorted()
        if (offsets.isEmpty()) return "No alerts"
        return offsets.joinToString(", ") { offsetText(it) }
    }
}
