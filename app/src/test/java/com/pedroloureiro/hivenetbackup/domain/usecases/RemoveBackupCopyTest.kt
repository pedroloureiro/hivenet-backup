package com.pedroloureiro.hivenetbackup.domain.usecases

import com.pedroloureiro.hivenetbackup.domain.models.BatchFile
import com.pedroloureiro.hivenetbackup.domain.models.FailureReason
import com.pedroloureiro.hivenetbackup.domain.models.FileState
import com.pedroloureiro.hivenetbackup.domain.repositories.FakeBackupCopyRepository
import com.pedroloureiro.hivenetbackup.domain.repositories.FakeBackupCopyRepository.Companion.backupCopyOf
import com.pedroloureiro.hivenetbackup.domain.repositories.FakeBatchRepository
import com.pedroloureiro.hivenetbackup.domain.repositories.FakeSourceFileRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoveBackupCopyTest {
  private val batchRepository = FakeBatchRepository()
  private val sourceFileRepository = FakeSourceFileRepository()
  private val backupCopyRepository = FakeBackupCopyRepository()
  private val removeBackupCopy = RemoveBackupCopy(batchRepository, backupCopyRepository)

  @Test
  fun `records Removed, then deletes the file's folder`() = runTest {
    val batchFile = finishedFile(FileState.BackedUp(backupCopyOf(batchId = 1, batchFileId = 1, "a.txt", "a")))

    removeBackupCopy(batchFile)

    assertEquals(FileState.Removed, batchRepository.stateOf(batchFile.id))
    assertEquals(listOf(1L to batchFile.id), backupCopyRepository.deletedFolders)
  }

  @Test
  fun `a refused record deletes nothing`() = runTest {
    val batchFile = finishedFile(FileState.Failed(FailureReason.CouldNotReadSource))

    val failure = runCatching { removeBackupCopy(batchFile) }.exceptionOrNull()

    assertTrue("Expected IllegalStateException, got $failure", failure is IllegalStateException)
    assertEquals(emptyList<Pair<Long, Long>>(), backupCopyRepository.deletedFolders)
  }

  private suspend fun finishedFile(outcome: FileState): BatchFile {
    val batchFile = batchRepository.create(listOf(sourceFileRepository.add("a.txt", "a"))).files.single()
    batchRepository.markCopying(batchFile.id)
    batchRepository.recordOutcome(batchFile.id, outcome)
    return batchFile
  }
}
