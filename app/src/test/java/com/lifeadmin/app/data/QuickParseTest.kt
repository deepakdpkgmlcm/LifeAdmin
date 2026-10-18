package com.lifeadmin.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class QuickParseTest {
    // Sunday 18 Oct 2026, 10:00
    private val now = LocalDateTime.of(2026, 10, 18, 10, 0)
    private fun p(s: String) = QuickParse.parse(s, now)

    @Test
    fun rentEveryMonth() {
        val r = p("Pay rent 5th every month 10am")
        assertEquals("Pay rent", r.title)
        assertEquals(Kind.PAYMENT, r.kind)
        assertEquals(Category.BILL, r.category)
        assertEquals(LocalDate.of(2026, 11, 5), r.date)
        assertEquals(600, r.minute)
        assertEquals(Cycle.MONTHLY, r.cycle)
    }

    @Test
    fun callMomSundayEvening() {
        val r = p("Call mom Sunday 7pm")
        assertEquals("Call mom", r.title)
        assertEquals(Kind.REMINDER, r.kind)
        assertEquals(Category.PERSONAL, r.category)
        assertEquals(LocalDate.of(2026, 10, 18), r.date)
        assertEquals(19 * 60, r.minute)
    }

    @Test
    fun waterEveryTwoHours() {
        val r = p("Drink water every 2 hours")
        assertEquals("Drink water", r.title)
        assertEquals(Cycle.HOURS, r.cycle)
        assertEquals(2, r.every)
        assertEquals(11 * 60, r.minute)
        assertEquals(Category.HEALTH, r.category)
    }

    @Test
    fun passportExpiry() {
        val r = p("Passport expires 12 Mar 2027")
        assertEquals("Passport", r.title)
        assertEquals(Kind.RENEWAL, r.kind)
        assertEquals(Category.DOCUMENT, r.category)
        assertEquals(LocalDate.of(2027, 3, 12), r.date)
        assertNull(r.minute)
    }

    @Test
    fun netflixWithAmount() {
        val r = p("Netflix ₹649 monthly on 3rd")
        assertEquals("Netflix", r.title)
        assertEquals(Kind.PAYMENT, r.kind)
        assertEquals(Category.SUBSCRIPTION, r.category)
        assertEquals(649.0, r.amount!!, 0.0)
        assertEquals("INR", r.currency)
        assertEquals(Cycle.MONTHLY, r.cycle)
        assertEquals(LocalDate.of(2026, 11, 3), r.date)
    }

    @Test
    fun standupWeekdaysAlarm() {
        val r = p("Standup weekdays 10:30 alarm")
        assertEquals("Standup", r.title)
        assertEquals(Cycle.WEEKLY, r.cycle)
        assertEquals("1,2,3,4,5", r.days)
        assertEquals(630, r.minute)
        assertTrue(r.alarm)
        assertEquals(LocalDate.of(2026, 10, 19), r.date)
    }

    @Test
    fun tomorrowAtNine() {
        val r = p("Remind me to take medicine tomorrow at 9")
        assertEquals("Take medicine", r.title)
        assertEquals(LocalDate.of(2026, 10, 19), r.date)
        assertEquals(9 * 60, r.minute)
    }

    @Test
    fun inThirtyMinutes() {
        val r = p("check the oven in 30 minutes")
        assertEquals("Check the oven", r.title)
        assertEquals(LocalDate.of(2026, 10, 18), r.date)
        assertEquals(10 * 60 + 30, r.minute)
    }

    @Test
    fun timeAlreadyPassedMovesToTomorrow() {
        val r = p("Stretch 8am")
        assertEquals(LocalDate.of(2026, 10, 19), r.date)
        assertEquals(8 * 60, r.minute)
    }
}
