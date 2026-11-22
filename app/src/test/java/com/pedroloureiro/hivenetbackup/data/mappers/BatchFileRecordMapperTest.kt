package com.pedroloureiro.hivenetbackup.data.mappers

import com.pedroloureiro.hivenetbackup.data.BatchFileOutcome
import com.pedroloureiro.hivenetbackup.data.BatchFileRecord
import com.pedroloureiro.hivenetbackup.domain.models.BackupCopy
import com.pedroloureiro.hivenetbackup.domain.models.Batch
import com.pedroloureiro.hivenetbackup.domain.models.BatchFile
import com.pedroloureiro.hivenetbackup.domain.models.FailureReason
import com.pedroloureiro.hivenetbackup.domain.models.FileState
import com.pedroloureiro.hivenetbackup.domain.models.SourceFile
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BatchFileRecordMapperTest {
  private val sourceFile = SourceFile(uri = "content://docs/1", displayName = "a.txt", mimeType = "text/plain", reportedSize = 3)

  @Test
  fun `every file state survives the round trip through a record`() {
    val fileStates = listOf(
      FileState.Waiting,
      FileState.Copying,
      FileState.BackedUp(BackupCopy(relativePath = "1/7/a.txt", fingerprint = "ab12", storedSize = 3)),
      FileState.Failed(FailureReason.SourceNoLongerAvailable),
      FileState.Failed(FailureReason.CouldNotReadSource),
      FileState.Failed(FailureReason.NotEnoughSpace),
      FileState.Failed(FailureReason.CopyDidNotMatchSource),
      FileState.Failed(FailureReason.UnexpectedError("java.io.IOException: EIO")),
      FileState.Interrupted,
      FileState.NotStarted,
      FileState.Removed,
    )

    fileStates.forEach { fileState ->
      val record = sourceFile.toBatchFileRecord().copy(id = 7, batchId = 2).applying(fileState.toBatchFileOutcome(batchFileId = 7))

      assertEquals(BatchFile(id = 7, batchId = 2, sourceFile = sourceFile, state = fileState), record.toBatchFile())
    }
  }

  @Test
  fun `a source file with an unknown size survives the round trip`() {
    val unknownSize = sourceFile.copy(reportedSize = null)

    assertEquals(unknownSize, unknownSize.toBatchFileRecord().toBatchFile().sourceFile)
  }

  @Test
  fun `records of one batch become that batch with its files in order`() {
    val records = listOf(record(id = 4, displayName = "b.txt"), record(id = 5, displayName = "a.txt"))

    val batch = records.toBatch()

    assertEquals(
      Batch(id = 2, createdAt = Instant.ofEpochMilli(1_000), files = records.map { it.toBatchFile() }),
      batch,
    )
    assertEquals(listOf("b.txt", "a.txt"), batch.files.map { it.sourceFile.displayName })
  }

  @Test
  fun `a backed up record without its copy is refused`() {
    val record = record(id = 7).copy(state = BatchFileRecord.State.BACKED_UP, storedSize = 3, relativePath = "1/7/a.txt")

    assertThrows(IllegalStateException::class.java) { record.toBatchFile() }
  }

  @Test
  fun `a failed record without its failure reason is refused`() {
    val record = record(id = 7).copy(state = BatchFileRecord.State.FAILED)

    assertThrows(IllegalStateException::class.java) { record.toBatchFile() }
  }

  private fun record(id: Long, displayName: String = "a.txt") =
    sourceFile.copy(displayName = displayName).toBatchFileRecord().copy(id = id, batchId = 2, batchCreatedAtEpochMs = 1_000)

  // What Room's partial update does with a BatchFileOutcome.
  private fun BatchFileRecord.applying(outcome: BatchFileOutcome) = copy(
    state = outcome.state,
    failureReason = outcome.failureReason,
    failureDetail = outcome.failureDetail,
    storedSize = outcome.storedSize,
    fingerprint = outcome.fingerprint,
    relativePath = outcome.relativePath,
  )
}
