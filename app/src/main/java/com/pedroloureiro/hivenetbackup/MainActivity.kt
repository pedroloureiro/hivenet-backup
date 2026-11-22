package com.pedroloureiro.hivenetbackup

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.core.content.FileProvider
import com.pedroloureiro.hivenetbackup.domain.models.BackupCopy
import com.pedroloureiro.hivenetbackup.ui.BackupScreen
import java.io.File
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.koinViewModel

class MainActivity : ComponentActivity() {
  private val backupStore: File by inject()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent { MaterialTheme { BackupScreen(koinViewModel(), ::openBackupCopy) } }
  }

  // The viewer gets read access to this one Backup copy and nothing else.
  private fun openBackupCopy(backupCopy: BackupCopy, mimeType: String) {
    val uri = FileProvider.getUriForFile(this, "$packageName.backups", File(backupStore, backupCopy.relativePath))
    val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mimeType).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    startActivity(Intent.createChooser(view, null))
  }
}
