package com.komgareader.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.komgareader.app.ui.components.EinkModal
import com.komgareader.app.ui.components.EinkOutlinedButton
import com.komgareader.app.ui.components.FieldCaption
import com.komgareader.app.ui.components.HighlightText
import com.komgareader.app.ui.components.SettingsGroup
import com.komgareader.app.ui.components.StepperRow
import com.komgareader.app.ui.components.SwitchRow
import com.komgareader.app.ui.icons.AppIcons
import com.komgareader.app.ui.reader.NovelTypographyControls
import com.komgareader.app.ui.theme.EinkTokens
import com.komgareader.app.ui.theme.ThemeMode
import com.komgareader.domain.model.DisplayMode
import com.komgareader.domain.model.SourceKind
import com.komgareader.domain.repository.ServerConfig

/** Schrittweite und Grenzen der Webtoon-Überlappung (in Prozent). */
private const val OVERLAP_STEP = 5
private const val OVERLAP_MIN = 0
private const val OVERLAP_MAX = 50

/**
 * Modus des Verbindungs-Modals: ADD (leere Felder) oder EDIT einer bestehenden [ServerConfig].
 * Genau ein Modal gleichzeitig (E-Ink-Regel) → ein einziger nullbarer State steuert beides.
 */
private sealed interface ConnectionModal {
    data object Add : ConnectionModal
    data class Edit(val config: ServerConfig) : ConnectionModal
}

