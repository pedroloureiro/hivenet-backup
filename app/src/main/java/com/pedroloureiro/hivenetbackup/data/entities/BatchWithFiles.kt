package com.pedroloureiro.hivenetbackup.data.entities

import androidx.room.Embedded
import androidx.room.Relation

data class BatchWithFiles(
  @Embedded val batch: BatchEntity,
  @Relation(parentColumn = "id", entityColumn = "batchId") val files: List<BatchFileEntity>,
)
