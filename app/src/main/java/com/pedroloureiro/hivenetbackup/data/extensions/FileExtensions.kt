package com.pedroloureiro.hivenetbackup.data.extensions

import java.io.File
import java.security.MessageDigest

fun File.digest(algorithm: String): ByteArray {
  val digest = MessageDigest.getInstance(algorithm)
  inputStream().use { input ->
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
      val count = input.read(buffer)
      if (count == -1) break
      digest.update(buffer, 0, count)
    }
  }
  return digest.digest()
}
