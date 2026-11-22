package com.pedroloureiro.hivenetbackup.domain.repositories

import com.pedroloureiro.hivenetbackup.domain.models.SourceFile
import java.io.InputStream

class FakeSourceFileRepository : SourceFileRepository {
  private val sourceFiles = mutableMapOf<String, SourceFile>()
  private val openers = mutableMapOf<String, () -> InputStream>()

  fun add(displayName: String, content: String, reportedSize: Long? = content.length.toLong()): SourceFile =
    add(displayName, reportedSize) { content.byteInputStream() }

  // open runs each time the Source file is opened, so it can throw or hand back a breaking stream.
  fun add(displayName: String, reportedSize: Long?, open: () -> InputStream): SourceFile {
    val sourceFile = SourceFile(uri = "content://picked/$displayName", displayName = displayName, mimeType = "text/plain", reportedSize = reportedSize)
    sourceFiles[sourceFile.uri] = sourceFile
    openers[sourceFile.uri] = open
    return sourceFile
  }

  override suspend fun describe(uri: String): SourceFile = sourceFiles.getValue(uri)

  override fun open(uri: String): InputStream = openers.getValue(uri)()
}
