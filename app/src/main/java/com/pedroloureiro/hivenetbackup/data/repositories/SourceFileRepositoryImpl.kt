package com.pedroloureiro.hivenetbackup.data.repositories

import android.content.ContentResolver
import android.provider.OpenableColumns
import androidx.core.net.toUri
import com.pedroloureiro.hivenetbackup.data.mappers.toSourceFile
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
      cursor.toSourceFile(uri, mimeType = contentResolver.getType(contentUri))
    }
  }

  // openInputStream returns null only when the provider crashed: the source is gone for this Batch.
  override fun open(uri: String): InputStream =
    contentResolver.openInputStream(uri.toUri()) ?: throw FileNotFoundException("No stream for $uri")

  private companion object {
    val OPENABLE_COLUMNS = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
  }
}
