package com.pedroloureiro.hivenetbackup.data.mappers

import android.database.Cursor
import android.provider.OpenableColumns
import androidx.core.net.toUri
import com.pedroloureiro.hivenetbackup.domain.models.SourceFile

private const val UNNAMED = "unnamed"
private const val FALLBACK_MIME_TYPE = "application/octet-stream"

// The provider's answer to a query for OpenableColumns; null or empty when it has nothing to say.
fun Cursor?.toSourceFile(uri: String, mimeType: String?): SourceFile {
  val row = this?.takeIf { it.moveToFirst() }
  return SourceFile(
    uri = uri,
    displayName = row?.stringOrNull(OpenableColumns.DISPLAY_NAME) ?: uri.toUri().lastPathSegment ?: UNNAMED,
    mimeType = mimeType ?: FALLBACK_MIME_TYPE,
    reportedSize = row?.longOrNull(OpenableColumns.SIZE)?.takeIf { it >= 0 },
  )
}

private fun Cursor.stringOrNull(column: String): String? =
  getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let(::getString)

private fun Cursor.longOrNull(column: String): Long? =
  getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let(::getLong)
