package com.pedroloureiro.hivenetbackup.data

import com.pedroloureiro.hivenetbackup.data.BatchFileRecord.FailureReason
import com.pedroloureiro.hivenetbackup.data.BatchFileRecord.State

data class BatchFileOutcome(
  val id: Long,
  val state: State,
  val failureReason: FailureReason? = null,
  val failureDetail: String? = null,
  val storedSize: Long? = null,
  val fingerprint: String? = null,
  val relativePath: String? = null,
)
