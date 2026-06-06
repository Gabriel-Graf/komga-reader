package com.komgareader.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.komgareader.app.BuildConfig
import com.komgareader.app.i18n.Language
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.ui.components.ChoiceRow
import com.komgareader.app.ui.components.HighlightText
import com.komgareader.app.ui.components.SectionHeader
import com.komgareader.app.ui.components.StepperRow
import com.komgareader.app.ui.theme.EinkTokens
import com.komgareader.app.ui.theme.ThemeMode
import com.komgareader.domain.model.DisplayMode

/** Die einzelnen Settings-Sektionen. Reihenfolge = Sidebar-/Accordion-Reihenfolge. */
enum class SettingsSectionId { CONNECTION, APPEARANCE, READER, DOWNLOADS, LANGUAGE, ABOUT }

/** Schrittweite und Grenzen der Webtoon-Überlappung (in Prozent). */
private const val OVERLAP_STEP = 5
private const val OVERLAP_MIN = 0
private const val OVERLAP_MAX = 50

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ConnectionSettingsContent(viewModel: SettingsViewModel, query: String) {
    val s = LocalStrings.current
    val server by viewModel.server.collectAsState()

    var nameInput by remember { mutableStateOf("") }
    var urlInput by remember { mutableStateOf("") }
    var apiKeyInput by remember { mutableStateOf("") }
    var usernameInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(EinkTokens.sectionGap)) {
        val statusText = if (server != null) "${s.connected}: ${server!!.name}" else s.notConnected
        HighlightText(statusText, query, MaterialTheme.typography.bodyLarge)

        OutlinedTextField(
            value = nameInput,
            onValueChange = { nameInput = it },
            label = { Text(s.serverDisplayName) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
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
        OutlinedTextField(
            value = apiKeyInput,
            onValueChange = { apiKeyInput = it },
            label = { Text(s.serverApiKeyOptional) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )

        HorizontalDivider()
        Text(
            text = s.orSeparator,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        HorizontalDivider()

        val autofill = LocalAutofill.current
        val autofillTree = LocalAutofillTree.current

        val usernameNode = remember {
            AutofillNode(autofillTypes = listOf(AutofillType.Username), onFill = { usernameInput = it })
        }
        autofillTree += usernameNode
        OutlinedTextField(
            value = usernameInput,
            onValueChange = { usernameInput = it },
            label = { Text(s.serverUsername) },
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { usernameNode.boundingBox = it.boundsInWindow() }
                .onFocusChanged { focus ->
                    autofill?.run {
                        if (focus.isFocused) requestAutofillForNode(usernameNode)
                        else cancelAutofillForNode(usernameNode)
                    }
                },
            singleLine = true,
        )

        val passwordNode = remember {
            AutofillNode(autofillTypes = listOf(AutofillType.Password), onFill = { passwordInput = it })
        }
        autofillTree += passwordNode
        OutlinedTextField(
            value = passwordInput,
            onValueChange = { passwordInput = it },
            label = { Text(s.serverPassword) },
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { passwordNode.boundingBox = it.boundsInWindow() }
                .onFocusChanged { focus ->
                    autofill?.run {
                        if (focus.isFocused) requestAutofillForNode(passwordNode)
                        else cancelAutofillForNode(passwordNode)
                    }
                },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        Row {
            Button(onClick = {
                viewModel.saveServer(nameInput, urlInput, apiKeyInput, usernameInput, passwordInput)
                nameInput = ""; urlInput = ""; apiKeyInput = ""; usernameInput = ""; passwordInput = ""
            }) { Text(s.connect) }
            if (server != null) {
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { viewModel.disconnect() }) { Text(s.disconnect) }
            }
        }
    }
}

@Composable
fun AppearanceSettingsContent(viewModel: SettingsViewModel, query: String) {
    val s = LocalStrings.current
    val themeModeStr by viewModel.themeMode.collectAsState()
    val themeMode = runCatching { ThemeMode.valueOf(themeModeStr) }.getOrDefault(ThemeMode.SYSTEM)

    Column {
        SectionHeader(s.settingsTheme)
        ThemeMode.entries.forEach { mode ->
            val label = when (mode) {
                ThemeMode.LIGHT -> s.themeLight
                ThemeMode.DARK -> s.themeDark
                ThemeMode.SYSTEM -> s.themeSystem
            }
            ChoiceRow(label, selected = mode == themeMode, query = query) { viewModel.setTheme(mode.name) }
        }
    }
}

@Composable
fun ReaderSettingsContent(viewModel: SettingsViewModel, query: String) {
    val s = LocalStrings.current
    val displayModeStr by viewModel.displayMode.collectAsState()
    val displayMode = runCatching { DisplayMode.valueOf(displayModeStr) }.getOrDefault(DisplayMode.EINK)
    val overlap by viewModel.webtoonOverlapPercent.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(EinkTokens.sectionGap)) {
        Column {
            SectionHeader(s.settingsWebtoon)
            HighlightText(
                s.webtoonOverlapHelper, query, MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StepperRow(
                label = s.webtoonOverlap,
                value = overlap,
                canDecrement = overlap > OVERLAP_MIN,
                canIncrement = overlap < OVERLAP_MAX,
                onDecrement = { viewModel.setWebtoonOverlap(overlap - OVERLAP_STEP) },
                onIncrement = { viewModel.setWebtoonOverlap(overlap + OVERLAP_STEP) },
                display = { "$it %" },
            )
        }
        Column {
            SectionHeader(s.settingsDisplayMode)
            HighlightText(
                s.displayModeHelper, query, MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DisplayMode.entries.forEach { dm ->
                val label = when (dm) {
                    DisplayMode.EINK -> s.displayEink
                    DisplayMode.SMARTPHONE -> s.displaySmartphone
                }
                ChoiceRow(label, selected = dm == displayMode, query = query) { viewModel.setDisplayMode(dm.name) }
            }
        }
    }
}

@Composable
fun DownloadsSettingsContent(viewModel: SettingsViewModel, query: String) {
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

    Column {
        SectionHeader(s.downloadFolder)
        val folderLabel = downloadDir?.let { dir ->
            runCatching { Uri.parse(dir).lastPathSegment ?: dir }.getOrElse { dir }
        } ?: s.defaultFolder
        HighlightText(
            folderLabel, query, MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 12.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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

@Composable
fun LanguageSettingsContent(viewModel: SettingsViewModel, query: String) {
    val languageStr by viewModel.language.collectAsState()
    Column {
        Language.entries.forEach { lang ->
            val label = when (lang) {
                Language.DE -> "Deutsch"
                Language.EN -> "English"
            }
            ChoiceRow(label, selected = lang.code == languageStr, query = query) { viewModel.setLanguage(lang.code) }
        }
    }
}

@Composable
fun AboutContent(query: String) {
    val s = LocalStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(EinkTokens.sectionGap)) {
        Column {
            HighlightText(s.appName, query, MaterialTheme.typography.titleLarge)
            HighlightText(
                s.aboutDevice, query, MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(Modifier.fillMaxWidth()) {
            Text("${s.versionLabel}: ", style = MaterialTheme.typography.bodyMedium)
            HighlightText(BuildConfig.VERSION_NAME, query, MaterialTheme.typography.bodyMedium)
        }
    }
}
