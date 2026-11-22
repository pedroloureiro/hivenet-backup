package com.pedroloureiro.hivenetbackup.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module

/** Batches run here, never in viewModelScope: they outlive the screen but not the process (ADR-0002). */
val appModule = module {
  single<CoroutineScope> { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
}
