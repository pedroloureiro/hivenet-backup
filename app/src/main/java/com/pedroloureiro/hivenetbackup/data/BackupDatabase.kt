package com.pedroloureiro.hivenetbackup.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [BatchFileRecord::class], version = 1)
abstract class BackupDatabase : RoomDatabase() {
  abstract fun backupDao(): BackupDao
}
