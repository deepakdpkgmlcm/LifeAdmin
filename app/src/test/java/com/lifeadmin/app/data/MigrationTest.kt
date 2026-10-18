package com.lifeadmin.app.data

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Builds a version-1 database exactly as v0.1 of the app created it, then opens it with
 * the current code. Room refuses to open it if the migration doesn't match the schema,
 * so this catches upgrade crashes before they reach a phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MigrationTest {

    @Test
    fun upgradeFromV1KeepsItemsAndAddsHistory() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-test.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()

        val v1 = SQLiteDatabase.openOrCreateDatabase(file, null)
        v1.execSQL(
            "CREATE TABLE IF NOT EXISTS `items` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, " +
                "`category` TEXT NOT NULL, `dueEpochDay` INTEGER NOT NULL, `cycle` TEXT NOT NULL, " +
                "`autoRenew` INTEGER NOT NULL, `amount` REAL, `currency` TEXT NOT NULL, " +
                "`notes` TEXT NOT NULL, `reminderDays` TEXT NOT NULL, " +
                "`lastNotifiedStage` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)"
        )
        v1.execSQL(
            "INSERT INTO items (title, category, dueEpochDay, cycle, autoRenew, amount, currency, " +
                "notes, reminderDays, lastNotifiedStage, createdAt) VALUES " +
                "('Passport', 'DOCUMENT', 20000, 'NONE', 0, NULL, 'USD', '', '30,7,1,0', 2147483647, 0)"
        )
        v1.version = 1
        v1.close()

        val db = AppDatabase.build(context, name)
        val items = db.itemDao().getAll()
        assertEquals(1, items.size)
        assertEquals("Passport", items[0].title)
        assertEquals(Category.DOCUMENT, items[0].category)

        db.historyDao().insert(
            History(
                itemId = items[0].id,
                itemTitle = "Passport",
                category = Category.DOCUMENT,
                dueEpochDay = 20000
            )
        )
        assertEquals(1, db.historyDao().getAll().size)
        db.close()
    }
}
