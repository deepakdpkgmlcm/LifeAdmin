package com.lifeadmin.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.lifeadmin.app.data.AlertStyle
import com.lifeadmin.app.data.AppDatabase
import com.lifeadmin.app.data.AppSettings
import com.lifeadmin.app.data.Backup
import com.lifeadmin.app.data.Category
import com.lifeadmin.app.data.Cycle
import com.lifeadmin.app.data.History
import com.lifeadmin.app.data.Item
import com.lifeadmin.app.data.Kind
import com.lifeadmin.app.reminders.AlarmScheduler
import com.lifeadmin.app.reminders.ItemOps
import com.lifeadmin.app.reminders.Notifier
import com.lifeadmin.app.reminders.ReminderScheduler
import com.lifeadmin.app.reminders.Schedule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/** What the edit form produces. */
data class Draft(
    val title: String,
    val kind: Kind,
    val category: Category,
    val dueEpochDay: Long,
    val dueMinute: Int?,
    val cycle: Cycle,
    val repeatEvery: Int,
    val repeatDays: String,
    val repeatLimit: Int?,
    val repeatUntilEpochDay: Long?,
    val autoRenew: Boolean,
    val amount: Double?,
    val currency: String,
    val notes: String,
    val alertOffsets: String,
    val alertStyle: AlertStyle,
    val nagEveryMin: Int,
    val nagCount: Int,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val dao = db.itemDao()
    private val historyDao = db.historyDao()
    val settings = AppSettings(app)

    private val ctx: Application get() = getApplication()

    /** null while the first load is in progress. Includes completed items (check [Item.done]). */
    val items: StateFlow<List<Item>?> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val history: StateFlow<List<History>> = historyDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun observeItem(id: Long): Flow<Item?> = dao.observe(id)

    fun observeHistory(itemId: Long): Flow<List<History>> = historyDao.observeForItem(itemId)

    /** History rows created by the last completion of each item, so Undo can remove them. */
    private val lastCompletionHistory = mutableMapOf<Long, Long>()

    val defaultMinute: Int get() = settings.reminderHour * 60

    suspend fun get(id: Long): Item? = dao.get(id)

    fun save(id: Long?, d: Draft) {
        viewModelScope.launch {
            val repeating = d.cycle != Cycle.NONE
            val old = id?.let { dao.get(it) }
            val base = old ?: Item(title = d.title, category = d.category, dueEpochDay = d.dueEpochDay)
            val scheduleChanged = old == null ||
                old.dueEpochDay != d.dueEpochDay || old.dueMinute != d.dueMinute ||
                old.alertOffsets != d.alertOffsets || old.cycle != d.cycle ||
                old.repeatEvery != d.repeatEvery || old.repeatDays != d.repeatDays
            val item = base.copy(
                title = d.title.trim(),
                kind = d.kind,
                category = d.category,
                dueEpochDay = d.dueEpochDay,
                dueMinute = d.dueMinute,
                cycle = d.cycle,
                repeatEvery = d.repeatEvery.coerceAtLeast(1),
                repeatDays = if (d.cycle == Cycle.WEEKLY) d.repeatDays else "",
                repeatLimit = if (repeating) d.repeatLimit else null,
                repeatUntilEpochDay = if (repeating) d.repeatUntilEpochDay else null,
                autoRenew = repeating && d.autoRenew,
                amount = if (d.kind == Kind.REMINDER) null else d.amount,
                currency = d.currency,
                notes = d.notes.trim(),
                alertOffsets = d.alertOffsets,
                alertStyle = d.alertStyle,
                nagEveryMin = d.nagEveryMin,
                nagCount = if (d.nagEveryMin > 0) d.nagCount else 0,
                // Editing the time re-arms its alerts from now on.
                lastFiredAt = if (scheduleChanged) System.currentTimeMillis() - 1 else base.lastFiredAt,
                snoozedUntil = if (scheduleChanged) null else base.snoozedUntil,
                done = if (scheduleChanged) false else base.done,
                doneAt = if (scheduleChanged) null else base.doneAt,
            )
            val saved = if (old == null) item.copy(id = dao.insert(item)) else item.also { dao.update(it) }
            if (old != null && scheduleChanged) Notifier.cancel(ctx, old.id.toInt())
            AlarmScheduler.schedule(ctx, saved)
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            dao.get(id)?.let { deleteNow(it) }
        }
    }

    /** Swipe-to-delete. Pair with [restore] for Undo. */
    fun deleteItem(item: Item) {
        viewModelScope.launch { deleteNow(item) }
    }

    private suspend fun deleteNow(item: Item) {
        dao.delete(item)
        Notifier.cancel(ctx, item.id.toInt())
        AlarmScheduler.cancel(ctx, item.id)
    }

    /**
     * Done / Paid / Renewed. Returns the next occurrence for repeating items (null when the
     * item is now finished). Pair with [restore] for Undo.
     */
    fun complete(item: Item): LocalDateTime? {
        val next = Schedule.complete(item, System.currentTimeMillis(), defaultMinute).next
        viewModelScope.launch {
            val c = ItemOps.complete(ctx, item)
            lastCompletionHistory[item.id] = c.history.id
        }
        return next
    }

    fun completeById(id: Long) {
        viewModelScope.launch {
            dao.get(id)?.let { ItemOps.complete(ctx, it) }
        }
    }

    /** Brings back a completed one-off item. */
    fun reopen(item: Item) {
        viewModelScope.launch {
            val updated = item.copy(done = false, doneAt = null, lastFiredAt = System.currentTimeMillis())
            dao.update(updated)
            AlarmScheduler.schedule(ctx, updated)
        }
    }

    /** Puts an item back exactly as it was (Undo), removing a just-recorded completion. */
    fun restore(item: Item) {
        viewModelScope.launch {
            dao.upsert(item)
            lastCompletionHistory.remove(item.id)?.let { historyDao.deleteById(it) }
            AlarmScheduler.schedule(ctx, item)
        }
    }

    fun setReminderHour(hour: Int) {
        settings.reminderHour = hour
        ReminderScheduler.scheduleDaily(ctx, hour, reschedule = true)
        viewModelScope.launch { AlarmScheduler.rescheduleAll(ctx) }
    }

    fun rescheduleAll() {
        viewModelScope.launch { AlarmScheduler.rescheduleAll(ctx) }
    }

    suspend fun exportBackup(): String = Backup.toJson(dao.getAll(), historyDao.getAll())

    /**
     * Replaces everything with the backup's contents. Returns how many items were restored.
     * Throws IllegalArgumentException if the file isn't a valid backup.
     */
    suspend fun importBackup(text: String): Int {
        val parsed = Backup.fromJson(text)
        val now = System.currentTimeMillis()
        db.withTransaction {
            historyDao.deleteAll()
            dao.deleteAll()
            dao.insertAll(parsed.items.map { it.copy(lastNotifiedStage = Item.NOT_NOTIFIED, lastFiredAt = now) })
            historyDao.insertAll(parsed.history)
        }
        AlarmScheduler.rescheduleAll(ctx)
        return parsed.items.size
    }
}
