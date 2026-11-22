package com.pedroloureiro.hivenetbackup.data.mappers

import com.pedroloureiro.hivenetbackup.data.BatchFileOutcome
import com.pedroloureiro.hivenetbackup.data.BatchFileRecord
import com.pedroloureiro.hivenetbackup.data.BatchFileRecord.State
import com.pedroloureiro.hivenetbackup.domain.models.BackupCopy
import com.pedroloureiro.hivenetbackup.domain.models.Batch
import com.pedroloureiro.hivenetbackup.domain.models.BatchFile
import com.pedroloureiro.hivenetbackup.domain.models.FailureReason
import com.pedroloureiro.hivenetbackup.domain.models.FileState
import com.pedroloureiro.hivenetbackup.domain.models.SourceFile
import java.time.Instant

fun SourceFile.toBatchFileRecord() =
  BatchFileRecord(sourceUri = uri, displayName = displayName, mimeType = mimeType, reportedSize = reportedSize)

// The records of one Batch, in picked order. The manager never returns an empty Batch.
fun List<BatchFileRecord>.toBatch(): Batch {
  val first = first()
  return Batch(
    id = first.batchId,
    createdAt = Instant.ofEpochMilli(first.batchCreatedAtEpochMs),
    files = map { it.toBatchFile() },
  )
}

fun BatchFileRecord.toBatchFile() = BatchFile(
  id = id,
  batchId = batchId,
  sourceFile = SourceFile(uri = sourceUri, displayName = displayName, mimeType = mimeType, reportedSize = reportedSize),
  state = toFileState(),
)

fun FileState.toBatchFileOutcome(batchFileId: Long): BatchFileOutcome = when (this) {
  FileState.Waiting -> BatchFileOutcome(id = batchFileId, state = State.WAITING)
  FileState.Copying -> BatchFileOutcome(id = batchFileId, state = State.COPYING)
  is FileState.BackedUp -> BatchFileOutcome(
    id = batchFileId,
    state = State.BACKED_UP,
    storedSize = copy.storedSize,
    fingerprint = copy.fingerprint,
    relativePath = copy.relativePath,
  )
  is FileState.Failed -> BatchFileOutcome(
    id = batchFileId,
    state = State.FAILED,
    failureReason = reason.toRecordFailureReason(),
    failureDetail = (reason as? FailureReason.UnexpectedError)?.detail,
  )
  FileState.Interrupted -> BatchFileOutcome(id = batchFileId, state = State.INTERRUPTED)
  FileState.NotStarted -> BatchFileOutcome(id = batchFileId, state = State.NOT_STARTED)
  FileState.Removed -> BatchFileOutcome(id = batchFileId, state = State.REMOVED)
}

private fun BatchFileRecord.toFileState(): FileState = when (state) {
  State.WAITING -> FileState.Waiting
  State.COPYING -> FileState.Copying
  State.BACKED_UP -> FileState.BackedUp(
    BackupCopy(
      relativePath = required(relativePath, "relativePath"),
      fingerprint = required(fingerprint, "fingerprint"),
      storedSize = required(storedSize, "storedSize"),
    ),
  )
  State.FAILED -> FileState.Failed(toFailureReason())
  State.INTERRUPTED -> FileState.Interrupted
  State.NOT_STARTED -> FileState.NotStarted
  State.REMOVED -> FileState.Removed
}

private fun BatchFileRecord.toFailureReason(): FailureReason = when (required(failureReason, "failureReason")) {
  BatchFileRecord.FailureReason.SOURCE_NO_LONGER_AVAILABLE -> FailureReason.SourceNoLongerAvailable
  BatchFileRecord.FailureReason.COULD_NOT_READ_SOURCE -> FailureReason.CouldNotReadSource
  BatchFileRecord.FailureReason.NOT_ENOUGH_SPACE -> FailureReason.NotEnoughSpace
  BatchFileRecord.FailureReason.COPY_DID_NOT_MATCH_SOURCE -> FailureReason.CopyDidNotMatchSource
  BatchFileRecord.FailureReason.UNEXPECTED_ERROR -> FailureReason.UnexpectedError(failureDetail.orEmpty())
}

private fun FailureReason.toRecordFailureReason(): BatchFileRecord.FailureReason = when (this) {
  FailureReason.SourceNoLongerAvailable -> BatchFileRecord.FailureReason.SOURCE_NO_LONGER_AVAILABLE
  FailureReason.CouldNotReadSource -> BatchFileRecord.FailureReason.COULD_NOT_READ_SOURCE
  FailureReason.NotEnoughSpace -> BatchFileRecord.FailureReason.NOT_ENOUGH_SPACE
  FailureReason.CopyDidNotMatchSource -> BatchFileRecord.FailureReason.COPY_DID_NOT_MATCH_SOURCE
  is FailureReason.UnexpectedError -> BatchFileRecord.FailureReason.UNEXPECTED_ERROR
}

// Only a broken row lacks a field its state requires: the manager writes them together.
private fun <T : Any> BatchFileRecord.required(value: T?, field: String): T =
  checkNotNull(value) { "Batch file $id is $state but has no $field" }
