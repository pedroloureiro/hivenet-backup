package com.pedroloureiro.hivenetbackup.domain.usecases

import com.pedroloureiro.hivenetbackup.domain.BackupCopyFailedException
import com.pedroloureiro.hivenetbackup.domain.models.BackupCopy
import com.pedroloureiro.hivenetbackup.domain.models.BatchFile
import com.pedroloureiro.hivenetbackup.domain.models.FailureReason
import com.pedroloureiro.hivenetbackup.domain.models.FileState
import com.pedroloureiro.hivenetbackup.domain.models.SourceFile
import com.pedroloureiro.hivenetbackup.domain.repositories.BackupCopyRepository
import com.pedroloureiro.hivenetbackup.domain.repositories.BatchRepository
import com.pedroloureiro.hivenetbackup.domain.repositories.SourceFileRepository
import java.io.FileNotFoundException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

class RunBatch(
  private val batchRepository: BatchRepository,
  private val sourceFileRepository: SourceFileRepository,
  private val backupCopyRepository: BackupCopyRepository,
) {
  // Recording stays outside backUp's catch: a refused or failed record stops the Batch, and the next start settles it.
  suspend operator fun invoke(sourceFiles: List<SourceFile>) {
    val batch = batchRepository.create(sourceFiles)
    batch.files.forEach { batchFile ->
      batchRepository.markCopying(batchFile.id)
      batchRepository.recordOutcome(batchFile.id, backUp(batch.id, batchFile))
    }
  }

  // On failure, write has already deleted the file's folder.
  private suspend fun backUp(batchId: Long, batchFile: BatchFile): FileState = try {
    if (fitsInBackupStore(batchFile.sourceFile)) {
      FileState.BackedUp(copy(batchId, batchFile))
    } else {
      FileState.Failed(FailureReason.NotEnoughSpace)
    }
  } catch (cancellation: CancellationException) {
    throw cancellation
  } catch (exception: Exception) {
    FileState.Failed(exception.toFailureReason())
  }

  // An unknown size can't be checked up front; ENOSPC while writing still catches it.
  private fun fitsInBackupStore(sourceFile: SourceFile): Boolean {
    val reportedSize = sourceFile.reportedSize ?: return true
    return reportedSize <= backupCopyRepository.freeSpace() - SPACE_MARGIN_BYTES
  }

  private suspend fun copy(batchId: Long, batchFile: BatchFile): BackupCopy =
    sourceFileRepository.open(batchFile.sourceFile.uri).use { source -> backupCopyRepository.write(batchId, batchFile, source) }

  // Source file exceptions arrive unchanged; Backup store failures arrive as BackupCopyFailedException.
  private fun Exception.toFailureReason(): FailureReason = when (this) {
    is BackupCopyFailedException -> reason
    // Downloads reports a deleted file as SecurityException.
    is FileNotFoundException, is SecurityException -> FailureReason.SourceNoLongerAvailable
    is IOException -> FailureReason.CouldNotReadSource
    else -> FailureReason.UnexpectedError(toString())
  }

  private companion object {
    const val SPACE_MARGIN_BYTES = 50L * 1024 * 1024
  }
}
