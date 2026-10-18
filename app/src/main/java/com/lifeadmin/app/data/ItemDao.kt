package com.lifeadmin.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Query("SELECT * FROM items ORDER BY dueEpochDay ASC, title ASC")
    fun observeAll(): Flow<List<Item>>

    @Query("SELECT * FROM items ORDER BY dueEpochDay ASC")
    suspend fun getAll(): List<Item>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun get(id: Long): Item?

    @Insert
    suspend fun insert(item: Item): Long

    @Update
    suspend fun update(item: Item)

    @Delete
    suspend fun delete(item: Item)
}
