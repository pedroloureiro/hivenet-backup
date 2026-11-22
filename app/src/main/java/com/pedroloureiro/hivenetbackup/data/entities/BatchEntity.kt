package com.pedroloureiro.hivenetbackup.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "batches")
data class BatchEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  // Epoch milliseconds.
  val createdAt: Long,
)
