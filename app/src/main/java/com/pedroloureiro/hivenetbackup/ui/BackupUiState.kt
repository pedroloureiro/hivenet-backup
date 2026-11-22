package com.pedroloureiro.hivenetbackup.ui

import com.pedroloureiro.hivenetbackup.domain.models.Batch
import com.pedroloureiro.hivenetbackup.domain.models.SourceFile

data class BackupUiState(
  val selection: List<SourceFile> = emptyList(),
  val latestBatch: Batch? = null,
) {
  val canSelectFiles: Boolean
    get() = latestBatch?.isRunning != true

  val canBackUp: Boolean
    get() = canSelectFiles && selection.isNotEmpty()
}
