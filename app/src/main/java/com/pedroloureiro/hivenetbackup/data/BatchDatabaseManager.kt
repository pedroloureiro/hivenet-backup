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
    check(outcome.state != State.REMOVED) { "Only a Backed up file can become Removed" }
    database.withTransaction {
      val state = dao.stateOf(outcome.id)
      check(state == State.COPYING) { "Batch file ${outcome.id} is $state, so it can't take an Outcome" }
      dao.update(outcome)
    }
  }

  // Clears the copy's fields: a Removed file keeps nothing.
  suspend fun markRemoved(recordId: Long) {
    database.withTransaction {
      val state = dao.stateOf(recordId)
      check(state == State.BACKED_UP) { "Batch file $recordId is $state, so it can't become Removed" }
      dao.update(BatchFileOutcome(id = recordId, state = State.REMOVED))
    }
  }

  suspend fun settleAbandoned(): List<BatchFileRecord> = database.withTransaction {
    val interrupted = dao.recordsIn(State.COPYING).map { it.copy(state = State.INTERRUPTED) }
    dao.replaceState(from = State.WAITING, to = State.NOT_STARTED)
    dao.replaceState(from = State.COPYING, to = State.INTERRUPTED)
    interrupted
  }

  suspend fun removedRecords(): List<BatchFileRecord> = dao.recordsIn(State.REMOVED)

  fun observeLatest(): Flow<List<BatchFileRecord>> = dao.observeLatestBatch()

  fun observeBackedUp(): Flow<List<BatchFileRecord>> = dao.observeRecordsIn(State.BACKED_UP)

  private companion object {
    val progressStates = setOf(State.WAITING, State.COPYING)
  }
}
