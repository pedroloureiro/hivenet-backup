package com.pedroloureiro.hivenetbackup.data.mappers

import com.pedroloureiro.hivenetbackup.extensions.cutToUtf8Bytes
import com.pedroloureiro.hivenetbackup.extensions.utf8Bytes

private const val MAX_EXTENSION_BYTES = 32

fun String.toBackupCopyName(maxBytes: Int): String {
  val name = replace('/', '_').replace('\u0000', '_')
  if (name == "" || name == "." || name == "..") return "_"
  if (name.utf8Bytes() <= maxBytes) return name
  val dot = name.lastIndexOf('.')
  val extension = if (dot > 0) name.substring(dot) else ""
  if (extension.isEmpty() || extension.utf8Bytes() > MAX_EXTENSION_BYTES) return name.cutToUtf8Bytes(maxBytes)
  return name.substring(0, dot).cutToUtf8Bytes(maxBytes - extension.utf8Bytes()) + extension
}
