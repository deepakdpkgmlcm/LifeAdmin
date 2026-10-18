package com.lifeadmin.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Item::class, History::class], version = 5, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
    abstract fun historyDao(): HistoryDao

    companion object {
        const val NAME = "lifeadmin.db"

        /** v1 -> v2: adds the payment history table. Keeps all existing items. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `history` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`itemId` INTEGER NOT NULL, " +
                        "`itemTitle` TEXT NOT NULL, " +
                        "`category` TEXT NOT NULL, " +
                        "`dueEpochDay` INTEGER NOT NULL, " +
                        "`paidAt` INTEGER NOT NULL, " +
                        "`amount` REAL, " +
                        "`currency` TEXT NOT NULL)"
                )
            }
        }

        /** v2 -> v3: reminder features (kinds, times, flexible repeats, alarms, done state). */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val columns = listOf(
                    "`kind` TEXT NOT NULL DEFAULT 'REMINDER'",
                    "`dueMinute` INTEGER",
                    "`repeatEvery` INTEGER NOT NULL DEFAULT 1",
                    "`repeatDays` TEXT NOT NULL DEFAULT ''",
                    "`repeatLimit` INTEGER",
                    "`repeatUntilEpochDay` INTEGER",
                    "`alertOffsets` TEXT NOT NULL DEFAULT ''",
                    "`alertStyle` TEXT NOT NULL DEFAULT 'NOTIFICATION'",
                    "`nagEveryMin` INTEGER NOT NULL DEFAULT 0",
                    "`nagCount` INTEGER NOT NULL DEFAULT 0",
                    "`lastFiredAt` INTEGER NOT NULL DEFAULT 0",
                    "`done` INTEGER NOT NULL DEFAULT 0",
                    "`doneAt` INTEGER",
                    "`snoozedUntil` INTEGER",
                )
                columns.forEach { db.execSQL("ALTER TABLE `items` ADD COLUMN $it") }
                db.execSQL(
                    "UPDATE `items` SET `kind` = CASE " +
                        "WHEN `category` IN ('SUBSCRIPTION','BILL','INSURANCE') THEN 'PAYMENT' " +
                        "WHEN `category` IN ('DOCUMENT','VEHICLE','WARRANTY') THEN 'RENEWAL' " +
                        "ELSE 'REMINDER' END"
                )
                // Existing items count as "already alerted up to now", so the upgrade
                // doesn't fire a burst of old reminders.
                db.execSQL("UPDATE `items` SET `lastFiredAt` = CAST(strftime('%s','now') AS INTEGER) * 1000")
            }
        }

        /** v3 -> v4: reference number and payment method. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `items` ADD COLUMN `reference` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `items` ADD COLUMN `paymentMethod` TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v4 -> v5: history remembers the item's kind (for the Done tab). */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `history` ADD COLUMN `kind` TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "UPDATE `history` SET `kind` = " +
                        "(SELECT `kind` FROM `items` WHERE `items`.`id` = `history`.`itemId`) " +
                        "WHERE EXISTS (SELECT 1 FROM `items` WHERE `items`.`id` = `history`.`itemId`)"
                )
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context).also { instance = it }
            }

        fun build(context: Context, name: String = NAME): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
    }
}
