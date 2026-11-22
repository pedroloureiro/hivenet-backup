package com.pedroloureiro.hivenetbackup.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.pedroloureiro.hivenetbackup.data.entities.BatchEntity
import com.pedroloureiro.hivenetbackup.data.entities.BatchFileEntity

@Database(entities = [BatchEntity::class, BatchFileEntity::class], version = 1)
abstract class BackupDatabase : RoomDatabase() {
  abstract fun backupDao(): BackupDao
}
