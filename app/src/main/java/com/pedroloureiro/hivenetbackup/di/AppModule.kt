package com.pedroloureiro.hivenetbackup.di

import com.pedroloureiro.hivenetbackup.domain.usecases.SettleAbandonedBatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.dsl.module

/** Batches run here, never in viewModelScope: they outlive the screen but not the process (ADR-0002). */
val appModule = module {
  single<CoroutineScope> { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
  // Settling starts with the process; BackupViewModel joins this Job before starting a Batch.
  single<Job>(createdAtStart = true) {
    val settleAbandonedBatch = get<SettleAbandonedBatch>()
    get<CoroutineScope>().launch { settleAbandonedBatch() }
  }
}
