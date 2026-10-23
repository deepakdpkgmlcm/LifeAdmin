package com.lifeadmin.app.reminders

import com.lifeadmin.app.data.Category
import com.lifeadmin.app.data.Cycle
import com.lifeadmin.app.data.Item
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CalendarDatesTest {
    private val oct1 = LocalDate.of(2026, 10, 1)
    private val oct31 = LocalDate.of(2026, 10, 31)

    private fun item(due: LocalDate, cycle: Cycle = Cycle.NONE, every: Int = 1, days: String = "",
                     limit: Int? = null, until: LocalDate? = null, minute: Int? = 9 * 60, rule: String = "") =
        Item(
            title = "T", category = Category.TASK, dueEpochDay = due.toEpochDay(), dueMinute = minute,
            cycle = cycle, repeatEvery = every, repeatDays = days, repeatLimit = limit,
            repeatUntilEpochDay = until?.toEpochDay(), repeatRule = rule
        )

    private fun d(day: Int) = LocalDate.of(2026, 10, day)

    @Test
    fun oneOffOnlyOnItsDate() {
        assertEquals(listOf(d(14)), Schedule.datesInRange(item(d(14)), oct1, oct31, 540))
        assertEquals(emptyList<LocalDate>(), Schedule.datesInRange(item(LocalDate.of(2026, 11, 2)), oct1, oct31, 540))
    }

    @Test
    fun weeklyOnChosenDays() {
        // Mondays and Thursdays from Thu 15 Oct 2026.
        val dates = Schedule.datesInRange(item(d(15), Cycle.WEEKLY, days = "1,4"), oct1, oct31, 540)
        assertEquals(listOf(d(15), d(19), d(22), d(26), d(29)), dates)
    }

    @Test
    fun monthlyItemStartedEarlierShowsThisMonth() {
        val rent = item(LocalDate.of(2026, 8, 5), Cycle.MONTHLY)
        assertEquals(listOf(d(5)), Schedule.datesInRange(rent, oct1, oct31, 540))
    }

    @Test
    fun repeatLimitAndEndDate() {
        assertEquals(listOf(d(20), d(21), d(22)), Schedule.datesInRange(item(d(20), Cycle.DAILY, limit = 2), oct1, oct31, 540))
        assertEquals(listOf(d(27), d(28)), Schedule.datesInRange(item(d(27), Cycle.DAILY, until = d(28)), oct1, oct31, 540))
    }

    @Test
    fun hourlyCountsOncePerDayAndSkipWeekends() {
        val water = item(d(29), Cycle.HOURS, every = 2)
        assertEquals(listOf(d(29), d(30), d(31)), Schedule.datesInRange(water, oct1, oct31, 540))
        // Fri 23 Oct, weekdays only: skips 24-25.
        val standup = item(d(23), Cycle.DAILY, rule = Schedule.RULE_SKIP_WEEKENDS)
        assertEquals(listOf(d(23), d(26), d(27)), Schedule.datesInRange(standup, oct1, d(27), 540))
    }
}
