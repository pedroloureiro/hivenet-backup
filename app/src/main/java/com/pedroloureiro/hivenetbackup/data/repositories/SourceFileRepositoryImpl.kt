package com.pedroloureiro.hivenetbackup.data.repositories

import android.content.ContentResolver
import android.database.Cursor
import android.provider.OpenableColumns
import androidx.core.net.toUri
import com.pedroloureiro.hivenetbackup.domain.models.SourceFile
import com.pedroloureiro.hivenetbackup.domain.repositories.SourceFileRepository
import java.io.FileNotFoundException
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SourceFileRepositoryImpl(private val contentResolver: ContentResolver) : SourceFileRepository {
  override suspend fun describe(uri: String): SourceFile = withContext(Dispatchers.IO) {
    val contentUri = uri.toUri()
    contentResolver.query(contentUri, OPENABLE_COLUMNS, null, null, null).use { cursor ->
      val row = cursor?.takeIf { it.moveToFirst() }
      SourceFile(
        uri = uri,
        displayName = row?.stringOrNull(OpenableColumns.DISPLAY_NAME) ?: contentUri.lastPathSegment ?: UNNAMED,
        mimeType = contentResolver.getType(contentUri) ?: FALLBACK_MIME_TYPE,
        reportedSize = row?.longOrNull(OpenableColumns.SIZE)?.takeIf { it >= 0 },
      )
    }
  }

  // openInputStream returns null only when the provider crashed: the source is gone for this Batch.
  override fun open(uri: String): InputStream =
    contentResolver.openInputStream(uri.toUri()) ?: throw FileNotFoundException("No stream for $uri")

  private fun Cursor.stringOrNull(column: String): String? =
    getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let(::getString)

  private fun Cursor.longOrNull(column: String): Long? =
    getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let(::getLong)

  private companion object {
    val OPENABLE_COLUMNS = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
    const val UNNAMED = "unnamed"
    const val FALLBACK_MIME_TYPE = "application/octet-stream"
  }
}
