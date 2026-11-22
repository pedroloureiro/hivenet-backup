package com.pedroloureiro.hivenetbackup.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pedroloureiro.hivenetbackup.data.BatchFileRecord.FailureReason
import com.pedroloureiro.hivenetbackup.data.BatchFileRecord.State
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BatchDatabaseManagerTest {
  private lateinit var database: BackupDatabase
  private lateinit var manager: BatchDatabaseManager

  @Before
  fun setUp() {
    database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), BackupDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    manager = BatchDatabaseManager(database)
  }

  @After
  fun tearDown() {
    database.close()
  }

  @Test
  fun `creates a batch with every file waiting in picked order`() = runTest {
    val batch = manager.createBatch(createdAtEpochMs = 1_000, records = listOf(record("b.txt"), record("a.txt"), record("c.txt")))

    assertEquals(listOf("b.txt", "a.txt", "c.txt"), batch.map { it.displayName })
    assertEquals(listOf(0, 1, 2), batch.map { it.position })
    assertTrue(batch.all { it.batchId == 1L && it.batchCreatedAtEpochMs == 1_000L && it.state == State.WAITING })
    assertEquals(batch, manager.observeLatest().first())
  }

  @Test
  fun `gives each new batch the next batch id`() = runTest {
    manager.createBatch(createdAtEpochMs = 1_000, records = listOf(record("a.txt")))

    val second = manager.createBatch(createdAtEpochMs = 2_000, records = listOf(record("b.txt")))

    assertEquals(2L, second.single().batchId)
  }

  @Test
  fun `refuses an empty batch`() = runTest {
    val failure = runCatching { manager.createBatch(createdAtEpochMs = 1_000, records = emptyList()) }.exceptionOrNull()

    assertTrue("Expected IllegalArgumentException, got $failure", failure is IllegalArgumentException)
  }

  @Test
  fun `records nothing when one file of a batch cannot be stored`() = runTest {
    val clashingRecords = listOf(record("a.txt").copy(id = 7), record("b.txt").copy(id = 7))

    val failure = runCatching { manager.createBatch(createdAtEpochMs = 1_000, records = clashingRecords) }.exceptionOrNull()

    assertTrue(failure != null)
    assertEquals(emptyList<BatchFileRecord>(), manager.observeLatest().first())
  }

  @Test
  fun `marks a waiting file copying`() = runTest {
    val recordId = manager.createBatch(createdAtEpochMs = 1_000, records = listOf(record("a.txt"))).single().id

    manager.markCopying(recordId)

    assertEquals(State.COPYING, latestRecord().state)
  }

  @Test
  fun `refuses to mark copying a file that is not waiting`() = runTest {
    val recordId = manager.createBatch(createdAtEpochMs = 1_000, records = listOf(record("a.txt"))).single().id
    manager.markCopying(recordId)

    assertRefused { manager.markCopying(recordId) }

    manager.recordOutcome(backedUp(recordId))

    assertRefused { manager.markCopying(recordId) }
    assertEquals(State.BACKED_UP, latestRecord().state)
  }

  @Test
  fun `records the outcome of a copying file`() = runTest {
    val recordId = manager.createBatch(createdAtEpochMs = 1_000, records = listOf(record("a.txt"))).single().id
    manager.markCopying(recordId)

    manager.recordOutcome(backedUp(recordId))

    val recorded = latestRecord()
    assertEquals(State.BACKED_UP, recorded.state)
    assertEquals("1/1/a.txt", recorded.relativePath)
    assertEquals("ab12", recorded.fingerprint)
    assertEquals(4L, recorded.storedSize)
    assertNull(recorded.failureReason)
  }

  @Test
  fun `records a failure with its reason and detail`() = runTest {
    val recordId = manager.createBatch(createdAtEpochMs = 1_000, records = listOf(record("a.txt"))).single().id
    manager.markCopying(recordId)

    manager.recordOutcome(
      BatchFileOutcome(
        id = recordId,
        state = State.FAILED,
        failureReason = FailureReason.UNEXPECTED_ERROR,
        failureDetail = "EIO",
      ),
    )

    val recorded = latestRecord()
    assertEquals(State.FAILED, recorded.state)
    assertEquals(FailureReason.UNEXPECTED_ERROR, recorded.failureReason)
    assertEquals("EIO", recorded.failureDetail)
    assertNull(recorded.relativePath)
  }

  @Test
  fun `refuses an outcome for a file that is not copying yet`() = runTest {
    val recordId = manager.createBatch(createdAtEpochMs = 1_000, records = listOf(record("a.txt"))).single().id

    assertRefused { manager.recordOutcome(backedUp(recordId)) }

    assertEquals(State.WAITING, latestRecord().state)
  }

  @Test
  fun `never changes an outcome once recorded`() = runTest {
    val recordId = manager.createBatch(createdAtEpochMs = 1_000, records = listOf(record("a.txt"))).single().id
    manager.markCopying(recordId)
    manager.recordOutcome(backedUp(recordId))

    assertRefused {
      manager.recordOutcome(
        BatchFileOutcome(id = recordId, state = State.FAILED, failureReason = FailureReason.NOT_ENOUGH_SPACE),
      )
    }

    assertEquals(State.BACKED_UP, latestRecord().state)
    assertEquals("ab12", latestRecord().fingerprint)
  }

  @Test
  fun `refuses a state that is not final as an outcome`() = runTest {
    val recordId = manager.createBatch(createdAtEpochMs = 1_000, records = listOf(record("a.txt"))).single().id
    manager.markCopying(recordId)

    assertRefused { manager.recordOutcome(BatchFileOutcome(id = recordId, state = State.WAITING)) }
    assertRefused { manager.recordOutcome(BatchFileOutcome(id = recordId, state = State.COPYING)) }

    assertEquals(State.COPYING, latestRecord().state)
  }

  @Test
  fun `refuses removed as an outcome`() = runTest {
    val recordId = manager.createBatch(createdAtEpochMs = 1_000, records = listOf(record("a.txt"))).single().id
    manager.markCopying(recordId)

    assertRefused { manager.recordOutcome(BatchFileOutcome(id = recordId, state = State.REMOVED)) }

    assertEquals(State.COPYING, latestRecord().state)
  }

  @Test
  fun `marks a backed up file removed and clears its copy`() = runTest {
    val recordId = manager.createBatch(createdAtEpochMs = 1_000, records = listOf(record("a.txt"))).single().id
    backUp(recordId)

    manager.markRemoved(recordId)

    val recorded = latestRecord()
    assertEquals(State.REMOVED, recorded.state)
    assertNull(recorded.relativePath)
    assertNull(recorded.fingerprint)
    assertNull(recorded.storedSize)
  }

  @Test
  fun `refuses to mark removed a file that is not backed up`() = runTest {
    val recordId = manager.createBatch(createdAtEpochMs = 1_000, records = listOf(record("a.txt"))).single().id

    assertRefused { manager.markRemoved(recordId) }

    manager.markCopying(recordId)

    assertRefused { manager.markRemoved(recordId) }

    manager.recordOutcome(BatchFileOutcome(id = recordId, state = State.FAILED, failureReason = FailureReason.NOT_ENOUGH_SPACE))

    assertRefused { manager.markRemoved(recordId) }
    assertEquals(State.FAILED, latestRecord().state)
  }

  @Test
  fun `refuses to mark removed a file already removed`() = runTest {
    val recordId = manager.createBatch(createdAtEpochMs = 1_000, records = listOf(record("a.txt"))).single().id
    backUp(recordId)
    manager.markRemoved(recordId)

    assertRefused { manager.markRemoved(recordId) }
  }

  @Test
  fun `lists the removed files`() = runTest {
    val (kept, removed) = manager.createBatch(createdAtEpochMs = 1_000, records = listOf(record("a.txt"), record("b.txt"))).map { it.id }
    backUp(kept)
    backUp(removed)
    manager.markRemoved(removed)

    assertEquals(listOf(removed), manager.removedRecords().map { it.id })
  }

  @Test
  fun `observes every backed up file, newest batch first and in picked order within one`() = runTest {
    val (olderBackedUp, olderFailed) = manager.createBatch(createdAtEpochMs = 1_000, records = listOf(record("a.txt"), record("b.txt"))).map { it.id }
    backUp(olderBackedUp)
    manager.markCopying(olderFailed)
    manager.recordOutcome(BatchFileOutcome(id = olderFailed, state = State.FAILED, failureReason = FailureReason.NOT_ENOUGH_SPACE))
    val newer = manager.createBatch(createdAtEpochMs = 2_000, records = listOf(record("d.txt"), record("c.txt"), record("e.txt"))).map { it.id }
    newer.forEach { backUp(it) }
    manager.markRemoved(newer.last())

    assertEquals(listOf("d.txt", "c.txt", "a.txt"), manager.observeBackedUp().first().map { it.displayName })
  }

  @Test
  fun `settles abandoned files in every batch and leaves outcomes alone`() = runTest {
    val older = manager.createBatch(createdAtEpochMs = 1_000, records = listOf(record("old.txt"))).single()
    val latest = manager.createBatch(createdAtEpochMs = 2_000, records = listOf(record("done.txt"), record("busy.txt"), record("next.txt")))
    val (done, busy, _) = latest.map { it.id }
    manager.markCopying(done)
    manager.recordOutcome(backedUp(done))
    manager.markCopying(busy)

    manager.settleAbandoned()

    assertEquals(
      listOf(State.BACKED_UP, State.INTERRUPTED, State.NOT_STARTED),
      manager.observeLatest().first().map { it.state },
    )
    assertEquals(State.NOT_STARTED, database.backupDao().batch(older.batchId).single().state)
  }

  @Test
  fun `returns only the files it marks interrupted when settling`() = runTest {
    val older = manager.createBatch(createdAtEpochMs = 1_000, records = listOf(record("old.txt"))).single()
    manager.markCopying(older.id)
    manager.settleAbandoned()
    val latest = manager.createBatch(createdAtEpochMs = 2_000, records = listOf(record("busy.txt"), record("next.txt")))
    manager.markCopying(latest.first().id)

    val interrupted = manager.settleAbandoned()

    assertEquals(listOf(latest.first().id), interrupted.map { it.id })
    assertEquals(State.INTERRUPTED, interrupted.single().state)
    assertEquals(2L, interrupted.single().batchId)
  }

  @Test
  fun `observes nothing before any batch exists`() = runTest {
    assertEquals(emptyList<BatchFileRecord>(), manager.observeLatest().first())
  }

  @Test
  fun `observes the most recent batch with its files in picked order`() = runTest {
    manager.createBatch(createdAtEpochMs = 5_000, records = listOf(record("old.txt")))
    manager.createBatch(createdAtEpochMs = 1_000, records = listOf(record("z.txt"), record("y.txt"), record("x.txt")))

    val latest = manager.observeLatest().first()

    assertEquals(listOf("z.txt", "y.txt", "x.txt"), latest.map { it.displayName })
    assertTrue(latest.all { it.batchCreatedAtEpochMs == 1_000L })
  }

  private suspend fun latestRecord(): BatchFileRecord = manager.observeLatest().first().single()

  private suspend fun backUp(recordId: Long) {
    manager.markCopying(recordId)
    manager.recordOutcome(backedUp(recordId))
  }

  private suspend fun assertRefused(change: suspend () -> Unit) {
    val failure = runCatching { change() }.exceptionOrNull()
    assertTrue("Expected IllegalStateException, got $failure", failure is IllegalStateException)
  }

  private fun record(name: String) = BatchFileRecord(
    sourceUri = "content://test/$name",
    displayName = name,
    mimeType = "text/plain",
    reportedSize = 4,
  )

  private fun backedUp(recordId: Long) = BatchFileOutcome(
    id = recordId,
    state = State.BACKED_UP,
    storedSize = 4,
    fingerprint = "ab12",
    relativePath = "1/1/a.txt",
  )
}
