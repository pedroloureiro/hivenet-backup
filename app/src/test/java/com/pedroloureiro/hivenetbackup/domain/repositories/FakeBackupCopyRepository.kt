package com.pedroloureiro.hivenetbackup.domain.repositories

import com.pedroloureiro.hivenetbackup.domain.BackupCopyFailedException
import com.pedroloureiro.hivenetbackup.domain.models.BackupCopy
import com.pedroloureiro.hivenetbackup.domain.models.BatchFile
import java.io.InputStream

class FakeBackupCopyRepository : BackupCopyRepository {
  var freeSpaceBytes = Long.MAX_VALUE

  // Thrown after reading the source, as the real write does for a Backup store failure.
  val writeFailures = mutableMapOf<String, BackupCopyFailedException>()

  val writtenNames = mutableListOf<String>()
  var partFilesDeleted = false
  val deletedFolders = mutableListOf<Pair<Long, Long>>()

  override fun freeSpace(): Long = freeSpaceBytes

  override suspend fun write(batchId: Long, file: BatchFile, source: InputStream): BackupCopy {
    val content = source.readBytes().decodeToString()
    val displayName = file.sourceFile.displayName
    writeFailures[displayName]?.let { throw it }
    writtenNames += displayName
    return backupCopyOf(batchId, file.id, displayName, content)
  }

  override suspend fun deletePartFiles() {
    partFilesDeleted = true
  }

  override suspend fun deleteFolderOf(batchId: Long, batchFileId: Long) {
    deletedFolders += batchId to batchFileId
  }

  companion object {
    // The content stands in for its Fingerprint, so a test can tell which bytes were stored.
    fun backupCopyOf(batchId: Long, batchFileId: Long, displayName: String, content: String) =
      BackupCopy(relativePath = "$batchId/$batchFileId/$displayName", fingerprint = content, storedSize = content.length.toLong())
  }
}
