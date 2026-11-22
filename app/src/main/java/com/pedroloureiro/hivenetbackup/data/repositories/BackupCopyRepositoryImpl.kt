package com.pedroloureiro.hivenetbackup.data.repositories

import com.pedroloureiro.hivenetbackup.data.mappers.toBackupCopyName
import com.pedroloureiro.hivenetbackup.data.mappers.toBackupStoreFailureReason
import com.pedroloureiro.hivenetbackup.domain.BackupCopyFailedException
import com.pedroloureiro.hivenetbackup.domain.models.BackupCopy
import com.pedroloureiro.hivenetbackup.domain.models.BatchFile
import com.pedroloureiro.hivenetbackup.domain.models.FailureReason
import com.pedroloureiro.hivenetbackup.domain.repositories.BackupCopyRepository
import com.pedroloureiro.hivenetbackup.extensions.digest
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BackupCopyRepositoryImpl(private val backupStore: File) : BackupCopyRepository {
  override fun freeSpace(): Long {
    // usableSpace is 0 for a folder that doesn't exist yet.
    backupStore.mkdirs()
    return backupStore.usableSpace
  }

  override suspend fun write(batchId: Long, file: BatchFile, source: InputStream): BackupCopy = withContext(Dispatchers.IO) {
    val folder = folderOf(batchId, file.id)
    val backupCopyFile = File(folder, file.sourceFile.displayName.toBackupCopyName(MAX_NAME_BYTES))
    val partFile = File(folder, backupCopyFile.name + PART_SUFFIX)
    try {
      folder.mkdirs()
      val fingerprint = copyToPartFile(source, partFile, file.sourceFile.reportedSize)
      confirm(partFile, fingerprint)
      inBackupStore { Files.move(partFile.toPath(), backupCopyFile.toPath(), StandardCopyOption.ATOMIC_MOVE) }
      BackupCopy(relativePath = backupCopyFile.relativeTo(backupStore).path, fingerprint = fingerprint, storedSize = backupCopyFile.length())
    } catch (failure: Throwable) {
      folder.deleteRecursively()
      throw failure
    }
  }

  override suspend fun deletePartFiles() = withContext(Dispatchers.IO) {
    backupStore.walk().filter { it.isFile && it.name.endsWith(PART_SUFFIX) }.forEach { it.delete() }
  }

  override suspend fun deleteFolderOf(batchId: Long, batchFileId: Long) {
    withContext(Dispatchers.IO) { folderOf(batchId, batchFileId).deleteRecursively() }
  }

  private fun folderOf(batchId: Long, batchFileId: Long) = File(backupStore, "$batchId/$batchFileId")

  // Returns the Fingerprint of the bytes read from the source.
  private fun copyToPartFile(source: InputStream, partFile: File, reportedSize: Long?): String {
    val digest = MessageDigest.getInstance(FINGERPRINT_ALGORITHM)
    var byteCount = 0L
    inBackupStore { FileOutputStream(partFile) }.use { output ->
      val buffer = ByteArray(BUFFER_BYTES)
      while (true) {
        // Exceptions from the Source file pass unchanged: RunBatch maps them.
        val count = source.read(buffer)
        if (count == -1) break
        digest.update(buffer, 0, count)
        inBackupStore { output.write(buffer, 0, count) }
        byteCount += count
      }
      if (reportedSize != null && byteCount != reportedSize) throw BackupCopyFailedException(FailureReason.CopyDidNotMatchSource)
      inBackupStore { output.fd.sync() }
    }
    return digest.digest().toHexString()
  }

  private fun confirm(partFile: File, fingerprint: String) {
    val storedFingerprint = inBackupStore { partFile.digest(FINGERPRINT_ALGORITHM).toHexString() }
    if (storedFingerprint != fingerprint) throw BackupCopyFailedException(FailureReason.CopyDidNotMatchSource)
  }

  // An IOException here comes from the Backup store, never from the Source file.
  private inline fun <T> inBackupStore(block: () -> T): T = try {
    block()
  } catch (exception: IOException) {
    throw BackupCopyFailedException(exception.toBackupStoreFailureReason(), exception)
  }

  private companion object {
    const val PART_SUFFIX = ".part"
    const val FINGERPRINT_ALGORITHM = "SHA-256"
    const val BUFFER_BYTES = 64 * 1024

    // The file-system limit of 255 bytes, less room for PART_SUFFIX.
    const val MAX_NAME_BYTES = 255 - PART_SUFFIX.length
  }
}
