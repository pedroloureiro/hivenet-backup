package com.pedroloureiro.hivenetbackup.domain.models

sealed interface FileState {
  data object Waiting : FileState

  data object Copying : FileState
}
