package com.lifeadmin.app.util

import com.lifeadmin.app.data.Category
import com.lifeadmin.app.data.Cycle
import com.lifeadmin.app.data.Item
import com.lifeadmin.app.reminders.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ExportAndMoneyTest {
    private fun item(cycle: Cycle, every: Int = 1, days: String = "", rule: String = "", limit: Int? = null, until: LocalDate? = null) =
        Item(
            title = "T", category = Category.TASK, dueEpochDay = LocalDate.of(2026, 10, 18).toEpochDay(),
            cycle = cycle, repeatEvery = every, repeatDays = days, repeatRule = rule,
            repeatLimit = limit, repeatUntilEpochDay = until?.toEpochDay()
        )

    @Test
    fun rrules() {
        assertNull(CalendarExport.rrule(item(Cycle.NONE)))
        assertEquals("FREQ=HOURLY;INTERVAL=2", CalendarExport.rrule(item(Cycle.HOURS, 2)))
        assertEquals("FREQ=WEEKLY;BYDAY=MO,WE,FR", CalendarExport.rrule(item(Cycle.WEEKLY, days = "5,1,3")))
        assertEquals("FREQ=MONTHLY;BYMONTHDAY=-1", CalendarExport.rrule(item(Cycle.MONTHLY, rule = Schedule.RULE_LAST_DAY)))
        assertEquals("FREQ=MONTHLY;BYDAY=2TU", CalendarExport.rrule(item(Cycle.MONTHLY, rule = Schedule.nthRule(2, 2))))
        assertEquals("FREQ=MONTHLY;BYDAY=-1FR", CalendarExport.rrule(item(Cycle.MONTHLY, rule = Schedule.nthRule(5, 5))))
        assertEquals("FREQ=MONTHLY;INTERVAL=3", CalendarExport.rrule(item(Cycle.QUARTERLY)))
        assertEquals("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR", CalendarExport.rrule(item(Cycle.DAILY, rule = Schedule.RULE_SKIP_WEEKENDS)))
        assertEquals("FREQ=DAILY;COUNT=5", CalendarExport.rrule(item(Cycle.DAILY, limit = 4)))
        assertEquals("FREQ=YEARLY;UNTIL=20300101", CalendarExport.rrule(item(Cycle.YEARLY, until = LocalDate.of(2030, 1, 1))))
    }

    @Test
    fun money() {
        val rates = Money.parseRates("usd=83.5, EUR=90,bad,XX=2")
        assertEquals(mapOf("USD" to 83.5, "EUR" to 90.0), rates)
        assertEquals(835.0, Money.convert(10.0, "USD", "INR", rates)!!, 0.001)
        assertEquals(10.0, Money.convert(10.0, "inr", "INR", rates)!!, 0.0)
        assertNull(Money.convert(10.0, "GBP", "INR", rates))
        assertEquals("EUR=90.0,USD=83.5", Money.ratesToString(rates))
    }
}
