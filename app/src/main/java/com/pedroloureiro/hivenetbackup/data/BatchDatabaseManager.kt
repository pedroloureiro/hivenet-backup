package com.pedroloureiro.hivenetbackup.data

import androidx.room.withTransaction
import com.pedroloureiro.hivenetbackup.data.BatchFileRecord.State
import kotlinx.coroutines.flow.Flow

class BatchDatabaseManager(private val database: BackupDatabase) {
  private val dao = database.backupDao()

  suspend fun createBatch(createdAtEpochMs: Long, records: List<BatchFileRecord>): List<BatchFileRecord> {
    require(records.isNotEmpty()) { "A Batch needs at least one file" }
    return database.withTransaction {
      val batchId = (dao.latestBatchId() ?: 0) + 1
      dao.insert(
        records.mapIndexed { position, record ->
          record.copy(
            batchId = batchId,
            batchCreatedAtEpochMs = createdAtEpochMs,
            position = position,
            state = State.WAITING,
          )
        },
      )
      dao.batch(batchId)
    }
  }

  suspend fun markCopying(recordId: Long) {
    database.withTransaction {
      val state = dao.stateOf(recordId)
      check(state == State.WAITING) { "Batch file $recordId is $state, so it can't start copying" }
      dao.setState(recordId, State.COPYING)
    }
  }

  suspend fun recordOutcome(outcome: BatchFileOutcome) {
    check(outcome.state !in progressStates) { "${outcome.state} is not an Outcome" }
    database.withTransaction {
      val state = dao.stateOf(outcome.id)
      check(state == State.COPYING) { "Batch file ${outcome.id} is $state, so it can't take an Outcome" }
      dao.update(outcome)
    }
  }

  suspend fun settleAbandoned(): List<BatchFileRecord> = database.withTransaction {
    val interrupted = dao.recordsIn(State.COPYING).map { it.copy(state = State.INTERRUPTED) }
    dao.replaceState(from = State.WAITING, to = State.NOT_STARTED)
    dao.replaceState(from = State.COPYING, to = State.INTERRUPTED)
    interrupted
  }

  fun observeLatest(): Flow<List<BatchFileRecord>> = dao.observeLatestBatch()

  private companion object {
    val progressStates = setOf(State.WAITING, State.COPYING)
  }
}
