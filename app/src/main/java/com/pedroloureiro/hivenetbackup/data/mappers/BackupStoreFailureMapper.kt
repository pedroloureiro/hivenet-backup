package com.pedroloureiro.hivenetbackup.data.mappers

import android.system.ErrnoException
import android.system.OsConstants
import com.pedroloureiro.hivenetbackup.domain.models.FailureReason
import java.io.IOException

fun IOException.toBackupStoreFailureReason(): FailureReason =
  if ((cause as? ErrnoException)?.errno == OsConstants.ENOSPC) {
    FailureReason.NotEnoughSpace
  } else {
    FailureReason.UnexpectedError(toString())
  }
