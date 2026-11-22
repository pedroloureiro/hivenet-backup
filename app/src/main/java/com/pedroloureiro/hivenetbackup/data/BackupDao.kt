package com.pedroloureiro.hivenetbackup.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.pedroloureiro.hivenetbackup.data.BatchFileRecord.State
import kotlinx.coroutines.flow.Flow

@Dao
interface BackupDao {
  @Insert
  suspend fun insert(records: List<BatchFileRecord>)

  @Query("SELECT MAX(batchId) FROM batch_file_records")
  suspend fun latestBatchId(): Long?

  @Query("SELECT * FROM batch_file_records WHERE batchId = :batchId ORDER BY position")
  suspend fun batch(batchId: Long): List<BatchFileRecord>

  @Query("SELECT * FROM batch_file_records WHERE state = :state")
  suspend fun recordsIn(state: State): List<BatchFileRecord>

  @Query("SELECT state FROM batch_file_records WHERE id = :recordId")
  suspend fun stateOf(recordId: Long): State?

  @Query("UPDATE batch_file_records SET state = :state WHERE id = :recordId")
  suspend fun setState(recordId: Long, state: State)

  @Update(entity = BatchFileRecord::class)
  suspend fun update(outcome: BatchFileOutcome)

  @Query("UPDATE batch_file_records SET state = :to WHERE state = :from")
  suspend fun replaceState(from: State, to: State)

  @Query(
    "SELECT * FROM batch_file_records " +
      "WHERE batchId = (SELECT MAX(batchId) FROM batch_file_records) ORDER BY position",
  )
  fun observeLatestBatch(): Flow<List<BatchFileRecord>>
}
