package com.pedroloureiro.hivenetbackup.domain

import com.pedroloureiro.hivenetbackup.domain.models.FailureReason

/** Thrown by BackupCopyRepository for the failures only the data layer can tell apart, such as ENOSPC. */
class BackupCopyFailedException(val reason: FailureReason, cause: Throwable? = null) : Exception(cause)
