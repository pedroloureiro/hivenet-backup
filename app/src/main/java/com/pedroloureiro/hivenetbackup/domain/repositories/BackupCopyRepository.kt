package com.pedroloureiro.hivenetbackup.domain.repositories

import com.pedroloureiro.hivenetbackup.domain.models.BackupCopy
import com.pedroloureiro.hivenetbackup.domain.models.BatchFile
import java.io.InputStream

interface BackupCopyRepository {
  fun freeSpace(): Long

  /**
   * Returns only once the copy is confirmed and under its final name. On failure nothing is left behind;
   * source-stream exceptions propagate unchanged, every other failure is a BackupCopyFailedException.
   */
  suspend fun write(batchId: Long, file: BatchFile, source: InputStream): BackupCopy

  fun contentUriFor(copy: BackupCopy): String

  suspend fun deletePartFiles()
}
