package com.pedroloureiro.hivenetbackup.domain.models

data class BatchFile(
  val id: Long,
  val sourceFile: SourceFile,
  val state: FileState,
)
