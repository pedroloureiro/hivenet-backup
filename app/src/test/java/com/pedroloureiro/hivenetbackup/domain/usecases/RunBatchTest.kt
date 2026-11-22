package com.pedroloureiro.hivenetbackup.domain.usecases

import com.pedroloureiro.hivenetbackup.domain.BackupCopyFailedException
import com.pedroloureiro.hivenetbackup.domain.models.FailureReason
import com.pedroloureiro.hivenetbackup.domain.models.FileState
import com.pedroloureiro.hivenetbackup.domain.repositories.FakeBackupCopyRepository
import com.pedroloureiro.hivenetbackup.domain.repositories.FakeBackupCopyRepository.Companion.backupCopyOf
import com.pedroloureiro.hivenetbackup.domain.repositories.FakeBatchRepository
import com.pedroloureiro.hivenetbackup.domain.repositories.FakeSourceFileRepository
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RunBatchTest {
  private val batchRepository = FakeBatchRepository()
  private val sourceFileRepository = FakeSourceFileRepository()
  private val backupCopyRepository = FakeBackupCopyRepository()
  private val runBatch = RunBatch(batchRepository, sourceFileRepository, backupCopyRepository)

  @Test
  fun `records the Batch in picked order and backs up one file at a time`() = runTest {
    val sourceFiles = listOf(sourceFileRepository.add("b.txt", "bee"), sourceFileRepository.add("a.txt", "ay"))

    runBatch(sourceFiles)

    assertEquals(sourceFiles, latestFiles().map { it.sourceFile })
    assertEquals(
      listOf(
        1L to FileState.Copying,
        1L to FileState.BackedUp(backupCopyOf(batchId = 1, batchFileId = 1, "b.txt", "bee")),
        2L to FileState.Copying,
        2L to FileState.BackedUp(backupCopyOf(batchId = 1, batchFileId = 2, "a.txt", "ay")),
      ),
      batchRepository.recorded,
    )
  }

  @Test
  fun `a failed file doesn't stop the files after it`() = runTest {
    val sourceFiles = listOf(
      sourceFileRepository.add("a.txt", "a"),
      sourceFileRepository.add("gone.txt", reportedSize = 1) { throw FileNotFoundException("gone.txt") },
      sourceFileRepository.add("c.txt", "c"),
    )

    runBatch(sourceFiles)

    assertEquals(
      listOf(
        FileState.BackedUp(backupCopyOf(batchId = 1, batchFileId = 1, "a.txt", "a")),
        FileState.Failed(FailureReason.SourceNoLongerAvailable),
        FileState.BackedUp(backupCopyOf(batchId = 1, batchFileId = 3, "c.txt", "c")),
      ),
      latestFiles().map { it.state },
    )
  }

  @Test
  fun `a source that can't be opened is Source no longer available`() = runTest {
    assertEquals(FailureReason.SourceNoLongerAvailable, failureReasonWhenOpening { throw FileNotFoundException("gone") })
  }

  @Test
  fun `Downloads' SecurityException for a deleted source is Source no longer available`() = runTest {
    assertEquals(FailureReason.SourceNoLongerAvailable, failureReasonWhenOpening { breaksWith(SecurityException("Permission Denial")) })
  }

  @Test
  fun `any other read error is Couldn't read source`() = runTest {
    assertEquals(FailureReason.CouldNotReadSource, failureReasonWhenOpening { breaksWith(IOException("EIO")) })
  }

  @Test
  fun `anything else is Unexpected error with its detail`() = runTest {
    assertEquals(
      FailureReason.UnexpectedError("java.lang.IllegalArgumentException: bad uri"),
      failureReasonWhenOpening { throw IllegalArgumentException("bad uri") },
    )
  }

  @Test
  fun `records the Failure reason the Backup store reports`() = runTest {
    backupCopyRepository.writeFailures["full.txt"] = BackupCopyFailedException(FailureReason.NotEnoughSpace)
    backupCopyRepository.writeFailures["grew.txt"] = BackupCopyFailedException(FailureReason.CopyDidNotMatchSource)

    runBatch(listOf(sourceFileRepository.add("full.txt", "f"), sourceFileRepository.add("grew.txt", "g")))

    assertEquals(
      listOf(FileState.Failed(FailureReason.NotEnoughSpace), FileState.Failed(FailureReason.CopyDidNotMatchSource)),
      latestFiles().map { it.state },
    )
  }

  @Test
  fun `fails as Not enough space only the file whose reported size doesn't fit with the margin`() = runTest {
    backupCopyRepository.freeSpaceBytes = 100 * MB
    val sourceFiles = listOf(
      sourceFileRepository.add("too-big.bin", "x", reportedSize = 50 * MB + 1),
      sourceFileRepository.add("just-fits.bin", "y", reportedSize = 50 * MB),
      sourceFileRepository.add("unknown-size.bin", "z", reportedSize = null),
    )

    runBatch(sourceFiles)

    assertEquals(FileState.Failed(FailureReason.NotEnoughSpace), latestFiles().first().state)
    assertEquals(listOf("just-fits.bin", "unknown-size.bin"), backupCopyRepository.writtenNames)
  }

  @Test
  fun `cancellation stops the Batch without recording an Outcome`() = runTest {
    val sourceFiles = listOf(
      sourceFileRepository.add("a.txt", reportedSize = 1) { breaksWith(CancellationException("process dying")) },
      sourceFileRepository.add("b.txt", "b"),
    )

    val failure = runCatching { runBatch(sourceFiles) }.exceptionOrNull()

    assertEquals(CancellationException::class, failure?.let { it::class })
    assertEquals(listOf(FileState.Copying, FileState.Waiting), latestFiles().map { it.state })
  }

  @Test
  fun `a refused record stops the Batch instead of becoming a Failure reason`() = runTest {
    batchRepository.recordOutcomeFailure = IllegalStateException("Batch file 1 is BACKED_UP, so it can't take an Outcome")

    val failure = runCatching { runBatch(listOf(sourceFileRepository.add("a.txt", "a"), sourceFileRepository.add("b.txt", "b"))) }
      .exceptionOrNull()

    assertEquals(IllegalStateException::class, failure?.let { it::class })
    assertEquals("Batch file 1 is BACKED_UP, so it can't take an Outcome", failure?.message)
    assertEquals(listOf(FileState.Copying, FileState.Waiting), latestFiles().map { it.state })
  }

  private suspend fun failureReasonWhenOpening(open: () -> InputStream): FailureReason? {
    runBatch(listOf(sourceFileRepository.add("a.txt", reportedSize = 1, open)))
    return (latestFiles().single().state as? FileState.Failed)?.reason
  }

  private suspend fun latestFiles() = batchRepository.observeLatest().first()?.files.orEmpty()

  private fun breaksWith(readError: Exception) = object : InputStream() {
    override fun read(): Int = throw readError
  }

  private companion object {
    const val MB = 1024L * 1024
  }
}
