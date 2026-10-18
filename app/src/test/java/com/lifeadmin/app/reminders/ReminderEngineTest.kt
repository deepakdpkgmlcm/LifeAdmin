package com.lifeadmin.app.reminders

import com.lifeadmin.app.data.Category
import com.lifeadmin.app.data.Cycle
import com.lifeadmin.app.data.Item
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReminderEngineTest {
    private val today = LocalDate.of(2026, 10, 18)

    private fun item(
        due: LocalDate,
        reminders: String = "30,7,1,0",
        cycle: Cycle = Cycle.NONE,
        autoRenew: Boolean = false,
        lastStage: Int = Item.NOT_NOTIFIED,
    ) = Item(
        id = 1,
        title = "Test",
        category = Category.DOCUMENT,
        dueEpochDay = due.toEpochDay(),
        cycle = cycle,
        autoRenew = autoRenew,
        reminderDays = reminders,
        lastNotifiedStage = lastStage,
    )

    @Test
    fun noReminderWhenFarAway() {
        assertNull(ReminderEngine.stageFor(item(today.plusDays(45)), today))
    }

    @Test
    fun picksSmallestReachedStage() {
        assertEquals(30, ReminderEngine.stageFor(item(today.plusDays(20)), today))
        assertEquals(7, ReminderEngine.stageFor(item(today.plusDays(3)), today))
        assertEquals(1, ReminderEngine.stageFor(item(today.plusDays(1)), today))
        assertEquals(0, ReminderEngine.stageFor(item(today), today))
    }

    @Test
    fun overdueStage() {
        assertEquals(Item.OVERDUE_STAGE, ReminderEngine.stageFor(item(today.minusDays(2)), today))
    }

    @Test
    fun notifiesEachStageOnlyOnce() {
        val fresh = item(today.plusDays(5))
        assertTrue(ReminderEngine.shouldNotify(fresh, 7))
        val sent = fresh.copy(lastNotifiedStage = 7)
        assertFalse(ReminderEngine.shouldNotify(sent, 7))
        assertTrue(ReminderEngine.shouldNotify(sent, 1))
    }

    @Test
    fun autoRenewMovesDateForward() {
        val sub = item(LocalDate.of(2026, 8, 5), cycle = Cycle.MONTHLY, autoRenew = true, lastStage = 0)
        val rolled = ReminderEngine.rolledForward(sub, today)
        assertEquals(LocalDate.of(2026, 11, 5).toEpochDay(), rolled.dueEpochDay)
        assertEquals(Item.NOT_NOTIFIED, rolled.lastNotifiedStage)
    }

    @Test
    fun monthEndStaysAtMonthEnd() {
        val feb = Cycle.MONTHLY.next(LocalDate.of(2027, 1, 31))
        assertEquals(LocalDate.of(2027, 2, 28), feb)
        assertEquals(LocalDate.of(2027, 3, 31), Cycle.MONTHLY.next(feb))
        assertEquals(LocalDate.of(2027, 2, 15), Cycle.MONTHLY.next(LocalDate.of(2027, 1, 15)))
    }

    @Test
    fun nonAutoRenewStaysOverdue() {
        val bill = item(today.minusDays(3), cycle = Cycle.MONTHLY, autoRenew = false)
        assertSame(bill, ReminderEngine.rolledForward(bill, today))
    }
}
