package com.pedroloureiro.hivenetbackup.ui

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pedroloureiro.hivenetbackup.R
import com.pedroloureiro.hivenetbackup.domain.models.BackupCopy
import com.pedroloureiro.hivenetbackup.domain.models.Batch
import com.pedroloureiro.hivenetbackup.domain.models.BatchFile
import com.pedroloureiro.hivenetbackup.domain.models.FailureReason
import com.pedroloureiro.hivenetbackup.domain.models.FileState
import com.pedroloureiro.hivenetbackup.domain.models.SourceFile
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val CREATED_AT_FORMAT = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())

@Composable
fun BackupScreen(viewModel: BackupViewModel, onOpen: (backupCopy: BackupCopy, mimeType: String) -> Unit) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
    viewModel.addToSelection(uris.map { it.toString() })
  }
  var batchFileToRemove by remember { mutableStateOf<BatchFile?>(null) }
  BackupContent(
    uiState = uiState,
    onSelectFiles = { documentPicker.launch(arrayOf("*/*")) },
    onClear = viewModel::clearSelection,
    onBackUp = viewModel::backUp,
    onOpen = onOpen,
    onDelete = { batchFileToRemove = it },
  )
  batchFileToRemove?.let { batchFile ->
    DeleteBackupCopyDialog(
      displayName = batchFile.sourceFile.displayName,
      onConfirm = {
        viewModel.remove(batchFile)
        batchFileToRemove = null
      },
      onDismiss = { batchFileToRemove = null },
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackupContent(
  uiState: BackupUiState,
  onSelectFiles: () -> Unit,
  onClear: () -> Unit,
  onBackUp: () -> Unit,
  onOpen: (backupCopy: BackupCopy, mimeType: String) -> Unit,
  onDelete: (batchFile: BatchFile) -> Unit,
) {
  Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) }) { innerPadding ->
    LazyColumn(contentPadding = innerPadding) {
      item {
        SelectionActions(
          selectedCount = uiState.selection.size,
          canSelectFiles = uiState.canSelectFiles,
          canBackUp = uiState.canBackUp,
          onSelectFiles = onSelectFiles,
          onClear = onClear,
          onBackUp = onBackUp,
        )
      }
      items(uiState.selection, key = { it.uri }) { sourceFile ->
        ListItem(supportingContent = { Text(sizeText(sourceFile.reportedSize)) }) { Text(sourceFile.displayName) }
      }
      uiState.latestBatch?.let { batch ->
        item { BatchSummary(batch) }
        items(batch.files, key = { it.id }) { batchFile -> BatchFileRow(batchFile, onOpen) }
      }
      if (uiState.backupCopies.isNotEmpty()) {
        item { SectionTitle(stringResource(R.string.backup_copies)) }
        // A file can also be a row of the latest Batch, so its key here must differ.
        items(uiState.backupCopies, key = { "copy-${it.id}" }) { batchFile -> BackupCopyRow(batchFile, onOpen, onDelete) }
      }
    }
  }
}

@Composable
private fun SelectionActions(
  selectedCount: Int,
  canSelectFiles: Boolean,
  canBackUp: Boolean,
  onSelectFiles: () -> Unit,
  onClear: () -> Unit,
  onBackUp: () -> Unit,
) {
  Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedButton(onClick = onSelectFiles, enabled = canSelectFiles) { Text(stringResource(R.string.select_files)) }
      TextButton(onClick = onClear, enabled = selectedCount > 0) { Text(stringResource(R.string.clear)) }
    }
    Button(onClick = onBackUp, enabled = canBackUp, modifier = Modifier.fillMaxWidth()) {
      Text(pluralStringResource(R.plurals.back_up_files, selectedCount, selectedCount))
    }
  }
}

@Composable
private fun SectionTitle(title: String) {
  Text(title, Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp), style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun BatchSummary(batch: Batch) {
  val countPerFileState = batch.files.groupingBy { it.state.labelRes() }.eachCount()
    .map { (labelRes, count) -> "$count ${stringResource(labelRes)}" }
  Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp)) {
    Text(stringResource(R.string.latest_batch), style = MaterialTheme.typography.titleMedium)
    Text(CREATED_AT_FORMAT.format(batch.createdAt), style = MaterialTheme.typography.bodyMedium)
    Text(countPerFileState.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
  }
}

