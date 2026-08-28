package com.liana.countdown.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface OccasionDao {

    /**
     * Ordering is deliberately left to the caller: a yearly occasion's next occurrence is a
     * function of today's date, which SQLite cannot compute. See [com.liana.countdown.domain.Countdown.sortKey].
     */
    @Query("SELECT * FROM occasions WHERE is_deleted = 0")
    fun observeAll(): Flow<List<Occasion>>

    @Query("SELECT * FROM occasions WHERE id = :id")
    suspend fun getById(id: Long): Occasion?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(occasion: Occasion): Long

    @Update
    suspend fun update(occasion: Occasion)

    @Query("UPDATE occasions SET is_deleted = 1 WHERE id = :id")
    suspend fun softDelete(id: Long)
}
