package com.pedroloureiro.hivenetbackup.ui

import com.pedroloureiro.hivenetbackup.domain.models.FailureReason
import com.pedroloureiro.hivenetbackup.domain.models.FileState
import com.pedroloureiro.hivenetbackup.domain.repositories.FakeBackupCopyRepository
import com.pedroloureiro.hivenetbackup.domain.repositories.FakeBackupCopyRepository.Companion.backupCopyOf
import com.pedroloureiro.hivenetbackup.domain.repositories.FakeBatchRepository
import com.pedroloureiro.hivenetbackup.domain.repositories.FakeSourceFileRepository
import com.pedroloureiro.hivenetbackup.domain.usecases.RunBatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BackupViewModelTest {
  private val batchRepository = FakeBatchRepository()
  private val sourceFileRepository = FakeSourceFileRepository()
  private val runBatch = RunBatch(batchRepository, sourceFileRepository, FakeBackupCopyRepository())

  @Before
  fun setMainDispatcher() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
  }

  @After
  fun resetMainDispatcher() {
    Dispatchers.resetMain()
  }

  @Test
  fun `picks are appended in picked order and a file already selected isn't added twice`() = runTest {
    val viewModel = observedViewModel()
    val (a, b, c) = listOf("a.txt", "b.txt", "c.txt").map { sourceFileRepository.add(it, it) }

    viewModel.addToSelection(listOf(b.uri, a.uri))
    viewModel.addToSelection(listOf(a.uri, c.uri))

    assertEquals(listOf(b, a, c), viewModel.uiState.value.selection)
  }

  @Test
  fun `starting a Batch clears the selection and backs up what was selected`() = runTest {
    val viewModel = observedViewModel()
    val (a, b) = listOf("a.txt", "b.txt").map { sourceFileRepository.add(it, it) }
    viewModel.addToSelection(listOf(a.uri, b.uri))

    viewModel.backUp()

    assertEquals(emptyList<Any>(), viewModel.uiState.value.selection)
    advanceUntilIdle()
    assertEquals(
      listOf(
        FileState.BackedUp(backupCopyOf(batchId = 1, batchFileId = 1, "a.txt", "a.txt")),
        FileState.BackedUp(backupCopyOf(batchId = 1, batchFileId = 2, "b.txt", "b.txt")),
      ),
      viewModel.uiState.value.latestBatch?.files?.map { it.state },
    )
  }

  @Test
  fun `a second tap before the screen updates starts nothing`() = runTest {
    val viewModel = observedViewModel()
    viewModel.addToSelection(listOf(sourceFileRepository.add("a.txt", "a").uri))

    viewModel.backUp()
    viewModel.backUp()
    advanceUntilIdle()

    assertEquals(1L, viewModel.uiState.value.latestBatch?.id)
  }

  @Test
  fun `state follows the latest Batch, running while a file is Waiting or Copying`() = runTest {
    val viewModel = observedViewModel()
    assertNull(viewModel.uiState.value.latestBatch)
    assertTrue(viewModel.uiState.value.canSelectFiles)

    val batch = batchRepository.create(listOf(sourceFileRepository.add("a.txt", "a")))
    val batchFileId = batch.files.single().id
    viewModel.addToSelection(listOf(sourceFileRepository.add("b.txt", "b").uri))

    assertEquals(batch, viewModel.uiState.value.latestBatch)
    assertFalse(viewModel.uiState.value.canSelectFiles)
    assertFalse(viewModel.uiState.value.canBackUp)

    batchRepository.markCopying(batchFileId)
    assertEquals(FileState.Copying, viewModel.uiState.value.latestBatch?.files?.single()?.state)
    assertFalse(viewModel.uiState.value.canBackUp)

    batchRepository.recordOutcome(batchFileId, FileState.Failed(FailureReason.CouldNotReadSource))
    assertEquals(FileState.Failed(FailureReason.CouldNotReadSource), viewModel.uiState.value.latestBatch?.files?.single()?.state)
    assertTrue(viewModel.uiState.value.canSelectFiles)
    assertTrue(viewModel.uiState.value.canBackUp)
  }

  // The test scope stands in for the app-wide scope, so runTest fails if the launched Batch throws.
  private fun TestScope.observedViewModel(): BackupViewModel {
    val viewModel = BackupViewModel(batchRepository, sourceFileRepository, runBatch, appScope = this)
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    return viewModel
  }
}
