package com.pedroloureiro.hivenetbackup.data.repositories

import com.pedroloureiro.hivenetbackup.domain.BackupCopyFailedException
import com.pedroloureiro.hivenetbackup.domain.models.BackupCopy
import com.pedroloureiro.hivenetbackup.domain.models.BatchFile
import com.pedroloureiro.hivenetbackup.domain.models.FailureReason
import com.pedroloureiro.hivenetbackup.domain.models.FileState
import com.pedroloureiro.hivenetbackup.domain.models.SourceFile
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupCopyRepositoryImplTest {
  @get:Rule
  val temporaryFolder = TemporaryFolder()

  private lateinit var backupStore: File
  private lateinit var repository: BackupCopyRepositoryImpl

  @Before
  fun setUp() {
    backupStore = File(temporaryFolder.root, "backups")
    repository = BackupCopyRepositoryImpl(backupStore)
  }

  @Test
  fun `writes a byte-identical Backup copy under its final name`() = runTest {
    val bytes = ByteArray(200_000) { it.toByte() }

    val backupCopy = repository.write(batchId = 1, file = batchFile(id = 7, "photo.jpg", bytes.size.toLong()), source = ByteArrayInputStream(bytes))

    assertEquals("1/7/photo.jpg", backupCopy.relativePath)
    assertEquals(bytes.size.toLong(), backupCopy.storedSize)
    assertArrayEquals(bytes, File(backupStore, backupCopy.relativePath).readBytes())
    assertEquals(listOf("photo.jpg"), File(backupStore, "1/7").list()?.toList())
  }

  @Test
  fun `records the SHA-256 of the content as the Fingerprint`() = runTest {
    val backupCopy = write("hello.txt", "hello".toByteArray())

    assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", backupCopy.fingerprint)
  }

  @Test
  fun `writes an empty Source file as an empty Backup copy`() = runTest {
    val backupCopy = write("empty.txt", ByteArray(0))

    assertEquals(0L, backupCopy.storedSize)
    assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", backupCopy.fingerprint)
    assertTrue(File(backupStore, backupCopy.relativePath).isFile)
  }

  @Test
  fun `skips the byte count check when the size is unknown`() = runTest {
    val backupCopy = repository.write(batchId = 1, file = batchFile(id = 7, "a.txt", reportedSize = null), source = "abc".byteInputStream())

    assertEquals(3L, backupCopy.storedSize)
  }

  @Test
  fun `fails as Copy didn't match source and keeps nothing when the byte count differs`() = runTest {
    val failure = runCatching {
      repository.write(batchId = 1, file = batchFile(id = 7, "a.txt", reportedSize = 4), source = "abc".byteInputStream())
    }.exceptionOrNull()

    assertEquals(FailureReason.CopyDidNotMatchSource, (failure as BackupCopyFailedException).reason)
    assertFalse(File(backupStore, "1/7").exists())
  }

  @Test
  fun `passes a source read error on unchanged and keeps nothing`() = runTest {
    val readError = IOException("Connection reset")
    val source = BreaksAfter(ByteArray(10_000), readError)

    val failure = runCatching { repository.write(batchId = 1, file = batchFile(id = 7, "a.txt", reportedSize = null), source = source) }.exceptionOrNull()

    // Stack-trace recovery may copy the exception across withContext, so compare type and message, not identity.
    assertEquals(readError.toString(), failure.toString())
    assertFalse(File(backupStore, "1/7").exists())
  }

  @Test
  fun `reports a write-side error as Unexpected error, not as a source exception`() = runTest {
    backupStore.mkdirs()
    File(backupStore, "1").writeText("a file where the Batch folder should be")

    val failure = runCatching { write("a.txt", "abc".toByteArray()) }.exceptionOrNull()

    assertTrue("Expected BackupCopyFailedException, got $failure", failure is BackupCopyFailedException)
    assertTrue((failure as BackupCopyFailedException).reason is FailureReason.UnexpectedError)
  }

  @Test
  fun `keeps same-named Source files apart`() = runTest {
    val first = repository.write(batchId = 1, file = batchFile(id = 7, "notes.txt"), source = "first".byteInputStream())
    val second = repository.write(batchId = 1, file = batchFile(id = 8, "notes.txt"), source = "second".byteInputStream())

    assertEquals("first", File(backupStore, first.relativePath).readText())
    assertEquals("second", File(backupStore, second.relativePath).readText())
  }

  @Test
  fun `replaces path separators and NUL in the name`() = runTest {
    assertEquals("1/7/a_b_c.txt", write("a/b\u0000c.txt").relativePath)
  }

  @Test
  fun `turns an empty or dot-only name into an underscore`() = runTest {
    assertEquals("1/7/_", write("").relativePath)
    assertEquals("1/8/_", repository.write(batchId = 1, file = batchFile(id = 8, ".."), source = ByteArrayInputStream(ByteArray(0))).relativePath)
  }

  @Test
  fun `cuts a long name at a character boundary and keeps its extension`() = runTest {
    // Each emoji is 4 UTF-8 bytes: 61 of them plus ".txt" is 248 bytes, 62 would pass 250.
    val backupCopy = write("😀".repeat(100) + ".txt")

    assertEquals("1/7/" + "😀".repeat(61) + ".txt", backupCopy.relativePath)
  }

  @Test
  fun `cuts a long name whole when its extension is too long to keep`() = runTest {
    val backupCopy = write("a." + "b".repeat(300))

    assertEquals("1/7/a." + "b".repeat(248), backupCopy.relativePath)
  }

  @Test
  fun `reports free space before the first Backup copy exists`() {
    assertTrue(repository.freeSpace() > 0)
  }

  @Test
  fun `deletes part files and keeps Backup copies`() = runTest {
    val backupCopy = write("kept.txt", "kept".toByteArray())
    val partFile = File(File(backupStore, "1/8").apply { mkdirs() }, "half.txt.part").apply { writeText("half") }

    repository.deletePartFiles()

    assertFalse(partFile.exists())
    assertTrue(File(backupStore, backupCopy.relativePath).exists())
  }

  @Test
  fun `deletes only the folder of the given file`() = runTest {
    write("a.txt", "a".toByteArray())
    val sameBatch = repository.write(batchId = 1, file = batchFile(id = 8, "b.txt"), source = "b".byteInputStream())
    val otherBatch = repository.write(batchId = 2, file = batchFile(id = 7, "c.txt"), source = "c".byteInputStream())

    repository.deleteFolderOf(batchId = 1, batchFileId = 7)

    assertFalse(File(backupStore, "1/7").exists())
    assertTrue(File(backupStore, sameBatch.relativePath).exists())
    assertTrue(File(backupStore, otherBatch.relativePath).exists())
  }

  private suspend fun write(displayName: String, bytes: ByteArray = ByteArray(0)): BackupCopy =
    repository.write(batchId = 1, file = batchFile(id = 7, displayName, bytes.size.toLong()), source = ByteArrayInputStream(bytes))

  private fun batchFile(id: Long, displayName: String, reportedSize: Long? = null) = BatchFile(
    id = id,
    batchId = 1,
    sourceFile = SourceFile(uri = "content://picked/$id", displayName = displayName, mimeType = "application/octet-stream", reportedSize = reportedSize),
    state = FileState.Copying,
  )

  // Serves the bytes, then throws instead of reporting the end of the stream.
  private class BreaksAfter(bytes: ByteArray, private val readError: IOException) : InputStream() {
    private val bytes = ByteArrayInputStream(bytes)

    override fun read(): Int = bytes.read().takeIf { it != -1 } ?: throw readError

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
      bytes.read(buffer, offset, length).takeIf { it != -1 } ?: throw readError
  }
}
