package com.pedroloureiro.hivenetbackup.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pedroloureiro.hivenetbackup.domain.models.SourceFile
import com.pedroloureiro.hivenetbackup.domain.repositories.BatchRepository
import com.pedroloureiro.hivenetbackup.domain.repositories.SourceFileRepository
import com.pedroloureiro.hivenetbackup.domain.usecases.RunBatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BackupViewModel(
  batchRepository: BatchRepository,
  private val sourceFileRepository: SourceFileRepository,
  private val runBatch: RunBatch,
  // App-wide, so a Batch outlives the screen (ADR-0002).
  private val appScope: CoroutineScope,
  private val abandonedSettled: Job,
) : ViewModel() {
  private val selection = MutableStateFlow<List<SourceFile>>(emptyList())

  val uiState: StateFlow<BackupUiState> = combine(selection, batchRepository.observeLatest(), ::BackupUiState)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), BackupUiState())

  fun addToSelection(uris: List<String>) {
    viewModelScope.launch {
      val picked = uris.map { sourceFileRepository.describe(it) }
      selection.update { current -> (current + picked).distinctBy { it.uri } }
    }
  }

  fun clearSelection() {
    selection.value = emptyList()
  }

  fun backUp() {
    val sourceFiles = selection.getAndUpdate { emptyList() }
    if (sourceFiles.isEmpty()) return
    appScope.launch {
      // Settling after create would mark this Batch's Waiting files Not started.
      abandonedSettled.join()
      runBatch(sourceFiles)
    }
  }

  private companion object {
    const val STOP_TIMEOUT_MS = 5_000L
  }
}