@Composable
fun ConnectionSettingsContent(viewModel: SettingsViewModel, query: String) {
    val s = LocalStrings.current
    val servers by viewModel.serverList.collectAsState()

    // Genau EIN Modal gleichzeitig: null = zu, sonst Add oder Edit (E-Ink-Invariante).
    var modal by remember { mutableStateOf<ConnectionModal?>(null) }

    // Verbundene Server (mehrere gleichzeitig, gemischt) — „+" im Kopf öffnet das Modal,
    // jede Zeile trägt Zahnrad (Bearbeiten) + Papierkorb (Entfernen).
    SettingsGroup(
        s.connectedServers,
        query,
        trailing = {
            IconButton(onClick = { modal = ConnectionModal.Add }) {
                Icon(
                    AppIcons.Plus,
                    contentDescription = s.addServer,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
    ) {
        if (servers.isEmpty()) {
            HighlightText(
                s.noServersHint, query, MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                servers.forEach { cfg ->
                    ServerRow(
                        config = cfg,
                        query = query,
                        editLabel = s.editServer,
                        removeLabel = s.removeServer,
                        onEdit = { modal = ConnectionModal.Edit(cfg) },
                        onRemove = { viewModel.removeServer(cfg.id) },
                    )
                }
            }
        }
    }

    modal?.let { mode ->
        ConnectionModal(
            mode = mode,
            onDismiss = { modal = null },
            onSave = { name, url, apiKey, username, password, kind, id ->
                viewModel.saveServer(name, url, apiKey, username, password, kind, id)
                modal = null
            },
        )
    }
}

/**
 * Verbindungs-Modal für ADD **und** EDIT (eine Composable, kein Duplikat). Hält alle
 * Verbindungseingaben: Quellenart, Name/URL, dann Anmeldung (Benutzer → Passwort → API-Schlüssel,
 * alle optional). Quellen-agnostisch: nur [SourceKind]/[ServerConfig], nie ein Komga-Typ.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ConnectionModal(
    mode: ConnectionModal,
    onDismiss: () -> Unit,
    onSave: (
        name: String, url: String, apiKey: String,
        username: String, password: String, kind: SourceKind, id: Long,
    ) -> Unit,
) {
    val s = LocalStrings.current
    val existing = (mode as? ConnectionModal.Edit)?.config
    val id = existing?.id ?: 0L

    var nameInput by remember { mutableStateOf(existing?.name.orEmpty()) }
    var urlInput by remember { mutableStateOf(existing?.baseUrl.orEmpty()) }
    var apiKeyInput by remember { mutableStateOf(existing?.apiKey.orEmpty()) }
    var usernameInput by remember { mutableStateOf(existing?.username.orEmpty()) }
    var passwordInput by remember { mutableStateOf(existing?.password.orEmpty()) }
    var kindInput by remember { mutableStateOf(existing?.kind ?: SourceKind.KOMGA) }

    EinkModal(
        title = if (existing == null) s.addServer else s.editServer,
        onDismiss = onDismiss,
        confirmLabel = s.save,
        onConfirm = {
            onSave(nameInput, urlInput, apiKeyInput, usernameInput, passwordInput, kindInput, id)
        },
        dismissLabel = s.cancel,
        confirmEnabled = nameInput.isNotBlank() && urlInput.isNotBlank(),
    ) {
        // Quellenart: Komga (REST) oder OPDS (Feed). Markennamen — kein i18n-Key nötig.
        Column {
            FieldCaption(s.serverSectionKind)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val select: (SourceKind) -> Unit = { kindInput = it }
                if (kindInput == SourceKind.KOMGA) {
                    Button(onClick = { select(SourceKind.KOMGA) }) { Text("Komga") }
                    EinkOutlinedButton(onClick = { select(SourceKind.OPDS) }) { Text("OPDS") }
                } else {
                    EinkOutlinedButton(onClick = { select(SourceKind.KOMGA) }) { Text("Komga") }
                    Button(onClick = { select(SourceKind.OPDS) }) { Text("OPDS") }
                }
            }
        }

        // Server-Identität: Name + URL gehören zusammen.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldCaption(s.serverSectionServer)
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
        }

        // Anmeldung (alle optional): Benutzer → Passwort → API-Schlüssel, schlicht gestapelt.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldCaption(s.serverSectionAuth)
            CredentialsFields(
                username = usernameInput,
                password = passwordInput,
                onUsername = { usernameInput = it },
                onPassword = { passwordInput = it },
                usernameLabel = s.serverUsername,
                passwordLabel = s.serverPassword,
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
        }
    }
}

/** Benutzername-/Passwort-Felder samt Autofill-Verdrahtung — als ein Block. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun CredentialsFields(
    username: String,
    password: String,
    onUsername: (String) -> Unit,
    onPassword: (String) -> Unit,
    usernameLabel: String,
    passwordLabel: String,
) {
    val autofill = LocalAutofill.current
    val autofillTree = LocalAutofillTree.current

    val usernameNode = remember(onUsername) {
        AutofillNode(autofillTypes = listOf(AutofillType.Username), onFill = onUsername)
    }
    autofillTree += usernameNode
    OutlinedTextField(
        value = username,
        onValueChange = onUsername,
        label = { Text(usernameLabel) },
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

    val passwordNode = remember(onPassword) {
        AutofillNode(autofillTypes = listOf(AutofillType.Password), onFill = onPassword)
    }
    autofillTree += passwordNode
    OutlinedTextField(
        value = password,
        onValueChange = onPassword,
        label = { Text(passwordLabel) },
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
}

/**
 * Eine Zeile der Server-Liste: Name + Quellenart + URL links, rechts zwei neutrale Icon-Aktionen
 * — Zahnrad (Bearbeiten) links vom Papierkorb (Entfernen). Flach mit 1.5px-Border
 * (E-Ink-Designsprache), kein Schatten. Aktions-Icons neutral (`onSurface`), kein Akzent.
 */
@Composable
private fun ServerRow(
    config: ServerConfig,
    query: String,
    editLabel: String,
    removeLabel: String,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(EinkTokens.hairline, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val kindLabel = when (config.kind) {
            SourceKind.KOMGA -> "Komga"
            SourceKind.OPDS -> "OPDS"
            else -> config.kind.name
        }
        Column(Modifier.weight(1f)) {
            HighlightText("${config.name}  ·  $kindLabel", query, MaterialTheme.typography.bodyLarge)
            HighlightText(config.baseUrl, query, MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onEdit) {
            Icon(
                AppIcons.Settings,
                contentDescription = editLabel,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                AppIcons.Delete,
                contentDescription = removeLabel,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
fun AppearanceSettingsContent(viewModel: SettingsViewModel, query: String) {
    val s = LocalStrings.current
    val themeModeStr by viewModel.themeMode.collectAsState()
    val themeMode = runCatching { ThemeMode.valueOf(themeModeStr) }.getOrDefault(ThemeMode.SYSTEM)

    SettingsGroup(s.settingsTheme, query) {
        ThemeMode.entries.forEach { mode ->
            val label = when (mode) {
                ThemeMode.LIGHT -> s.themeLight
                ThemeMode.DARK -> s.themeDark
                ThemeMode.SYSTEM -> s.themeSystem
            }
            ChoiceRow(label, selected = mode == themeMode, query = query, dense = true) {
                viewModel.setTheme(mode.name)
            }
        }
    }
}

@Composable
fun ReaderSettingsContent(viewModel: SettingsViewModel, query: String) {
    val s = LocalStrings.current
    val displayModeStr by viewModel.displayMode.collectAsState()
    val displayMode = runCatching { DisplayMode.valueOf(displayModeStr) }.getOrDefault(DisplayMode.EINK)
    val overlap by viewModel.webtoonOverlapPercent.collectAsState()
    val panelOverlay by viewModel.guidedPanelOverlay.collectAsState()
    val deviceManagedRefresh by viewModel.deviceManagedRefresh.collectAsState()

    // Roman-Typografie: dieselbe stateless Komponente wie das In-Reader-Panel, gegen dieselbe
    // SettingsRepository-Quelle verdrahtet (DRY, eine Wahrheit).
    val novelFontSizeEm by viewModel.novelFontSizeEm.collectAsState()
    val novelLineHeight by viewModel.novelLineHeight.collectAsState()
    val novelFontWeight by viewModel.novelFontWeight.collectAsState()
    val novelMarginPreset by viewModel.novelMarginPreset.collectAsState()
    val novelTextAlign by viewModel.novelTextAlign.collectAsState()
    val novelHyphenationLang by viewModel.novelHyphenationLang.collectAsState()
    val novelFontFamily by viewModel.novelFontFamily.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(EinkTokens.sectionGap)) {
        SettingsGroup(s.novelTypography, query) {
            NovelTypographyControls(
                fontSizeEm = novelFontSizeEm,
                onFontSize = viewModel::setNovelFontSizeEm,
                lineHeight = novelLineHeight,
                onLineHeight = viewModel::setNovelLineHeight,
                fontWeight = novelFontWeight,
                onFontWeight = viewModel::setNovelFontWeight,
                marginPreset = novelMarginPreset,
                onMargin = viewModel::setNovelMarginPreset,
                textAlign = novelTextAlign,
                onTextAlign = viewModel::setNovelTextAlign,
                hyphenationLang = novelHyphenationLang,
                onHyphenation = viewModel::setNovelHyphenationLang,
                fontFamily = novelFontFamily,
                onFontFamily = viewModel::setNovelFontFamily,
            )
        }
        SettingsGroup(s.settingsEinkRefresh, query) {
            SwitchRow(
                label = s.deviceManagedRefresh,
                helper = s.deviceManagedRefreshHelper,
                checked = deviceManagedRefresh,
                onCheckedChange = { viewModel.setDeviceManagedRefresh(it) },
                query = query,
            )
        }
        SettingsGroup(s.settingsWebtoon, query, helper = s.webtoonOverlapHelper) {
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
        SettingsGroup(s.settingsDisplayMode, query, helper = s.displayModeHelper) {
            DisplayMode.entries.forEach { dm ->
                val label = when (dm) {
                    DisplayMode.EINK -> s.displayEink
                    DisplayMode.SMARTPHONE -> s.displaySmartphone
                }
                ChoiceRow(label, selected = dm == displayMode, query = query, dense = true) {
                    viewModel.setDisplayMode(dm.name)
                }
            }
        }
        SettingsGroup(s.settingsGuidedDebug, query) {
            SwitchRow(
                label = s.readerPanelOverlay,
                checked = panelOverlay,
                onCheckedChange = { viewModel.setGuidedPanelOverlay(it) },
                query = query,
            )
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

    val folderLabel = downloadDir?.let { dir ->
        runCatching { Uri.parse(dir).lastPathSegment ?: dir }.getOrElse { dir }
    } ?: s.defaultFolder

    SettingsGroup(s.downloadFolder, query, helper = folderLabel) {
        Row {
            Button(onClick = { folderPicker.launch(null) }) { Text(s.chooseFolder) }
            if (downloadDir != null) {
                Spacer(Modifier.width(8.dp))
                EinkOutlinedButton(onClick = { viewModel.setDownloadDir(null) }) { Text(s.resetFolder) }
            }
        }
    }
}

@Composable
fun LanguageSettingsContent(viewModel: SettingsViewModel, query: String) {
    val s = LocalStrings.current
    val languageStr by viewModel.language.collectAsState()
    SettingsGroup(s.settingsLanguage, query) {
        Language.entries.forEach { lang ->
            val label = when (lang) {
                Language.DE -> "Deutsch"
                Language.EN -> "English"
            }
            ChoiceRow(label, selected = lang.code == languageStr, query = query, dense = true) {
                viewModel.setLanguage(lang.code)
            }
        }
    }
}

@Composable
fun AboutContent(query: String) {
    val s = LocalStrings.current
    SettingsGroup(s.appName, query) {
        HighlightText(
            s.aboutDevice, query, MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Row(Modifier.fillMaxWidth()) {
            Text("${s.versionLabel}: ", style = MaterialTheme.typography.bodyMedium)
            HighlightText(BuildConfig.VERSION_NAME, query, MaterialTheme.typography.bodyMedium)
        }
    }
}
