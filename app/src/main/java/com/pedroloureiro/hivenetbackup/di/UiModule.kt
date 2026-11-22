package com.pedroloureiro.hivenetbackup.di

import com.pedroloureiro.hivenetbackup.ui.BackupViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val uiModule = module {
  viewModelOf(::BackupViewModel)
}
