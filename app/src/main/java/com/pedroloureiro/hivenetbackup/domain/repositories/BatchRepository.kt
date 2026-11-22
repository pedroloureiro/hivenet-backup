package com.pedroloureiro.hivenetbackup.domain.repositories

import com.pedroloureiro.hivenetbackup.domain.models.Batch
import com.pedroloureiro.hivenetbackup.domain.models.Outcome
import com.pedroloureiro.hivenetbackup.domain.models.SourceFile
import kotlinx.coroutines.flow.Flow

interface BatchRepository {
  suspend fun create(sourceFiles: List<SourceFile>): Batch

  suspend fun markCopying(batchFileId: Long)

  suspend fun recordOutcome(batchFileId: Long, outcome: Outcome)

  suspend fun settleAbandoned()

  fun observeLatest(): Flow<Batch?>
}
