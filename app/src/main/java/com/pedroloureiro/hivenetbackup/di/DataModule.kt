package com.pedroloureiro.hivenetbackup.di

import androidx.room.Room
import com.pedroloureiro.hivenetbackup.data.BackupDatabase
import com.pedroloureiro.hivenetbackup.data.BatchDatabaseManager
import com.pedroloureiro.hivenetbackup.data.repositories.BackupCopyRepositoryImpl
import com.pedroloureiro.hivenetbackup.data.repositories.BatchRepositoryImpl
import com.pedroloureiro.hivenetbackup.data.repositories.SourceFileRepositoryImpl
import com.pedroloureiro.hivenetbackup.domain.repositories.BackupCopyRepository
import com.pedroloureiro.hivenetbackup.domain.repositories.BatchRepository
import com.pedroloureiro.hivenetbackup.domain.repositories.SourceFileRepository
import java.io.File
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val dataModule = module {
  single { Room.databaseBuilder(androidContext(), BackupDatabase::class.java, "batches.db").build() }
  singleOf(::BatchDatabaseManager)
  single<BatchRepository> { BatchRepositoryImpl(get()) }
  single<SourceFileRepository> { SourceFileRepositoryImpl(androidContext().contentResolver) }
  // The Backup store; the FileProvider paths in res/xml/backup_copy_paths.xml expose this folder only.
  single { File(androidContext().filesDir, "backups") }
  single<BackupCopyRepository> { BackupCopyRepositoryImpl(get()) }
}
