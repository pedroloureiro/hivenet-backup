package com.pedroloureiro.hivenetbackup.domain.usecases

import com.pedroloureiro.hivenetbackup.domain.models.BatchFile
import com.pedroloureiro.hivenetbackup.domain.repositories.BackupCopyRepository
import com.pedroloureiro.hivenetbackup.domain.repositories.BatchRepository

class RemoveBackupCopy(private val batchRepository: BatchRepository, private val backupCopyRepository: BackupCopyRepository) {
  // Recording first: a stop in between leaves a folder the next start deletes, never a Backed up row without its copy.
  suspend operator fun invoke(batchFile: BatchFile) {
    batchRepository.markRemoved(batchFile.id)
    backupCopyRepository.deleteFolderOf(batchFile.batchId, batchFile.id)
  }
}
