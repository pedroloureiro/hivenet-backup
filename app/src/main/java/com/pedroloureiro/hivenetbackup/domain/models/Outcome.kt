package com.pedroloureiro.hivenetbackup.domain.models

sealed interface Outcome : FileState {
  data class BackedUp(val copy: BackupCopy) : Outcome

  data class Failed(val reason: FailureReason) : Outcome

  data object Interrupted : Outcome

  data object NotStarted : Outcome
}
