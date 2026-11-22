package com.pedroloureiro.hivenetbackup.domain.repositories

import com.pedroloureiro.hivenetbackup.domain.models.Batch
import com.pedroloureiro.hivenetbackup.domain.models.BatchFile
import com.pedroloureiro.hivenetbackup.domain.models.FileState
import com.pedroloureiro.hivenetbackup.domain.models.SourceFile
import kotlinx.coroutines.flow.Flow

interface BatchRepository {
  suspend fun create(sourceFiles: List<SourceFile>): Batch

  suspend fun markCopying(batchFileId: Long)

  suspend fun recordOutcome(batchFileId: Long, fileState: FileState)

  suspend fun markRemoved(batchFileId: Long)

  suspend fun settleAbandoned(): List<BatchFile>

  suspend fun removedFiles(): List<BatchFile>

  fun observeLatest(): Flow<Batch?>

  // Newest Batch first, picked order within one.
  fun observeBackedUp(): Flow<List<BatchFile>>
}
