package com.lifeadmin.app.reminders

import android.content.Context
import com.lifeadmin.app.data.AppDatabase
import com.lifeadmin.app.data.AppSettings
import com.lifeadmin.app.data.Kind
import com.lifeadmin.app.data.dueDate
import com.lifeadmin.app.util.Format
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** The morning summary: what's due today, what's overdue, what's coming soon. */
object Digest {

    data class Summary(val title: String, val lines: List<String>)

    suspend fun build(context: Context): Summary? {
        val items = AppDatabase.get(context).itemDao().getAll().filter { !it.done }
        val defMin = AppSettings(context).reminderHour * 60
        val currency = AppSettings(context).currency
        val now = LocalDateTime.now()
        val today = now.toLocalDate()

        val overdue = items.filter { Schedule.occurrence(it, defMin).toLocalDate().isBefore(today) }
        val dueToday = items.filter { Schedule.occurrence(it, defMin).toLocalDate() == today }
        val soonExpiring = items.filter {
            it.kind == Kind.RENEWAL &&
                ChronoUnit.DAYS.between(today, it.dueDate()) in setOf(7L, 30L)
        }
        if (overdue.isEmpty() && dueToday.isEmpty() && soonExpiring.isEmpty()) return null

        val parts = mutableListOf<String>()
        val reminders = dueToday.count { it.kind == Kind.REMINDER }
        val payments = dueToday.filter { it.kind == Kind.PAYMENT }
        if (reminders > 0) parts += if (reminders == 1) "1 reminder" else "$reminders reminders"
        if (payments.isNotEmpty()) {
            val conv = AppSettings(context).converter()
            val total = payments.sumOf { p -> p.amount?.let { conv(it, p.currency) } ?: 0.0 }
            parts += (if (payments.size == 1) "1 bill" else "${payments.size} bills") +
                (if (total > 0) " (${Format.money(total, currency)})" else "")
        }
        val title = when {
            parts.isNotEmpty() -> "Today: " + parts.joinToString(", ")
            overdue.isNotEmpty() -> "${overdue.size} overdue"
            else -> "Coming up soon"
        }

        val lines = mutableListOf<String>()
        dueToday.sortedBy { it.dueMinute ?: defMin }.take(5).forEach { item ->
            lines += (item.dueMinute?.let { Format.time(it) + " · " } ?: "") + item.title
        }
        if (overdue.isNotEmpty()) lines += "Overdue: " + overdue.take(3).joinToString(", ") { it.title } +
            if (overdue.size > 3) " +${overdue.size - 3}" else ""
        soonExpiring.forEach { item ->
            lines += "${item.title} expires in ${ChronoUnit.DAYS.between(today, item.dueDate())} days"
        }
        return Summary(title, lines)
    }

    suspend fun show(context: Context) {
        val summary = build(context) ?: return
        Notifier.digest(context, summary.title, summary.lines)
    }

    @Suppress("unused")
    private fun isToday(d: LocalDate) = d == LocalDate.now()
}
