package com.example.emergencywatch.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TestEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: TestEventEntity)

    @Query("SELECT * FROM test_event_timeline ORDER BY epoch_ms DESC LIMIT 50")
    fun observeRecentEvents(): Flow<List<TestEventEntity>>

    @Query("SELECT * FROM test_event_timeline ORDER BY epoch_ms DESC LIMIT 50")
    suspend fun getRecentEvents(): List<TestEventEntity>

    @Query("DELETE FROM test_event_timeline")
    suspend fun clearAll()
}
