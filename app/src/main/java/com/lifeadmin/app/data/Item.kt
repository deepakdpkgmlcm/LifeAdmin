package com.lifeadmin.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class Category(val label: String) {
    DOCUMENT("Documents"),
    SUBSCRIPTION("Subscriptions"),
    BILL("Bills"),
    INSURANCE("Insurance"),
    VEHICLE("Vehicle"),
    WARRANTY("Warranties"),
    HOME("Home"),
    OTHER("Other");

    /** How the due date reads in reminders, e.g. "Expires in 7 days". */
    val dueVerb: String
        get() = when (this) {
            SUBSCRIPTION -> "Renews"
            BILL -> "Due"
            HOME, OTHER -> "Due"
            else -> "Expires"
        }
}

enum class Cycle(val label: String, val short: String) {
    NONE("Doesn't repeat", ""),
    WEEKLY("Every week", "Weekly"),
    MONTHLY("Every month", "Monthly"),
    QUARTERLY("Every 3 months", "Quarterly"),
    YEARLY("Every year", "Yearly");

    fun next(date: LocalDate): LocalDate = when (this) {
        NONE -> date
        WEEKLY -> date.plusWeeks(1)
        MONTHLY -> date.plusMonths(1)
        QUARTERLY -> date.plusMonths(3)
        YEARLY -> date.plusYears(1)
    }

    /** Multiplier to convert one payment into an average monthly cost. */
    val perMonth: Double
        get() = when (this) {
            NONE -> 0.0
            WEEKLY -> 52.0 / 12.0
            MONTHLY -> 1.0
            QUARTERLY -> 1.0 / 3.0
            YEARLY -> 1.0 / 12.0
        }
}

@Entity(tableName = "items")
data class Item(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val category: Category,
    /** Due or expiry date, stored as days since 1970-01-01. */
    val dueEpochDay: Long,
    val cycle: Cycle = Cycle.NONE,
    /** For repeating items: move the due date forward on its own once it passes. */
    val autoRenew: Boolean = false,
    val amount: Double? = null,
    val currency: String = "",
    val notes: String = "",
    /** Comma-separated days before the due date to remind, e.g. "30,7,1,0". */
    val reminderDays: String = DEFAULT_REMINDERS,
    /** Smallest reminder stage already sent, so each reminder fires only once. */
    val lastNotifiedStage: Int = NOT_NOTIFIED,
    val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val NOT_NOTIFIED = Int.MAX_VALUE
        const val OVERDUE_STAGE = -1
        const val DEFAULT_REMINDERS = "30,7,1,0"
    }
}

fun Item.dueDate(): LocalDate = LocalDate.ofEpochDay(dueEpochDay)

fun Item.daysLeft(today: LocalDate = LocalDate.now()): Long =
    ChronoUnit.DAYS.between(today, dueDate())

fun Item.reminderDayList(): List<Int> =
    reminderDays.split(",").mapNotNull { it.trim().toIntOrNull() }
