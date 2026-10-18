package com.lifeadmin.app.reminders

import com.lifeadmin.app.data.AlertStyle
import com.lifeadmin.app.data.Category
import com.lifeadmin.app.data.Cycle
import com.lifeadmin.app.data.Item
import com.lifeadmin.app.data.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class ScheduleTest {
    private val zone = ZoneOffset.UTC
    private val nine = 9 * 60

    private fun item(
        date: LocalDate,
        minute: Int? = null,
        cycle: Cycle = Cycle.NONE,
        every: Int = 1,
        days: String = "",
        alerts: String = "0",
        nagEvery: Int = 0,
        nagCount: Int = 0,
        autoRenew: Boolean = true,
        limit: Int? = null,
        until: LocalDate? = null,
        lastFired: Long = 0,
        kind: Kind = Kind.REMINDER,
        amount: Double? = null,
    ) = Item(
        id = 1, title = "Test", category = Category.TASK, kind = kind,
        dueEpochDay = date.toEpochDay(), dueMinute = minute, cycle = cycle, repeatEvery = every,
        repeatDays = days, alertOffsets = alerts, nagEveryMin = nagEvery, nagCount = nagCount,
        autoRenew = autoRenew, repeatLimit = limit, repeatUntilEpochDay = until?.toEpochDay(),
        lastFiredAt = lastFired, alertStyle = AlertStyle.NOTIFICATION, amount = amount,
    )

    private val d = LocalDate.of(2026, 10, 18) // a Sunday
    private fun at(h: Int, m: Int, date: LocalDate = d) = date.atTime(h, m)

    @Test
    fun allDayUsesDefaultTime() {
        assertEquals(at(9, 0), Schedule.occurrence(item(d), nine))
        assertEquals(at(14, 30), Schedule.occurrence(item(d, minute = 14 * 60 + 30), nine))
    }

    @Test
    fun everyFifteenMinutes() {
        val it = item(d, minute = 10 * 60, cycle = Cycle.MINUTES, every = 15)
        assertEquals(at(10, 15), Schedule.nextOccurrence(it, at(10, 0), nine))
        assertEquals(at(11, 0), Schedule.nextOccurrence(it, at(10, 52), nine))
        assertEquals(at(11, 0), Schedule.nextOccurrence(it, at(10, 45), nine))
    }

    @Test
    fun everyTwoHours() {
        val it = item(d, minute = 8 * 60, cycle = Cycle.HOURS, every = 2)
        assertEquals(at(14, 0), Schedule.nextOccurrence(it, at(13, 5), nine))
    }

    @Test
    fun everyThreeDays() {
        val it = item(d, minute = 7 * 60, cycle = Cycle.DAILY, every = 3)
        assertEquals(at(7, 0, d.plusDays(3)), Schedule.nextOccurrence(it, at(7, 0), nine))
        assertEquals(at(7, 0, d.plusDays(9)), Schedule.nextOccurrence(it, at(8, 0, d.plusDays(6)), nine))
    }

    @Test
    fun weekdaysOnly() {
        // Mon-Fri at 10:00, starting Sunday 18 Oct -> Monday 19 Oct, then Friday 23 -> Monday 26
        val it = item(d, minute = 600, cycle = Cycle.WEEKLY, days = "1,2,3,4,5")
        assertEquals(at(10, 0, d.plusDays(1)), Schedule.nextOccurrence(it, at(10, 0), nine))
        assertEquals(at(10, 0, d.plusDays(8)), Schedule.nextOccurrence(it, at(11, 0, d.plusDays(5)), nine))
    }

    @Test
    fun monthEndStaysAtMonthEnd() {
        val feb = Cycle.MONTHLY.next(LocalDate.of(2027, 1, 31))
        assertEquals(LocalDate.of(2027, 2, 28), feb)
        assertEquals(LocalDate.of(2027, 3, 31), Cycle.MONTHLY.next(feb))
        val it = item(LocalDate.of(2027, 1, 31), cycle = Cycle.MONTHLY)
        assertEquals(LocalDate.of(2027, 2, 28).atTime(9, 0), Schedule.nextOccurrence(it, LocalDateTime.of(2027, 1, 31, 9, 0), nine))
    }

    @Test
    fun repeatLimitAndUntilEndTheSeries() {
        assertNull(Schedule.nextOccurrence(item(d, cycle = Cycle.DAILY, limit = 0), at(9, 0), nine))
        assertNull(Schedule.nextOccurrence(item(d, cycle = Cycle.DAILY, until = d), at(9, 0), nine))
        val limited = item(d, cycle = Cycle.DAILY, limit = 2)
        val moved = Schedule.movedTo(limited, at(9, 0, d.plusDays(1)))
        assertEquals(1, moved.repeatLimit)
    }

    @Test
    fun alertTimesIncludeBeforeAndRepeatAlerts() {
        val it = item(d, minute = 600, alerts = "0,15,1440", nagEvery = 5, nagCount = 2)
        assertEquals(
            listOf(at(10, 0, d.minusDays(1)), at(9, 45), at(10, 0), at(10, 5), at(10, 10)),
            Schedule.alertTimes(it, nine)
        )
        assertEquals(at(10, 10), Schedule.occurrenceEnd(it, nine))
    }

    @Test
    fun nextAlertSkipsAlreadyFired() {
        val firedAt945 = Schedule.toMillis(at(9, 45), zone)
        val it = item(d, minute = 600, alerts = "0,15", lastFired = firedAt945)
        assertEquals(Schedule.toMillis(at(10, 0), zone), Schedule.nextAlertMillis(it, nine, zone))
        assertNull(Schedule.nextAlertMillis(it.copy(done = true), nine, zone))
    }

    @Test
    fun legacyItemsUseReminderDays() {
        val legacy = item(d, alerts = "").copy(reminderDays = "7,0")
        assertEquals(listOf(at(9, 0, d.minusDays(7)), at(9, 0)), Schedule.alertTimes(legacy, nine))
    }

    @Test
    fun rollForwardMovesFinishedAutoRepeats() {
        val it = item(d, minute = 600, cycle = Cycle.MINUTES, every = 30)
        assertSame(it, Schedule.rollForward(it, at(9, 59), nine, zone))
        val rolled = Schedule.rollForward(it, at(10, 1), nine, zone)
        assertEquals(10 * 60 + 30, rolled.dueMinute)
        // Payments without auto-renew wait to be marked as paid.
        val bill = item(d, cycle = Cycle.MONTHLY, autoRenew = false, kind = Kind.PAYMENT)
        assertSame(bill, Schedule.rollForward(bill, at(12, 0, d.plusDays(3)), nine, zone))
    }

    @Test
    fun lastDayAndLastWorkingDay() {
        val lastDay = item(LocalDate.of(2026, 10, 31), cycle = Cycle.MONTHLY).copy(repeatRule = Schedule.RULE_LAST_DAY)
        assertEquals(LocalDate.of(2026, 11, 30).atTime(9, 0), Schedule.nextOccurrence(lastDay, LocalDate.of(2026, 10, 31).atTime(9, 0), nine))
        // Feb 2027 ends on a Sunday -> last working day is Friday 26 Feb.
        val work = item(LocalDate.of(2027, 1, 29), cycle = Cycle.MONTHLY).copy(repeatRule = Schedule.RULE_LAST_WORKDAY)
        assertEquals(LocalDate.of(2027, 2, 26).atTime(9, 0), Schedule.nextOccurrence(work, LocalDate.of(2027, 1, 29).atTime(9, 0), nine))
    }

    @Test
    fun secondTuesdayAndLastFriday() {
        val second = item(LocalDate.of(2026, 10, 13), cycle = Cycle.MONTHLY).copy(repeatRule = Schedule.nthRule(2, 2))
        assertEquals(LocalDate.of(2026, 11, 10).atTime(9, 0), Schedule.nextOccurrence(second, LocalDate.of(2026, 10, 13).atTime(9, 0), nine))
        val lastFri = item(LocalDate.of(2026, 10, 30), cycle = Cycle.MONTHLY).copy(repeatRule = Schedule.nthRule(5, 5))
        assertEquals(LocalDate.of(2026, 11, 27).atTime(9, 0), Schedule.nextOccurrence(lastFri, LocalDate.of(2026, 10, 30).atTime(9, 0), nine))
    }

    @Test
    fun dailySkippingWeekends() {
        // Friday 23 Oct -> Monday 26 Oct
        val it = item(LocalDate.of(2026, 10, 23), minute = 540, cycle = Cycle.DAILY).copy(repeatRule = Schedule.RULE_SKIP_WEEKENDS)
        assertEquals(LocalDate.of(2026, 10, 26).atTime(9, 0), Schedule.nextOccurrence(it, LocalDate.of(2026, 10, 23).atTime(9, 0), nine))
    }

    @Test
    fun quietHoursWrapPastMidnight() {
        assertTrue(Schedule.inQuietHours(23 * 60, 22 * 60, 7 * 60))
        assertTrue(Schedule.inQuietHours(6 * 60, 22 * 60, 7 * 60))
        assertFalse(Schedule.inQuietHours(12 * 60, 22 * 60, 7 * 60))
        assertTrue(Schedule.inQuietHours(13 * 60, 12 * 60, 14 * 60))
        assertEquals(at(7, 0, d.plusDays(1)), Schedule.quietEnd(at(23, 0), 7 * 60))
        assertEquals(at(7, 0), Schedule.quietEnd(at(2, 0), 7 * 60))
    }

    @Test
    fun streakCountsConsecutiveDays() {
        assertEquals(3, Schedule.streak(listOf(d, d.minusDays(1), d.minusDays(2), d.minusDays(5)), d))
        assertEquals(2, Schedule.streak(listOf(d.minusDays(1), d.minusDays(2)), d))
        assertEquals(0, Schedule.streak(listOf(d.minusDays(3)), d))
    }

    @Test
    fun actualAmountIsRecorded() {
        val bill = item(d, cycle = Cycle.MONTHLY, kind = Kind.PAYMENT, amount = 35.0)
        assertEquals(41.5, Schedule.complete(bill, 1000L, nine, actualAmount = 41.5).history.amount!!, 0.0)
    }

    @Test
    fun completingOneOffMarksDone() {
        val c = Schedule.complete(item(d), 1000L, nine)
        assertTrue(c.updated.done)
        assertNull(c.next)
    }

    @Test
    fun completingRepeatingMovesOnAndRecordsPayment() {
        val bill = item(d, cycle = Cycle.MONTHLY, kind = Kind.PAYMENT, amount = 35.0, autoRenew = false)
        val c = Schedule.complete(bill, 1000L, nine)
        assertFalse(c.updated.done)
        assertEquals(d.plusMonths(1).toEpochDay(), c.updated.dueEpochDay)
        assertEquals(35.0, c.history.amount!!, 0.0)
        assertEquals("PAYMENT", c.history.kind)
        // Reminders record completion without an amount.
        assertNull(Schedule.complete(item(d, amount = 5.0), 1000L, nine).history.amount)
    }
}
