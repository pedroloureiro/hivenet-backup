package com.pedroloureiro.hivenetbackup.domain.models

data class SourceFile(
  val uri: String,
  val displayName: String,
  val mimeType: String,
  val reportedSize: Long?,
)
