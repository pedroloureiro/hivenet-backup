package com.pedroloureiro.hivenetbackup.domain.models

sealed interface FileState {
  data object Waiting : FileState

  data object Copying : FileState

  data class BackedUp(val copy: BackupCopy) : FileState

  data class Failed(val reason: FailureReason) : FileState

  data object Interrupted : FileState

  data object NotStarted : FileState

  data object Removed : FileState
}
