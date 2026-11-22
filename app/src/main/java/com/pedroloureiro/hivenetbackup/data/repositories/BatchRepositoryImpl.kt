package com.pedroloureiro.hivenetbackup.data.repositories

import com.pedroloureiro.hivenetbackup.data.BatchDatabaseManager
import com.pedroloureiro.hivenetbackup.data.mappers.toBatch
import com.pedroloureiro.hivenetbackup.data.mappers.toBatchFileOutcome
import com.pedroloureiro.hivenetbackup.data.mappers.toBatchFileRecord
import com.pedroloureiro.hivenetbackup.domain.models.Batch
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

  override suspend fun settleAbandoned() {
    batchDatabaseManager.settleAbandoned()
  }

  override fun observeLatest(): Flow<Batch?> =
    batchDatabaseManager.observeLatest().map { records -> records.ifEmpty { null }?.toBatch() }
}
