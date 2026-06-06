package com.komgareader.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.AutofillNode
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalAutofillTree
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.komgareader.app.i18n.Language
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.ui.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val s = LocalStrings.current
    val ctx = LocalContext.current
    val themeModeStr by viewModel.themeMode.collectAsState()
    val languageStr by viewModel.language.collectAsState()
    val downloadDir by viewModel.downloadDir.collectAsState()
    val server by viewModel.server.collectAsState()

    // SAF-Ordner-Picker: persistiert die Uri-Berechtigung und speichert sie in den Settings
    val folderPickerLauncher = rememberLauncherForActivityResult(
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

    val themeMode = runCatching { ThemeMode.valueOf(themeModeStr) }.getOrDefault(ThemeMode.SYSTEM)
    val language = if (languageStr == "en") Language.EN else Language.DE

    var nameInput by remember { mutableStateOf("") }
    var urlInput by remember { mutableStateOf("") }
    var apiKeyInput by remember { mutableStateOf("") }
    var usernameInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.settingsTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(s.settingsTheme)
            ThemeMode.entries.forEach { mode ->
                val label = when (mode) {
                    ThemeMode.LIGHT -> s.themeLight
                    ThemeMode.DARK -> s.themeDark
                    ThemeMode.SYSTEM -> s.themeSystem
                }
                OptionRow(label, selected = mode == themeMode) { viewModel.setTheme(mode.name) }
            }

            Text(s.settingsLanguage, modifier = Modifier.padding(top = 16.dp))
            Language.entries.forEach { lang ->
                OptionRow(lang.code.uppercase(), selected = lang == language) {
                    viewModel.setLanguage(lang.code)
                }
            }

            // Download-Ordner (SAF)
            Spacer(Modifier.height(16.dp))
            Text(s.downloadFolder)
            Spacer(Modifier.height(8.dp))
            val folderLabel = if (downloadDir != null) {
                // Lesbaren Pfad-Anteil aus der URI ableiten
                runCatching { Uri.parse(downloadDir).lastPathSegment ?: downloadDir!! }.getOrElse { downloadDir!! }
            } else {
                s.defaultFolder
            }
            Text(
                folderLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Row {
                Button(
                    onClick = { folderPickerLauncher.launch(null) },
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    Text(s.chooseFolder)
                }
                if (downloadDir != null) {
                    OutlinedButton(onClick = { viewModel.setDownloadDir(null) }) {
                        Text(s.resetFolder)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(s.settingsServer)
            Spacer(Modifier.height(8.dp))

            val statusText = if (server != null) {
                "${s.connected}: ${server!!.name}"
            } else {
                s.notConnected
            }
            Text(statusText, modifier = Modifier.padding(bottom = 8.dp))

            OutlinedTextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                label = { Text(s.serverDisplayName) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                label = { Text(s.serverUrl) },
                placeholder = { Text(s.serverUrlHint) },
                supportingText = { Text(s.serverUrlHelper) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = { apiKeyInput = it },
                label = { Text(s.serverApiKeyOptional) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Text(
                text = s.orSeparator,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 8.dp),
            )
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            val autofill = LocalAutofill.current
            val autofillTree = LocalAutofillTree.current

            val usernameAutofillNode = remember {
                AutofillNode(
                    autofillTypes = listOf(AutofillType.Username),
                    onFill = { usernameInput = it },
                )
            }
            autofillTree += usernameAutofillNode

            OutlinedTextField(
                value = usernameInput,
                onValueChange = { usernameInput = it },
                label = { Text(s.serverUsername) },
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { usernameAutofillNode.boundingBox = it.boundsInWindow() }
                    .onFocusChanged { focus ->
                        autofill?.run {
                            if (focus.isFocused) requestAutofillForNode(usernameAutofillNode)
                            else cancelAutofillForNode(usernameAutofillNode)
                        }
                    },
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))

            val passwordAutofillNode = remember {
                AutofillNode(
                    autofillTypes = listOf(AutofillType.Password),
                    onFill = { passwordInput = it },
                )
            }
            autofillTree += passwordAutofillNode

            OutlinedTextField(
                value = passwordInput,
                onValueChange = { passwordInput = it },
                label = { Text(s.serverPassword) },
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { passwordAutofillNode.boundingBox = it.boundsInWindow() }
                    .onFocusChanged { focus ->
                        autofill?.run {
                            if (focus.isFocused) requestAutofillForNode(passwordAutofillNode)
                            else cancelAutofillForNode(passwordAutofillNode)
                        }
                    },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            Spacer(Modifier.height(8.dp))
            Row {
                Button(
                    onClick = {
                        viewModel.saveServer(nameInput, urlInput, apiKeyInput, usernameInput, passwordInput)
                        nameInput = ""
                        urlInput = ""
                        apiKeyInput = ""
                        usernameInput = ""
                        passwordInput = ""
                    },
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    Text(s.connect)
                }
                if (server != null) {
                    OutlinedButton(onClick = { viewModel.disconnect() }) {
                        Text(s.disconnect)
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}