@Composable
private fun BatchFileRow(batchFile: BatchFile, onOpen: (backupCopy: BackupCopy, mimeType: String) -> Unit) {
  val fileState = batchFile.state
  ListItem(
    supportingContent = {
      Column {
        Text(sizeText(batchFile.sourceFile.reportedSize))
        if (fileState is FileState.Failed) {
          Text(stringResource(R.string.failed_because, failureReasonText(fileState.reason)), color = MaterialTheme.colorScheme.error)
        } else {
          Text(stringResource(fileState.labelRes()))
        }
      }
    },
    trailingContent = when (fileState) {
      is FileState.BackedUp -> {
        { TextButton(onClick = { onOpen(fileState.copy, batchFile.sourceFile.mimeType) }) { Text(stringResource(R.string.open)) } }
      }
      FileState.Copying -> {
        { CircularProgressIndicator(Modifier.size(24.dp)) }
      }
      else -> null
    },
  ) {
    Text(batchFile.sourceFile.displayName)
  }
}

@Composable
private fun BackupCopyRow(
  batchFile: BatchFile,
  onOpen: (backupCopy: BackupCopy, mimeType: String) -> Unit,
  onDelete: (batchFile: BatchFile) -> Unit,
) {
  // The section holds only Backed up files.
  val fileState = batchFile.state as? FileState.BackedUp ?: return
  ListItem(
    supportingContent = { Text(sizeText(fileState.copy.storedSize)) },
    trailingContent = {
      Row {
        TextButton(onClick = { onOpen(fileState.copy, batchFile.sourceFile.mimeType) }) { Text(stringResource(R.string.open)) }
        TextButton(onClick = { onDelete(batchFile) }) { Text(stringResource(R.string.delete)) }
      }
    },
  ) {
    Text(batchFile.sourceFile.displayName)
  }
}

@Composable
private fun DeleteBackupCopyDialog(displayName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.delete)) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    title = { Text(stringResource(R.string.delete_backup_copy_title)) },
    text = { Text(stringResource(R.string.delete_backup_copy_text, displayName)) },
  )
}

@Composable
private fun sizeText(reportedSize: Long?): String =
  reportedSize?.let { Formatter.formatShortFileSize(LocalContext.current, it) } ?: stringResource(R.string.unknown_size)

@Composable
private fun failureReasonText(failureReason: FailureReason): String = when (failureReason) {
  FailureReason.SourceNoLongerAvailable -> stringResource(R.string.failure_source_no_longer_available)
  FailureReason.CouldNotReadSource -> stringResource(R.string.failure_could_not_read_source)
  FailureReason.NotEnoughSpace -> stringResource(R.string.failure_not_enough_space)
  FailureReason.CopyDidNotMatchSource -> stringResource(R.string.failure_copy_did_not_match_source)
  is FailureReason.UnexpectedError -> stringResource(R.string.failure_unexpected_error, failureReason.detail)
}

@StringRes
private fun FileState.labelRes(): Int = when (this) {
  FileState.Waiting -> R.string.file_state_waiting
  FileState.Copying -> R.string.file_state_copying
  is FileState.BackedUp -> R.string.file_state_backed_up
  is FileState.Failed -> R.string.file_state_failed
  FileState.Interrupted -> R.string.file_state_interrupted
  FileState.NotStarted -> R.string.file_state_not_started
  FileState.Removed -> R.string.file_state_removed
}

@Preview(showBackground = true)
@Composable
private fun SelectionPreview() {
  MaterialTheme {
    BackupContent(
      uiState = BackupUiState(selection = listOf(previewSourceFile("notes.txt", 2_048), previewSourceFile("scan.pdf", null))),
      onSelectFiles = {},
      onClear = {},
      onBackUp = {},
      onOpen = { _, _ -> },
      onDelete = {},
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun RunningBatchPreview() {
  val states = listOf(
    FileState.BackedUp(BackupCopy(relativePath = "1/1/photo.jpg", fingerprint = "ab12", storedSize = 3_400_000)),
    FileState.Failed(FailureReason.SourceNoLongerAvailable),
    FileState.Copying,
    FileState.Waiting,
  )
  val files = states.mapIndexed { index, fileState ->
    BatchFile(id = index + 1L, batchId = 1, sourceFile = previewSourceFile("file-${index + 1}.jpg", 3_400_000), state = fileState)
  }
  MaterialTheme {
    BackupContent(
      uiState = BackupUiState(latestBatch = Batch(id = 1, createdAt = Instant.now(), files = files)),
      onSelectFiles = {},
      onClear = {},
      onBackUp = {},
      onOpen = { _, _ -> },
      onDelete = {},
    )
  }
}

private fun previewSourceFile(displayName: String, reportedSize: Long?) =
  SourceFile(uri = "content://preview/$displayName", displayName = displayName, mimeType = "application/octet-stream", reportedSize = reportedSize)
