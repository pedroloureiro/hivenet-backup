package com.pedroloureiro.hivenetbackup.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.pedroloureiro.hivenetbackup.data.entities.BatchEntity
import com.pedroloureiro.hivenetbackup.data.entities.BatchFileEntity
import com.pedroloureiro.hivenetbackup.data.entities.BatchFileEntity.State
import com.pedroloureiro.hivenetbackup.data.entities.BatchFileOutcome
import com.pedroloureiro.hivenetbackup.data.entities.BatchWithFiles
import kotlinx.coroutines.flow.Flow

@Dao
interface BackupDao {
  @Insert
  suspend fun insert(batch: BatchEntity): Long

  @Insert
  suspend fun insert(files: List<BatchFileEntity>)

  @Query("SELECT state FROM batch_files WHERE id = :batchFileId")
  suspend fun stateOf(batchFileId: Long): State?

  @Query("UPDATE batch_files SET state = :state WHERE id = :batchFileId")
  suspend fun setState(batchFileId: Long, state: State)

  @Update(entity = BatchFileEntity::class)
  suspend fun update(outcome: BatchFileOutcome)

  @Query("UPDATE batch_files SET state = :to WHERE state = :from")
  suspend fun replaceState(from: State, to: State)

  @Transaction
  @Query("SELECT * FROM batches WHERE id = :batchId")
  suspend fun batchWithFiles(batchId: Long): BatchWithFiles

  @Transaction
  @Query("SELECT * FROM batches ORDER BY id DESC LIMIT 1")
  fun observeLatest(): Flow<BatchWithFiles?>
}
