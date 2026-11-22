package com.pedroloureiro.hivenetbackup.domain.repositories

import com.pedroloureiro.hivenetbackup.domain.models.Batch
import com.pedroloureiro.hivenetbackup.domain.models.Outcome
import com.pedroloureiro.hivenetbackup.domain.models.SourceFile
import kotlinx.coroutines.flow.Flow

interface BatchRepository {
  /** Records the Batch and every file as Waiting, in the given order, in one transaction. */
  suspend fun create(sourceFiles: List<SourceFile>): Batch

  suspend fun markCopying(batchFileId: Long)

  /** Rejected if the file already has an Outcome: a final Outcome never changes. */
  suspend fun recordOutcome(batchFileId: Long, outcome: Outcome)

  /** Waiting becomes Not started, Copying becomes Interrupted. */
  suspend fun settleAbandoned()

  fun observeLatest(): Flow<Batch?>
}
