package com.pedroloureiro.hivenetbackup.domain.usecases

import com.pedroloureiro.hivenetbackup.domain.models.FileState
import com.pedroloureiro.hivenetbackup.domain.repositories.FakeBackupCopyRepository
import com.pedroloureiro.hivenetbackup.domain.repositories.FakeBackupCopyRepository.Companion.backupCopyOf
import com.pedroloureiro.hivenetbackup.domain.repositories.FakeBatchRepository
import com.pedroloureiro.hivenetbackup.domain.repositories.FakeSourceFileRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettleAbandonedBatchTest {
  private val batchRepository = FakeBatchRepository()
  private val sourceFileRepository = FakeSourceFileRepository()
  private val backupCopyRepository = FakeBackupCopyRepository()
  private val settleAbandonedBatch = SettleAbandonedBatch(batchRepository, backupCopyRepository)

  @Test
  fun `settles Waiting as Not started and Copying as Interrupted, leaving Outcomes alone`() = runTest {
    val (done, busy, next) = abandonedBatch()

    settleAbandonedBatch()

    assertEquals(FileState.BackedUp(backupCopyOf(batchId = 1, batchFileId = done, "done.txt", "d")), batchRepository.stateOf(done))
    assertEquals(FileState.Interrupted, batchRepository.stateOf(busy))
    assertEquals(FileState.NotStarted, batchRepository.stateOf(next))
  }

  @Test
  fun `deletes every part file and the folder of each file it marks Interrupted`() = runTest {
    val (_, busy, _) = abandonedBatch()

    settleAbandonedBatch()

    assertTrue(backupCopyRepository.partFilesDeleted)
    assertEquals(listOf(1L to busy), backupCopyRepository.deletedFolders)
  }

  @Test
  fun `deletes the folder of every Removed file`() = runTest {
    val batchFileId = batchRepository.create(listOf(sourceFileRepository.add("a.txt", "a"))).files.single().id
    batchRepository.markCopying(batchFileId)
    batchRepository.recordOutcome(batchFileId, FileState.BackedUp(backupCopyOf(batchId = 1, batchFileId = batchFileId, "a.txt", "a")))
    batchRepository.markRemoved(batchFileId)

    settleAbandonedBatch()

    assertEquals(listOf(1L to batchFileId), backupCopyRepository.deletedFolders)
  }

  // A Batch the app stopped during its second file.
  private suspend fun abandonedBatch(): List<Long> {
    val batch = batchRepository.create(
      listOf(sourceFileRepository.add("done.txt", "d"), sourceFileRepository.add("busy.txt", "b"), sourceFileRepository.add("next.txt", "n")),
    )
    val (done, busy, next) = batch.files.map { it.id }
    batchRepository.markCopying(done)
    batchRepository.recordOutcome(done, FileState.BackedUp(backupCopyOf(batchId = 1, batchFileId = done, "done.txt", "d")))
    batchRepository.markCopying(busy)
    return listOf(done, busy, next)
  }
}
