package com.lifeadmin.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lifeadmin.app.data.AppDatabase
import com.lifeadmin.app.data.AppSettings
import com.lifeadmin.app.data.Category
import com.lifeadmin.app.data.Cycle
import com.lifeadmin.app.data.Item
import com.lifeadmin.app.data.dueDate
import com.lifeadmin.app.reminders.Notifier
import com.lifeadmin.app.reminders.ReminderScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** What the edit form produces. */
data class Draft(
    val title: String,
    val category: Category,
    val dueEpochDay: Long,
    val cycle: Cycle,
    val autoRenew: Boolean,
    val amount: Double?,
    val currency: String,
    val notes: String,
    val reminderDays: String,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).itemDao()
    val settings = AppSettings(app)

    /** null while the first load is in progress. */
    val items: StateFlow<List<Item>?> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    suspend fun get(id: Long): Item? = dao.get(id)

    fun save(id: Long?, d: Draft) {
        viewModelScope.launch {
            val autoRenew = d.autoRenew && d.cycle != Cycle.NONE
            val old = id?.let { dao.get(it) }
            if (old == null) {
                dao.insert(
                    Item(
                        title = d.title.trim(),
                        category = d.category,
                        dueEpochDay = d.dueEpochDay,
                        cycle = d.cycle,
                        autoRenew = autoRenew,
                        amount = d.amount,
                        currency = d.currency,
                        notes = d.notes.trim(),
                        reminderDays = d.reminderDays,
                    )
                )
            } else {
                val scheduleChanged =
                    old.dueEpochDay != d.dueEpochDay || old.reminderDays != d.reminderDays
                dao.update(
                    old.copy(
                        title = d.title.trim(),
                        category = d.category,
                        dueEpochDay = d.dueEpochDay,
                        cycle = d.cycle,
                        autoRenew = autoRenew,
                        amount = d.amount,
                        currency = d.currency,
                        notes = d.notes.trim(),
                        reminderDays = d.reminderDays,
                        lastNotifiedStage =
                            if (scheduleChanged) Item.NOT_NOTIFIED else old.lastNotifiedStage,
                    )
                )
                if (scheduleChanged) Notifier.cancel(getApplication<Application>(), old.id.toInt())
            }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            dao.get(id)?.let {
                dao.delete(it)
                Notifier.cancel(getApplication<Application>(), it.id.toInt())
            }
        }
    }

    /** For repeating items: move to the next due date after the person has paid/renewed. */
    fun markRenewed(id: Long) {
        viewModelScope.launch {
            dao.get(id)?.let {
                val next = it.cycle.next(it.dueDate())
                dao.update(it.copy(dueEpochDay = next.toEpochDay(), lastNotifiedStage = Item.NOT_NOTIFIED))
                Notifier.cancel(getApplication<Application>(), it.id.toInt())
            }
        }
    }

    /** Swipe-to-delete from the home screen. Pair with [restore] for Undo. */
    fun deleteItem(item: Item) {
        viewModelScope.launch {
            dao.delete(item)
            Notifier.cancel(getApplication<Application>(), item.id.toInt())
        }
    }

    /**
     * Swipe-to-pay / "Mark as paid" from the home screen: moves a repeating item to its
     * next due date and returns that date. Pair with [restore] for Undo.
     */
    fun renewItem(item: Item): LocalDate {
        val next = item.cycle.next(item.dueDate())
        viewModelScope.launch {
            dao.update(item.copy(dueEpochDay = next.toEpochDay(), lastNotifiedStage = Item.NOT_NOTIFIED))
            Notifier.cancel(getApplication<Application>(), item.id.toInt())
        }
        return next
    }

    /** Puts an item back exactly as it was (Undo). */
    fun restore(item: Item) {
        viewModelScope.launch { dao.upsert(item) }
    }

    fun setReminderHour(hour: Int) {
        settings.reminderHour = hour
        ReminderScheduler.scheduleDaily(getApplication<Application>(), hour, reschedule = true)
    }
}
