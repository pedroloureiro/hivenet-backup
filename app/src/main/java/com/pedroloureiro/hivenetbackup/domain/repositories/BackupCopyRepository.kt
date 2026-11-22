package com.pedroloureiro.hivenetbackup.domain.repositories

import com.pedroloureiro.hivenetbackup.domain.models.BackupCopy
import com.pedroloureiro.hivenetbackup.domain.models.BatchFile
import java.io.InputStream

interface BackupCopyRepository {
  fun freeSpace(): Long

  suspend fun write(batchId: Long, file: BatchFile, source: InputStream): BackupCopy

  fun contentUriFor(copy: BackupCopy): String

  suspend fun deletePartFiles()
}
