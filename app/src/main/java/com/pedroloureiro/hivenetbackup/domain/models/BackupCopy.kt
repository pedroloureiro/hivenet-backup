package com.pedroloureiro.hivenetbackup.domain.models

data class BackupCopy(
  // Relative to the Backup store.
  val relativePath: String,
  val fingerprint: String,
  val storedSize: Long,
)
