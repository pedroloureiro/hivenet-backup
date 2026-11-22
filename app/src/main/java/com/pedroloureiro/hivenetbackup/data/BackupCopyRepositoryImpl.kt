package com.pedroloureiro.hivenetbackup.data

import android.system.ErrnoException
import android.system.OsConstants
import com.pedroloureiro.hivenetbackup.domain.BackupCopyFailedException
import com.pedroloureiro.hivenetbackup.domain.models.BackupCopy
import com.pedroloureiro.hivenetbackup.domain.models.BatchFile
import com.pedroloureiro.hivenetbackup.domain.models.FailureReason
import com.pedroloureiro.hivenetbackup.domain.repositories.BackupCopyRepository
import java.io.File
import java.io.FileInputStream
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
    val relativePath = "$batchId/${file.id}/${sanitised(file.sourceFile.displayName)}"
    val backupCopyFile = File(backupStore, relativePath)
    val folder = backupCopyFile.parentFile!!
    val partFile = File(folder, backupCopyFile.name + PART_SUFFIX)
    try {
      folder.mkdirs()
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
        val reportedSize = file.sourceFile.reportedSize
        if (reportedSize != null && byteCount != reportedSize) throw BackupCopyFailedException(FailureReason.CopyDidNotMatchSource)
        inBackupStore { output.fd.sync() }
      }
      val fingerprint = digest.digest().toHex()
      if (inBackupStore { fingerprintOf(partFile) } != fingerprint) throw BackupCopyFailedException(FailureReason.CopyDidNotMatchSource)
      inBackupStore { Files.move(partFile.toPath(), backupCopyFile.toPath(), StandardCopyOption.ATOMIC_MOVE) }
      BackupCopy(relativePath = relativePath, fingerprint = fingerprint, storedSize = byteCount)
    } catch (failure: Throwable) {
      folder.deleteRecursively()
      throw failure
    }
  }

  override suspend fun deletePartFiles() = withContext(Dispatchers.IO) {
    backupStore.walk().filter { it.isFile && it.name.endsWith(PART_SUFFIX) }.forEach { it.delete() }
  }

  override suspend fun deleteFolderOf(batchId: Long, batchFileId: Long) {
    withContext(Dispatchers.IO) { File(backupStore, "$batchId/$batchFileId").deleteRecursively() }
  }

  private fun fingerprintOf(file: File): String {
    val digest = MessageDigest.getInstance(FINGERPRINT_ALGORITHM)
    FileInputStream(file).use { input ->
      val buffer = ByteArray(BUFFER_BYTES)
      while (true) {
        val count = input.read(buffer)
        if (count == -1) break
        digest.update(buffer, 0, count)
      }
    }
    return digest.digest().toHex()
  }

  // An IOException here comes from the Backup store, never from the Source file.
  private inline fun <T> inBackupStore(block: () -> T): T = try {
    block()
  } catch (exception: IOException) {
    throw BackupCopyFailedException(failureReasonFor(exception), exception)
  }

  private fun failureReasonFor(exception: IOException): FailureReason =
    if ((exception.cause as? ErrnoException)?.errno == OsConstants.ENOSPC) {
      FailureReason.NotEnoughSpace
    } else {
      FailureReason.UnexpectedError(exception.toString())
    }

  private fun sanitised(displayName: String): String {
    val name = displayName.replace('/', '_').replace('\u0000', '_')
    if (name == "" || name == "." || name == "..") return "_"
    if (name.utf8Bytes() <= MAX_NAME_BYTES) return name
    val dot = name.lastIndexOf('.')
    val extension = if (dot > 0) name.substring(dot) else ""
    if (extension.isEmpty() || extension.utf8Bytes() > MAX_EXTENSION_BYTES) return name.cutTo(MAX_NAME_BYTES)
    return name.substring(0, dot).cutTo(MAX_NAME_BYTES - extension.utf8Bytes()) + extension
  }

  // Never splits a character, surrogate pairs included.
  private fun String.cutTo(maxBytes: Int): String {
    var end = 0
    var bytes = 0
    while (end < length) {
      val next = offsetByCodePoints(end, 1)
      bytes += substring(end, next).utf8Bytes()
      if (bytes > maxBytes) break
      end = next
    }
    return substring(0, end)
  }

  private fun String.utf8Bytes(): Int = toByteArray(Charsets.UTF_8).size

  private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

  private companion object {
    const val PART_SUFFIX = ".part"
    const val FINGERPRINT_ALGORITHM = "SHA-256"
    const val BUFFER_BYTES = 64 * 1024

    // The file-system limit of 255 bytes, less room for PART_SUFFIX.
    const val MAX_NAME_BYTES = 255 - PART_SUFFIX.length
    const val MAX_EXTENSION_BYTES = 32
  }
}
