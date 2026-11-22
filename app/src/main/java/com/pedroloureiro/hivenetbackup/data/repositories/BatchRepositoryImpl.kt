package com.pedroloureiro.hivenetbackup.data.repositories

import com.pedroloureiro.hivenetbackup.data.BatchDatabaseManager
import com.pedroloureiro.hivenetbackup.data.mappers.toBatch
import com.pedroloureiro.hivenetbackup.data.mappers.toBatchFileOutcome
import com.pedroloureiro.hivenetbackup.data.mappers.toBatchFile
import com.pedroloureiro.hivenetbackup.data.mappers.toBatchFileRecord
import com.pedroloureiro.hivenetbackup.domain.models.Batch
import com.pedroloureiro.hivenetbackup.domain.models.BatchFile
import com.pedroloureiro.hivenetbackup.domain.models.FileState
import com.pedroloureiro.hivenetbackup.domain.models.SourceFile
import com.pedroloureiro.hivenetbackup.domain.repositories.BatchRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BatchRepositoryImpl(private val batchDatabaseManager: BatchDatabaseManager) : BatchRepository {
  override suspend fun create(sourceFiles: List<SourceFile>): Batch =
    batchDatabaseManager.createBatch(
      createdAtEpochMs = System.currentTimeMillis(),
      records = sourceFiles.map { it.toBatchFileRecord() },
    ).toBatch()

  override suspend fun markCopying(batchFileId: Long) {
    batchDatabaseManager.markCopying(batchFileId)
  }

  override suspend fun recordOutcome(batchFileId: Long, fileState: FileState) {
    batchDatabaseManager.recordOutcome(fileState.toBatchFileOutcome(batchFileId))
  }

  override suspend fun markRemoved(batchFileId: Long) {
    batchDatabaseManager.markRemoved(batchFileId)
  }

  override suspend fun settleAbandoned(): List<BatchFile> =
    batchDatabaseManager.settleAbandoned().map { it.toBatchFile() }

  override suspend fun removedFiles(): List<BatchFile> =
    batchDatabaseManager.removedRecords().map { it.toBatchFile() }

  override fun observeLatest(): Flow<Batch?> =
    batchDatabaseManager.observeLatest().map { records -> records.ifEmpty { null }?.toBatch() }

  override fun observeBackedUp(): Flow<List<BatchFile>> =
    batchDatabaseManager.observeBackedUp().map { records -> records.map { it.toBatchFile() } }
}
