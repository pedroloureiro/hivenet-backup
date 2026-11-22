package com.pedroloureiro.hivenetbackup.domain.models

sealed interface FailureReason {
  data object SourceNoLongerAvailable : FailureReason

  data object CouldNotReadSource : FailureReason

  data object NotEnoughSpace : FailureReason

  data object CopyDidNotMatchSource : FailureReason

  data class UnexpectedError(val detail: String) : FailureReason
}
