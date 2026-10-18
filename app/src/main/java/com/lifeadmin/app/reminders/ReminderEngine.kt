package com.lifeadmin.app.reminders

import com.lifeadmin.app.data.Cycle
import com.lifeadmin.app.data.Item
import com.lifeadmin.app.data.ItemDao
import com.lifeadmin.app.data.daysLeft
import com.lifeadmin.app.data.dueDate
import com.lifeadmin.app.data.reminderDayList
import java.time.LocalDate

/** Pure reminder rules, kept free of Android code so they can be unit-tested. */
object ReminderEngine {

    /**
     * The reminder stage an item is in today: the smallest chosen "days before" value
     * that has been reached, [Item.OVERDUE_STAGE] if past due, or null if no reminder is due yet.
     */
    fun stageFor(item: Item, today: LocalDate): Int? {
        val days = item.daysLeft(today)
        if (days < 0) return Item.OVERDUE_STAGE
        return item.reminderDayList().filter { days <= it }.minOrNull()
    }

    /** True if this stage hasn't been notified yet (stages only ever go down). */
    fun shouldNotify(item: Item, stage: Int): Boolean = stage < item.lastNotifiedStage

    /**
     * For auto-renewing items whose date has passed, move the due date forward to the
     * next upcoming renewal. Returns the same instance if nothing changed.
     */
    fun rolledForward(item: Item, today: LocalDate): Item {
        if (!item.autoRenew || item.cycle == Cycle.NONE) return item
        var date = item.dueDate()
        if (!date.isBefore(today)) return item
        while (date.isBefore(today)) date = item.cycle.next(date)
        return item.copy(dueEpochDay = date.toEpochDay(), lastNotifiedStage = Item.NOT_NOTIFIED)
    }

    /** Applies [rolledForward] to every item in the database and returns the current list. */
    suspend fun rollForwardAll(dao: ItemDao, today: LocalDate): List<Item> =
        dao.getAll().map { item ->
            val rolled = rolledForward(item, today)
            if (rolled !== item) dao.update(rolled)
            rolled
        }
}
