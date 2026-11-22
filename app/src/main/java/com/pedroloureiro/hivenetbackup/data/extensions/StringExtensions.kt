package com.pedroloureiro.hivenetbackup.data.extensions

fun String.utf8Bytes(): Int = toByteArray(Charsets.UTF_8).size

// Never splits a character, surrogate pairs included.
fun String.cutToUtf8Bytes(maxBytes: Int): String {
  var end = 0
  var bytes = 0
  while (end < length) {
    val next = offsetByCodePoints(end, 1)
    bytes += substring(end, next).utf8Bytes()
    if (bytes > maxBytes) break
    end = next
  }
  return substring(0, end)
}
