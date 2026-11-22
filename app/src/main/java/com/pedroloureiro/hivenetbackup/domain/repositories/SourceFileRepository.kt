package com.pedroloureiro.hivenetbackup.domain.repositories

import com.pedroloureiro.hivenetbackup.domain.models.SourceFile
import java.io.InputStream

interface SourceFileRepository {
  suspend fun describe(uri: String): SourceFile

  fun open(uri: String): InputStream
}
