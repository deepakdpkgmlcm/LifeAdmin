package com.lifeadmin.app.data

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Backups must round-trip exactly, or restoring would silently lose data. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BackupTest {

    @Test
    fun roundTripKeepsEverything() {
        val items = listOf(
            Item(
                id = 7, title = "Netflix", category = Category.SUBSCRIPTION, dueEpochDay = 20750,
                cycle = Cycle.MONTHLY, autoRenew = true, amount = 15.49, currency = "USD",
                notes = "Family plan", reminderDays = "7,1", createdAt = 123L
            ),
            Item(id = 9, title = "Passport", category = Category.DOCUMENT, dueEpochDay = 20941, createdAt = 456L),
            Item(
                id = 11, title = "Stand up", category = Category.HEALTH, dueEpochDay = 20750, createdAt = 789L,
                dueMinute = 615, cycle = Cycle.WEEKLY, repeatEvery = 2, repeatDays = "1,3,5", repeatLimit = 4,
                repeatUntilEpochDay = 21000, alertOffsets = "0,10", alertStyle = AlertStyle.ALARM,
                nagEveryMin = 5, nagCount = 3, done = true, doneAt = 5L
            ),
        )
        val history = listOf(
            History(itemId = 7, itemTitle = "Netflix", category = Category.SUBSCRIPTION,
                dueEpochDay = 20720, paidAt = 999L, amount = 15.49, currency = "USD")
        )

        val parsed = Backup.fromJson(Backup.toJson(items, history))

        assertEquals(items, parsed.items)
        assertEquals(history, parsed.history)
        assertNull(parsed.items[1].amount)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOtherFiles() {
        Backup.fromJson("{\"hello\": \"world\"}")
    }
}
