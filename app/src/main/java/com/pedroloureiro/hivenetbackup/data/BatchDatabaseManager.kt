package com.pedroloureiro.hivenetbackup.data

import androidx.room.withTransaction
import com.pedroloureiro.hivenetbackup.data.entities.BatchEntity
import com.pedroloureiro.hivenetbackup.data.entities.BatchFileEntity
import com.pedroloureiro.hivenetbackup.data.entities.BatchFileEntity.State
import com.pedroloureiro.hivenetbackup.data.entities.BatchFileOutcome
import com.pedroloureiro.hivenetbackup.data.entities.BatchWithFiles
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BatchDatabaseManager(private val database: BackupDatabase) {
  private val dao = database.backupDao()

  suspend fun createBatch(createdAt: Long, files: List<BatchFileEntity>): BatchWithFiles =
    database.withTransaction {
      val batchId = dao.insert(BatchEntity(createdAt = createdAt))
      dao.insert(
        files.mapIndexed { position, file ->
          file.copy(batchId = batchId, position = position, state = State.WAITING)
        },
      )
      dao.batchWithFiles(batchId).inPickedOrder()
    }

  suspend fun markCopying(batchFileId: Long) {
    database.withTransaction {
      val state = dao.stateOf(batchFileId)
      check(state == State.WAITING) { "Batch file $batchFileId is $state, so it can't start copying" }
      dao.setState(batchFileId, State.COPYING)
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

  suspend fun settleAbandoned() {
    database.withTransaction {
      dao.replaceState(from = State.WAITING, to = State.NOT_STARTED)
      dao.replaceState(from = State.COPYING, to = State.INTERRUPTED)
    }
  }

  // Room's @Relation gives no order guarantee.
  fun observeLatest(): Flow<BatchWithFiles?> = dao.observeLatest().map { it?.inPickedOrder() }

  private fun BatchWithFiles.inPickedOrder() = copy(files = files.sortedBy { it.position })

  private companion object {
    val progressStates = setOf(State.WAITING, State.COPYING)
  }
}
