package com.pedroloureiro.hivenetbackup.domain.usecases

import com.pedroloureiro.hivenetbackup.domain.repositories.BackupCopyRepository
import com.pedroloureiro.hivenetbackup.domain.repositories.BatchRepository

class SettleAbandonedBatch(private val batchRepository: BatchRepository, private val backupCopyRepository: BackupCopyRepository) {
  suspend operator fun invoke() {
    val interruptedFiles = batchRepository.settleAbandoned()
    backupCopyRepository.deletePartFiles()
    // The app may have stopped after the rename but before recording Backed up, or after recording Removed but before
    // deleting the copy. Neither state keeps anything.
    (interruptedFiles + batchRepository.removedFiles()).forEach { backupCopyRepository.deleteFolderOf(it.batchId, it.id) }
  }
}
