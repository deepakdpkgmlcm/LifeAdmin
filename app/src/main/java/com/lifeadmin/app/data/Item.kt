package com.lifeadmin.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** What sort of thing this is; decides the main action and which fields matter. */
enum class Kind(val label: String, val doneVerb: String, val doneLabel: String) {
    REMINDER("Reminder", "Done", "Mark as done"),
    PAYMENT("Payment", "Paid", "Mark as paid"),
    RENEWAL("Expiry", "Renewed", "Mark as renewed");
}

enum class Category(val label: String, val defaultKind: Kind) {
    TASK("Tasks", Kind.REMINDER),
    PERSONAL("Personal", Kind.REMINDER),
    WORK("Work", Kind.REMINDER),
    HEALTH("Health", Kind.REMINDER),
    HOME("Home", Kind.REMINDER),
    BILL("Bills", Kind.PAYMENT),
    SUBSCRIPTION("Subscriptions", Kind.PAYMENT),
    INSURANCE("Insurance", Kind.PAYMENT),
    DOCUMENT("Documents", Kind.RENEWAL),
    VEHICLE("Vehicle", Kind.RENEWAL),
    WARRANTY("Warranties", Kind.RENEWAL),
    OTHER("Other", Kind.REMINDER);

    /** How the due date reads in reminders, e.g. "Expires in 7 days". */
    val dueVerb: String
        get() = when (defaultKind) {
            Kind.RENEWAL -> "Expires"
            Kind.PAYMENT -> if (this == SUBSCRIPTION) "Renews" else "Due"
            Kind.REMINDER -> "Due"
        }
}

/**
 * Repeat unit. Combined with [Item.repeatEvery] ("every 15 MINUTES", "every 2 WEEKS").
 * QUARTERLY is kept for items created before custom intervals existed.
 */
enum class Cycle(val label: String, val short: String, val unitSingular: String, val unitPlural: String) {
    NONE("Doesn't repeat", "", "", ""),
    MINUTES("Every N minutes", "Minutes", "minute", "minutes"),
    HOURS("Every N hours", "Hourly", "hour", "hours"),
    DAILY("Every N days", "Daily", "day", "days"),
    WEEKLY("Every N weeks", "Weekly", "week", "weeks"),
    MONTHLY("Every N months", "Monthly", "month", "months"),
    QUARTERLY("Every 3 months", "Quarterly", "quarter", "quarters"),
    YEARLY("Every N years", "Yearly", "year", "years");

    /** Average payments per month for one interval, used for spending totals. */
    fun perMonthFor(every: Int): Double {
        val n = every.coerceAtLeast(1).toDouble()
        return when (this) {
            NONE -> 0.0
            MINUTES -> 0.0 // too frequent to be a payment
            HOURS -> 0.0
            DAILY -> 30.44 / n
            WEEKLY -> 52.0 / 12.0 / n
            MONTHLY -> 1.0 / n
            QUARTERLY -> 1.0 / 3.0 / n
            YEARLY -> 1.0 / 12.0 / n
        }
    }

    /** Back-compat for code that only knows date-based repeats. */
    val perMonth: Double get() = perMonthFor(1)

    /** Next date for date-based units (month-end dates stay at month end). */
    fun next(date: LocalDate, every: Int = 1): LocalDate {
        val n = every.coerceAtLeast(1).toLong()
        return when (this) {
            NONE, MINUTES, HOURS -> date
            DAILY -> date.plusDays(n)
            WEEKLY -> date.plusWeeks(n)
            MONTHLY -> addMonths(date, n)
            QUARTERLY -> addMonths(date, 3 * n)
            YEARLY -> date.plusYears(n)
        }
    }

    companion object {
        /** Keeps month-end dates at month end (31 Jan -> 28 Feb -> 31 Mar), instead of drifting. */
        fun addMonths(date: LocalDate, months: Long): LocalDate {
            val result = date.plusMonths(months)
            return if (date.dayOfMonth == date.lengthOfMonth()) {
                result.withDayOfMonth(result.lengthOfMonth())
            } else {
                result
            }
        }
    }
}

enum class AlertStyle(val label: String) {
    NOTIFICATION("Notification"),
    ALARM("Alarm"),
}

@Entity(tableName = "items")
data class Item(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val category: Category,
    /** Due or expiry date, stored as days since 1970-01-01. */
    val dueEpochDay: Long,
    val cycle: Cycle = Cycle.NONE,
    /** Repeating items: move to the next occurrence on their own once this one passes. */
    val autoRenew: Boolean = false,
    val amount: Double? = null,
    val currency: String = "",
    val notes: String = "",
    /** Legacy (v1-v2) reminder days; new items use [alertOffsets]. */
    val reminderDays: String = DEFAULT_REMINDERS,
    /** Legacy (v1-v2). */
    val lastNotifiedStage: Int = NOT_NOTIFIED,
    val createdAt: Long = System.currentTimeMillis(),

    // ---- v3: reminder features ----
    val kind: Kind = category.defaultKind,
    /** Time of day in minutes after midnight; null = all day (alerts use the default time). */
    val dueMinute: Int? = null,
    /** Repeat every N units of [cycle]. */
    val repeatEvery: Int = 1,
    /** For weekly repeats: ISO weekdays (1 = Monday ... 7 = Sunday), comma separated; empty = same weekday. */
    val repeatDays: String = "",
    /** Occurrences left after the current one; null = forever. */
    val repeatLimit: Int? = null,
    /** Last date a repeat may fall on; null = no end date. */
    val repeatUntilEpochDay: Long? = null,
    /**
     * Alerts as minutes before the due time, comma separated ("0,15,1440").
     * "" = derive from legacy [reminderDays]; "none" = no alerts.
     */
    val alertOffsets: String = "",
    val alertStyle: AlertStyle = AlertStyle.NOTIFICATION,
    /** Re-alert every N minutes after the due time (0 = off)... */
    val nagEveryMin: Int = 0,
    /** ...this many times. */
    val nagCount: Int = 0,
    /** When an alert for this item last went off (epoch millis). */
    val lastFiredAt: Long = 0,
    val done: Boolean = false,
    val doneAt: Long? = null,
    /** A snoozed alert waiting to go off (epoch millis). */
    val snoozedUntil: Long? = null,
) {
    companion object {
        const val NOT_NOTIFIED = Int.MAX_VALUE
        const val OVERDUE_STAGE = -1
        const val DEFAULT_REMINDERS = "30,7,1,0"
        const val NO_ALERTS = "none"
    }
}

fun Item.dueDate(): LocalDate = LocalDate.ofEpochDay(dueEpochDay)

fun Item.daysLeft(today: LocalDate = LocalDate.now()): Long =
    ChronoUnit.DAYS.between(today, dueDate())

fun Item.reminderDayList(): List<Int> =
    reminderDays.split(",").mapNotNull { it.trim().toIntOrNull() }

/** Alert offsets in minutes before the due time. */
fun Item.alertOffsetList(): List<Int> = when (alertOffsets.trim()) {
    "" -> reminderDayList().map { it * 1440 }
    Item.NO_ALERTS -> emptyList()
    else -> alertOffsets.split(",").mapNotNull { it.trim().toIntOrNull() }.filter { it >= 0 }
}

fun Item.repeatDayList(): Set<Int> =
    repeatDays.split(",").mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..7 }.toSet()

val Item.isRepeating: Boolean get() = cycle != Cycle.NONE
