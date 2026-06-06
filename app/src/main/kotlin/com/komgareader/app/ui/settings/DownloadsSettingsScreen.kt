package com.komgareader.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.ui.components.SectionHeader
import com.komgareader.app.ui.components.SubPageScaffold

/** Downloads: SAF-Ordner für heruntergeladene Bücher wählen/zurücksetzen. */
@Composable
fun DownloadsSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val s = LocalStrings.current
    val ctx = LocalContext.current
    val downloadDir by viewModel.downloadDir.collectAsState()

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri != null) {
            ctx.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            viewModel.setDownloadDir(uri.toString())
        }
    }

    SubPageScaffold(title = s.settingsDownloads, onBack = onBack) {
        Column {
            SectionHeader(s.downloadFolder)
            val folderLabel = downloadDir?.let { dir ->
                runCatching { Uri.parse(dir).lastPathSegment ?: dir }.getOrElse { dir }
            } ?: s.defaultFolder
            Text(
                folderLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            Row {
                Button(onClick = { folderPicker.launch(null) }) { Text(s.chooseFolder) }
                if (downloadDir != null) {
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { viewModel.setDownloadDir(null) }) { Text(s.resetFolder) }
                }
            }
        }
    }
}
