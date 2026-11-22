package com.pedroloureiro.hivenetbackup.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "batch_file_records", indices = [Index("batchId")])
data class BatchFileRecord(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val batchId: Long = 0,
  val batchCreatedAtEpochMs: Long = 0,
  val position: Int = 0,
  val sourceUri: String,
  val displayName: String,
  val mimeType: String,
  val reportedSize: Long?,
  val state: State = State.WAITING,
  val failureReason: FailureReason? = null,
  val failureDetail: String? = null,
  val storedSize: Long? = null,
  val fingerprint: String? = null,
  val relativePath: String? = null,
) {
  enum class State { WAITING, COPYING, BACKED_UP, FAILED, INTERRUPTED, NOT_STARTED }

  enum class FailureReason {
    SOURCE_NO_LONGER_AVAILABLE,
    COULD_NOT_READ_SOURCE,
    NOT_ENOUGH_SPACE,
    COPY_DID_NOT_MATCH_SOURCE,
    UNEXPECTED_ERROR,
  }
}
