package com.pedroloureiro.hivenetbackup.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pedroloureiro.hivenetbackup.data.entities.BatchFileEntity
import com.pedroloureiro.hivenetbackup.data.entities.BatchFileEntity.FailureReason
import com.pedroloureiro.hivenetbackup.data.entities.BatchFileEntity.State
import com.pedroloureiro.hivenetbackup.data.entities.BatchFileOutcome
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
    val batch = manager.createBatch(createdAt = 1_000, files = listOf(file("b.txt"), file("a.txt"), file("c.txt")))

    assertEquals(1_000L, batch.batch.createdAt)
    assertEquals(listOf("b.txt", "a.txt", "c.txt"), batch.files.map { it.displayName })
    assertEquals(listOf(0, 1, 2), batch.files.map { it.position })
    assertTrue(batch.files.all { it.batchId == batch.batch.id && it.state == State.WAITING })
    assertEquals(batch, manager.observeLatest().first())
  }

  @Test
  fun `records nothing when one file of a batch cannot be stored`() = runTest {
    val clashingFiles = listOf(file("a.txt").copy(id = 7), file("b.txt").copy(id = 7))

    val failure = runCatching { manager.createBatch(createdAt = 1_000, files = clashingFiles) }.exceptionOrNull()

    assertTrue(failure != null)
    assertNull(manager.observeLatest().first())
  }

  @Test
  fun `marks a waiting file copying`() = runTest {
    val fileId = manager.createBatch(createdAt = 1_000, files = listOf(file("a.txt"))).files.single().id

    manager.markCopying(fileId)

    assertEquals(State.COPYING, latestFile().state)
  }

  @Test
  fun `refuses to mark copying a file that is not waiting`() = runTest {
    val fileId = manager.createBatch(createdAt = 1_000, files = listOf(file("a.txt"))).files.single().id
    manager.markCopying(fileId)

    assertRefused { manager.markCopying(fileId) }

    manager.recordOutcome(backedUp(fileId))

    assertRefused { manager.markCopying(fileId) }
    assertEquals(State.BACKED_UP, latestFile().state)
  }

  @Test
  fun `records the outcome of a copying file`() = runTest {
    val fileId = manager.createBatch(createdAt = 1_000, files = listOf(file("a.txt"))).files.single().id
    manager.markCopying(fileId)

    manager.recordOutcome(backedUp(fileId))

    val recorded = latestFile()
    assertEquals(State.BACKED_UP, recorded.state)
    assertEquals("1/1/a.txt", recorded.relativePath)
    assertEquals("ab12", recorded.fingerprint)
    assertEquals(4L, recorded.storedSize)
    assertNull(recorded.failureReason)
  }

  @Test
  fun `records a failure with its reason and detail`() = runTest {
    val fileId = manager.createBatch(createdAt = 1_000, files = listOf(file("a.txt"))).files.single().id
    manager.markCopying(fileId)

    manager.recordOutcome(
      BatchFileOutcome(
        id = fileId,
        state = State.FAILED,
        failureReason = FailureReason.UNEXPECTED_ERROR,
        failureDetail = "EIO",
      ),
    )

    val recorded = latestFile()
    assertEquals(State.FAILED, recorded.state)
    assertEquals(FailureReason.UNEXPECTED_ERROR, recorded.failureReason)
    assertEquals("EIO", recorded.failureDetail)
    assertNull(recorded.relativePath)
  }

  @Test
  fun `refuses an outcome for a file that is not copying yet`() = runTest {
    val fileId = manager.createBatch(createdAt = 1_000, files = listOf(file("a.txt"))).files.single().id

    assertRefused { manager.recordOutcome(backedUp(fileId)) }

    assertEquals(State.WAITING, latestFile().state)
  }

  @Test
  fun `never changes an outcome once recorded`() = runTest {
    val fileId = manager.createBatch(createdAt = 1_000, files = listOf(file("a.txt"))).files.single().id
    manager.markCopying(fileId)
    manager.recordOutcome(backedUp(fileId))

    assertRefused {
      manager.recordOutcome(
        BatchFileOutcome(id = fileId, state = State.FAILED, failureReason = FailureReason.NOT_ENOUGH_SPACE),
      )
    }

    assertEquals(State.BACKED_UP, latestFile().state)
    assertEquals("ab12", latestFile().fingerprint)
  }

  @Test
  fun `refuses a state that is not final as an outcome`() = runTest {
    val fileId = manager.createBatch(createdAt = 1_000, files = listOf(file("a.txt"))).files.single().id
    manager.markCopying(fileId)

    assertRefused { manager.recordOutcome(BatchFileOutcome(id = fileId, state = State.WAITING)) }
    assertRefused { manager.recordOutcome(BatchFileOutcome(id = fileId, state = State.COPYING)) }

    assertEquals(State.COPYING, latestFile().state)
  }

  @Test
  fun `settles abandoned files in every batch and leaves outcomes alone`() = runTest {
    val older = manager.createBatch(createdAt = 1_000, files = listOf(file("old.txt")))
    val latest = manager.createBatch(createdAt = 2_000, files = listOf(file("done.txt"), file("busy.txt"), file("next.txt")))
    val (done, busy, _) = latest.files.map { it.id }
    manager.markCopying(done)
    manager.recordOutcome(backedUp(done))
    manager.markCopying(busy)

    manager.settleAbandoned()

    assertEquals(
      listOf(State.BACKED_UP, State.INTERRUPTED, State.NOT_STARTED),
      manager.observeLatest().first()!!.files.map { it.state },
    )
    assertEquals(State.NOT_STARTED, database.backupDao().batchWithFiles(older.batch.id).files.single().state)
  }

  @Test
  fun `observes nothing before any batch exists`() = runTest {
    assertNull(manager.observeLatest().first())
  }

  @Test
  fun `observes the most recent batch with its files in picked order`() = runTest {
    manager.createBatch(createdAt = 5_000, files = listOf(file("old.txt")))
    manager.createBatch(createdAt = 1_000, files = listOf(file("z.txt"), file("y.txt"), file("x.txt")))

    val latest = manager.observeLatest().first()!!

    assertEquals(1_000L, latest.batch.createdAt)
    assertEquals(listOf("z.txt", "y.txt", "x.txt"), latest.files.map { it.displayName })
  }

  private suspend fun latestFile(): BatchFileEntity = manager.observeLatest().first()!!.files.single()

  private suspend fun assertRefused(change: suspend () -> Unit) {
    val failure = runCatching { change() }.exceptionOrNull()
    assertTrue("Expected IllegalStateException, got $failure", failure is IllegalStateException)
  }

  private fun file(name: String) = BatchFileEntity(
    sourceUri = "content://test/$name",
    displayName = name,
    mimeType = "text/plain",
    reportedSize = 4,
  )

  private fun backedUp(fileId: Long) = BatchFileOutcome(
    id = fileId,
    state = State.BACKED_UP,
    storedSize = 4,
    fingerprint = "ab12",
    relativePath = "1/1/a.txt",
  )
}
