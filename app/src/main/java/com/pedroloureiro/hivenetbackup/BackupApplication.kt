package com.pedroloureiro.hivenetbackup

import android.app.Application
import com.pedroloureiro.hivenetbackup.di.appModule
import com.pedroloureiro.hivenetbackup.di.dataModule
import com.pedroloureiro.hivenetbackup.di.domainModule
import com.pedroloureiro.hivenetbackup.di.uiModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class BackupApplication : Application() {
  override fun onCreate() {
    super.onCreate()
    startKoin {
      androidContext(this@BackupApplication)
      modules(appModule, dataModule, domainModule, uiModule)
    }
  }
}
