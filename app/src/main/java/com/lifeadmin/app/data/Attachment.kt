package com.lifeadmin.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** A file or photo kept with an item. The file itself lives in the app's private storage. */
@Entity(tableName = "attachments")
data class Attachment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    /** Name shown to the person, e.g. "policy.pdf". */
    val name: String,
    val mimeType: String,
    /** File name inside the app's attachments folder. */
    val fileName: String,
    val addedAt: Long = System.currentTimeMillis(),
)

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments WHERE itemId = :itemId ORDER BY addedAt DESC")
    fun observeForItem(itemId: Long): Flow<List<Attachment>>

    @Query("SELECT * FROM attachments")
    suspend fun getAll(): List<Attachment>

    @Query("SELECT * FROM attachments WHERE itemId = :itemId")
    suspend fun getForItem(itemId: Long): List<Attachment>

    @Insert
    suspend fun insert(attachment: Attachment): Long

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun deleteById(id: Long)
}
