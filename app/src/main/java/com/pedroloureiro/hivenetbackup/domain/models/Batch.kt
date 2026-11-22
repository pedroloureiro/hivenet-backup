package com.pedroloureiro.hivenetbackup.domain.models

import java.time.Instant

data class Batch(
  val id: Long,
  val createdAt: Instant,
  val files: List<BatchFile>,
)
