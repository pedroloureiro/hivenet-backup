package com.pedroloureiro.hivenetbackup.data.entities

import com.pedroloureiro.hivenetbackup.data.entities.BatchFileEntity.FailureReason
import com.pedroloureiro.hivenetbackup.data.entities.BatchFileEntity.State

// The batch_files columns an Outcome sets, written as a Room partial update.
data class BatchFileOutcome(
  val id: Long,
  val state: State,
  val failureReason: FailureReason? = null,
  val failureDetail: String? = null,
  val storedSize: Long? = null,
  val fingerprint: String? = null,
  val relativePath: String? = null,
)
