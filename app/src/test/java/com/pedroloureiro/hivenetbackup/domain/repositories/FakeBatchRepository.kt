package com.pedroloureiro.hivenetbackup.domain.repositories

import com.pedroloureiro.hivenetbackup.domain.models.Batch
import com.pedroloureiro.hivenetbackup.domain.models.BatchFile
import com.pedroloureiro.hivenetbackup.domain.models.FileState
import com.pedroloureiro.hivenetbackup.domain.models.SourceFile
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

// Refuses the same transitions as BatchDatabaseManager.
class FakeBatchRepository : BatchRepository {
  private val batches = MutableStateFlow<List<Batch>>(emptyList())
  private var nextBatchFileId = 1L

  // Every markCopying and recordOutcome, in call order.
  val recorded = mutableListOf<Pair<Long, FileState>>()

  var recordOutcomeFailure: Exception? = null

  override suspend fun create(sourceFiles: List<SourceFile>): Batch {
    require(sourceFiles.isNotEmpty())
    val batchId = batches.value.size + 1L
    val files = sourceFiles.map { BatchFile(id = nextBatchFileId++, batchId = batchId, sourceFile = it, state = FileState.Waiting) }
    val batch = Batch(id = batchId, createdAt = Instant.ofEpochMilli(batchId), files = files)
    batches.value += batch
    return batch
  }

  override suspend fun markCopying(batchFileId: Long) {
    check(stateOf(batchFileId) == FileState.Waiting)
    record(batchFileId, FileState.Copying)
  }

  override suspend fun recordOutcome(batchFileId: Long, fileState: FileState) {
    recordOutcomeFailure?.let { throw it }
    check(stateOf(batchFileId) == FileState.Copying)
    check(fileState != FileState.Waiting && fileState != FileState.Copying && fileState != FileState.Removed)
    record(batchFileId, fileState)
  }

  override suspend fun settleAbandoned(): List<BatchFile> {
    val interrupted = allFiles().filter { it.state == FileState.Copying }.map { it.copy(state = FileState.Interrupted) }
    allFiles().forEach { file ->
      when (file.state) {
        FileState.Waiting -> setState(file.id, FileState.NotStarted)
        FileState.Copying -> setState(file.id, FileState.Interrupted)
        else -> Unit
      }
    }
    return interrupted
  }

  override suspend fun markRemoved(batchFileId: Long) {
    check(stateOf(batchFileId) is FileState.BackedUp)
    record(batchFileId, FileState.Removed)
  }

  override suspend fun removedFiles(): List<BatchFile> = allFiles().filter { it.state == FileState.Removed }

  override fun observeLatest(): Flow<Batch?> = batches.map { it.lastOrNull() }

  override fun observeBackedUp(): Flow<List<BatchFile>> = batches.map { all ->
    all.reversed().flatMap { batch -> batch.files.filter { it.state is FileState.BackedUp } }
  }

  fun stateOf(batchFileId: Long): FileState = allFiles().single { it.id == batchFileId }.state

  private fun allFiles() = batches.value.flatMap { it.files }

  private fun record(batchFileId: Long, fileState: FileState) {
    recorded += batchFileId to fileState
    setState(batchFileId, fileState)
  }

  private fun setState(batchFileId: Long, fileState: FileState) {
    batches.value = batches.value.map { batch ->
      batch.copy(files = batch.files.map { if (it.id == batchFileId) it.copy(state = fileState) else it })
    }
  }
}
