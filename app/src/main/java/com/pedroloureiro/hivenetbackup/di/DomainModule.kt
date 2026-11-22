package com.pedroloureiro.hivenetbackup.di

import com.pedroloureiro.hivenetbackup.domain.usecases.RemoveBackupCopy
import com.pedroloureiro.hivenetbackup.domain.usecases.RunBatch
import com.pedroloureiro.hivenetbackup.domain.usecases.SettleAbandonedBatch
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

val domainModule = module {
  factoryOf(::RunBatch)
  factoryOf(::SettleAbandonedBatch)
  factoryOf(::RemoveBackupCopy)
}
