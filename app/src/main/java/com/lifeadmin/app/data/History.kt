package com.lifeadmin.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** One "paid / renewed" event, kept so the person can see what they paid and when. */
@Entity(tableName = "history")
data class History(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    /** Copied so history still reads well after the item is renamed or deleted. */
    val itemTitle: String,
    val category: Category,
    /** The due date that was paid. */
    val dueEpochDay: Long,
    /** When it was marked as paid (epoch millis). */
    val paidAt: Long = System.currentTimeMillis(),
    val amount: Double? = null,
    val currency: String = "",
)

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY paidAt DESC")
    fun observeAll(): Flow<List<History>>

    @Query("SELECT * FROM history WHERE itemId = :itemId ORDER BY dueEpochDay DESC")
    fun observeForItem(itemId: Long): Flow<List<History>>

    @Query("SELECT * FROM history ORDER BY paidAt DESC")
    suspend fun getAll(): List<History>

    @Insert
    suspend fun insert(entry: History): Long

    @Insert
    suspend fun insertAll(entries: List<History>)

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM history")
    suspend fun deleteAll()
}
