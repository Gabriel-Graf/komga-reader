package com.komgareader.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.komgareader.app.BuildConfig
import com.komgareader.app.data.AppUpdateState
import com.komgareader.app.data.UpdateInstall
import com.komgareader.data.update.ReleaseInfo
import com.komgareader.ui.theme.LocalDesignTokens
import com.komgareader.app.i18n.Language
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.ui.components.AnimatedAppIcon
import com.komgareader.app.ui.components.ChoiceRow
import com.komgareader.app.ui.components.CompactStepperRow
import com.komgareader.app.ui.components.IconAnimation
import com.komgareader.app.ui.components.SettingsRow
import com.komgareader.app.ui.components.EinkModal
import com.komgareader.app.ui.plugins.AddPluginSourceModals
import com.komgareader.app.ui.reader.HyphenationPicker
import com.komgareader.plugin.host.DiscoveredPlugin
import com.komgareader.app.ui.components.EinkOutlinedButton
import com.komgareader.app.ui.components.EinkTextField
import com.komgareader.app.ui.components.FieldCaption
import com.komgareader.app.ui.components.FolderPickerRow
import com.komgareader.app.ui.components.HighlightText
import com.komgareader.app.ui.components.PickerModal
import com.komgareader.app.ui.components.PickerRow
import com.komgareader.app.ui.components.ScopeHeader
import com.komgareader.app.ui.components.SegmentOption
import com.komgareader.app.ui.components.SegmentedChoiceRow
import com.komgareader.app.ui.components.SettingsGroup
import com.komgareader.app.ui.components.SettingsGroupIndent
import com.komgareader.app.ui.components.StepperRow
import com.komgareader.app.ui.components.SwitchRow
import com.komgareader.ui.icons.AppIcons
import com.komgareader.ui.theme.EinkTokens
import com.komgareader.app.ui.theme.ThemeMode
import com.komgareader.domain.model.BookmarkMarkerStyle
import com.komgareader.domain.model.DisplayMode
import com.komgareader.domain.model.ExternalOpenBehavior
import com.komgareader.domain.model.ReaderPreset
import com.komgareader.domain.model.ShellLayoutMode
import com.komgareader.domain.model.SourceKind
import com.komgareader.domain.render.NovelFonts
import com.komgareader.domain.render.NovelSettings
import com.komgareader.domain.repository.ServerConfig

/** Schrittweite und Grenzen der Webtoon-Überlappung (in Prozent). */
private const val OVERLAP_STEP = 5
private const val OVERLAP_MIN = 0
private const val OVERLAP_MAX = 50

/**
 * Zustand des Verbindungs-Modals. Genau ein Modal gleichzeitig (E-Ink-Regel) →
 * ein einziger nullbarer State steuert den gesamten Fluss:
 *
 * - [Add]  → Standard-Formular für Komga/OPDS
 * - [Edit] → Verbindung bearbeiten (Komga/OPDS)
 */
private sealed interface ConnectionModal {
    data object Add : ConnectionModal
    data class Edit(val config: ServerConfig) : ConnectionModal
}

@Composable
fun ConnectionSettingsContent(viewModel: SettingsViewModel, query: String) {
    val s = LocalStrings.current
    // Lokaler Ordner (SourceKind.LOCAL) wird unter „Downloads" verwaltet, nicht hier — daher
    // aus der „Verbundene Server"-Liste ausblenden (UX: ein lokaler Ordner ist kein Server).
    val servers by viewModel.serverList.collectAsState()
    val displayedServers = servers.filter { it.kind != SourceKind.LOCAL }
    val sourcePlugins by viewModel.sourcePlugins.collectAsState()

    // Genau EIN Modal gleichzeitig: null = zu (E-Ink-Invariante).
    var modal by remember { mutableStateOf<ConnectionModal?>(null) }
    // Plugin-Add-Flow: modal wird auf null gesetzt BEVOR pendingPlugin gesetzt wird →
    // zu keinem Zeitpunkt sind AddConnectionModal und PluginTofuModal gleichzeitig sichtbar.
    var pendingPlugin by remember { mutableStateOf<DiscoveredPlugin?>(null) }

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
        if (displayedServers.isEmpty()) {
            HighlightText(
                s.noServersHint, query, MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                displayedServers.forEach { cfg ->
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

    when (val mode = modal) {
        is ConnectionModal.Add -> AddConnectionModal(
            onDismiss = { modal = null },
            onSave = { name, url, apiKey, username, password, kind, id ->
                viewModel.saveServer(name, url, apiKey, username, password, kind, id)
                modal = null
            },
            sourcePlugins = sourcePlugins,
            onPluginSelected = { plugin ->
                // AddConnectionModal schließen BEVOR pendingPlugin gesetzt wird:
                // modal = null → keine Überschneidung mit PluginTofuModal.
                modal = null
                pendingPlugin = plugin
            },
        )
        is ConnectionModal.Edit -> EditConnectionModal(
            config = mode.config,
            onDismiss = { modal = null },
            onSave = { name, url, apiKey, username, password, kind, id ->
                viewModel.saveServer(name, url, apiKey, username, password, kind, id)
                modal = null
            },
        )
        null -> Unit
    }

    // Plugin-TOFU→Config-Flow: läuft erst wenn modal == null, also nie gleichzeitig
    // mit AddConnectionModal oder EditConnectionModal (E-Ink-Invariante: max. 1 Dialog).
    AddPluginSourceModals(
        trigger = pendingPlugin,
        onDismiss = { pendingPlugin = null },
        onAdd = { plugin, values ->
            viewModel.addPluginSource(plugin, values)
            pendingPlugin = null
        },
    )
}

/**
 * Modal „Server hinzufügen": Komga/OPDS-Formular (Segment-Selektor) plus „Plugin"-Segment
 * für entdeckte Quellen-Plugins. Im Plugin-Pfad ruft ein Tap auf eine Plugin-Zeile
 * [onPluginSelected] — das Modal schließt sich im Elternteil, bevor der TOFU→Config-Dialog
 * erscheint (E-Ink-Invariante: max. 1 Dialog gleichzeitig).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun AddConnectionModal(
    onDismiss: () -> Unit,
    onSave: (
        name: String, url: String, apiKey: String,
        username: String, password: String, kind: SourceKind, id: Long,
    ) -> Unit,
    sourcePlugins: List<DiscoveredPlugin>,
    onPluginSelected: (DiscoveredPlugin) -> Unit,
) {
    val s = LocalStrings.current

    var nameInput by remember { mutableStateOf("") }
    var urlInput by remember { mutableStateOf("") }
    var apiKeyInput by remember { mutableStateOf("") }
    var usernameInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var kindInput by remember { mutableStateOf(SourceKind.KOMGA) }

    EinkModal(
        title = s.addServer,
        onDismiss = onDismiss,
        confirmLabel = s.save,
        onConfirm = {
            onSave(nameInput, urlInput, apiKeyInput, usernameInput, passwordInput, kindInput, 0L)
        },
        dismissLabel = s.cancel,
        // Plugin-Pfad: kein Speichern über EinkModal-Button — onPluginSelected übernimmt das
        // Speichern und Schließen. (Lokaler Ordner wird unter „Downloads" verwaltet, nicht hier.)
        confirmEnabled = kindInput != SourceKind.PLUGIN &&
            nameInput.isNotBlank() && urlInput.isNotBlank(),
    ) {
        // Quellenart: Komga (REST), OPDS (Feed) oder Plugin als Segment-Selektor.
        SegmentedChoiceRow(
            label = s.serverSectionKind,
            options = listOf(
                SegmentOption(SourceKind.KOMGA.name, "Komga"),
                SegmentOption(SourceKind.OPDS.name, "OPDS"),
                SegmentOption(SourceKind.PLUGIN.name, s.serverKindPlugin),
            ),
            selectedKey = kindInput.name,
            onSelect = { kindInput = SourceKind.valueOf(it) },
        )

        if (kindInput == SourceKind.PLUGIN) {
            // Plugin-Pfad: Liste entdeckter Quellen-Plugins; leer → Hinweis.
            if (sourcePlugins.isEmpty()) {
                Text(
                    s.addServerNoSourcePlugins,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FieldCaption(s.addServerSelectPlugin)
                    sourcePlugins.forEach { plugin ->
                        ChoiceRow(
                            label = plugin.metadata.displayName,
                            selected = false,
                            query = "",
                            dense = true,
                            onSelect = { onPluginSelected(plugin) },
                        )
                    }
                }
            }
        } else {
            // Komga/OPDS-Pfad: normales Formular.

            // Server-Identität: Name + URL gehören zusammen.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldCaption(s.serverSectionServer)
                EinkTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = s.serverDisplayName,
                )
                EinkTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = s.serverUrl,
                    placeholder = s.serverUrlHint,
                    supportingText = s.serverUrlHelper,
                    keyboardType = KeyboardType.Uri,
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
                EinkTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    label = s.serverApiKeyOptional,
                    isPassword = true,
                )
            }
        }
    }
}

/**
 * Modal „Verbindung bearbeiten" für Komga/OPDS. Plugin-Verbindungen sind in v1 nicht editierbar
 * (Plugin-Schema kann sich ändern; Signatur-Pin bleibt; Benutzer entfernt und fügt neu hinzu).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun EditConnectionModal(
    config: ServerConfig,
    onDismiss: () -> Unit,
    onSave: (
        name: String, url: String, apiKey: String,
        username: String, password: String, kind: SourceKind, id: Long,
    ) -> Unit,
) {
    val s = LocalStrings.current
    val id = config.id

    var nameInput by remember { mutableStateOf(config.name) }
    var urlInput by remember { mutableStateOf(config.baseUrl) }
    var apiKeyInput by remember { mutableStateOf(config.apiKey.orEmpty()) }
    var usernameInput by remember { mutableStateOf(config.username.orEmpty()) }
    var passwordInput by remember { mutableStateOf(config.password.orEmpty()) }
    var kindInput by remember { mutableStateOf(config.kind) }

    EinkModal(
        title = s.editServer,
        onDismiss = onDismiss,
        confirmLabel = s.save,
        onConfirm = {
            onSave(nameInput, urlInput, apiKeyInput, usernameInput, passwordInput, kindInput, id)
        },
        dismissLabel = s.cancel,
        confirmEnabled = nameInput.isNotBlank() && urlInput.isNotBlank(),
    ) {
        SegmentedChoiceRow(
            label = s.serverSectionKind,
            options = listOf(
                SegmentOption(SourceKind.KOMGA.name, "Komga"),
                SegmentOption(SourceKind.OPDS.name, "OPDS"),
            ),
            selectedKey = kindInput.name,
            onSelect = { kindInput = SourceKind.valueOf(it) },
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldCaption(s.serverSectionServer)
            EinkTextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                label = s.serverDisplayName,
            )
            EinkTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                label = s.serverUrl,
                placeholder = s.serverUrlHint,
                supportingText = s.serverUrlHelper,
                keyboardType = KeyboardType.Uri,
            )
        }

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
            EinkTextField(
                value = apiKeyInput,
                onValueChange = { apiKeyInput = it },
                label = s.serverApiKeyOptional,
                isPassword = true,
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
    EinkTextField(
        value = username,
        onValueChange = onUsername,
        label = usernameLabel,
        modifier = Modifier
            .onGloballyPositioned { usernameNode.boundingBox = it.boundsInWindow() }
            .onFocusChanged { focus ->
                autofill?.run {
                    if (focus.isFocused) requestAutofillForNode(usernameNode)
                    else cancelAutofillForNode(usernameNode)
                }
            },
    )

    val passwordNode = remember(onPassword) {
        AutofillNode(autofillTypes = listOf(AutofillType.Password), onFill = onPassword)
    }
    autofillTree += passwordNode
    EinkTextField(
        value = password,
        onValueChange = onPassword,
        label = passwordLabel,
        modifier = Modifier
            .onGloballyPositioned { passwordNode.boundingBox = it.boundsInWindow() }
            .onFocusChanged { focus ->
                autofill?.run {
                    if (focus.isFocused) requestAutofillForNode(passwordNode)
                    else cancelAutofillForNode(passwordNode)
                }
            },
        isPassword = true,
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
    // Anzeige-Modus (E-Ink ⟷ Smartphone) gehört zur Darstellung — eigene Gruppe ganz oben, über dem
    // Erscheinungsbild. Steuert Bewegung/Akzentfarbe (DisplayBehavior); orthogonal zum Layout-Modus (Reader).
    val displayModeStr by viewModel.displayMode.collectAsState()
    val displayMode = runCatching { DisplayMode.valueOf(displayModeStr) }.getOrDefault(DisplayMode.EINK)
    val displayLabel: (DisplayMode) -> String = { dm ->
        when (dm) {
            DisplayMode.EINK -> s.displayEink
            DisplayMode.SMARTPHONE -> s.displaySmartphone
        }
    }
    val themeModeStr by viewModel.themeMode.collectAsState()
    val themeMode = runCatching { ThemeMode.valueOf(themeModeStr) }.getOrDefault(ThemeMode.SYSTEM)
    // Externer UI-Pack (L2): „Standard" + jeder installierte data-only UI-Pack (analog Sprach-Picker).
    val activeUiPack by viewModel.activeUiPack.collectAsState()
    val uiPacks by viewModel.availableUiPacks.collectAsState()

    // Eigener Column-Root (wie [ReaderSettingsContent]): der Host platziert die Section-`content` in einer
    // Box (SettingsScreen.kt), die mehrere Geschwister ÜBEREINANDER stapeln würde — die Gruppen
    // (Anzeige-Modus + Theme + UI-Pack) müssen vertikal gestapelt werden, sonst überlappen sie.
    Column(verticalArrangement = Arrangement.spacedBy(EinkTokens.sectionGap)) {
        SettingsGroup(s.settingsDisplayMode, query) {
            DisplayMode.entries.forEach { mode ->
                ChoiceRow(displayLabel(mode), selected = mode == displayMode, query = query, dense = true) {
                    viewModel.setDisplayMode(mode.name)
                }
            }
        }
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

        SettingsGroup(s.settingsUiPack, query) {
            ChoiceRow(s.uiPackDefault, selected = activeUiPack.isBlank(), query = query, dense = true) {
                viewModel.setActiveUiPack("")
            }
            uiPacks.forEach { spec ->
                ChoiceRow(spec.displayName, selected = spec.packageName == activeUiPack, query = query, dense = true) {
                    viewModel.setActiveUiPack(spec.packageName)
                }
            }
        }
    }
}

/**
 * Reader-Einstellungen als **scope-gruppierte Hierarchie**: dominante [ScopeHeader]
 * gliedern nach Lese-Kontext (Allgemein · Roman-Reader · Webtoon · Comic) statt nach
 * neun gleichrangigen Flach-Gruppen. Jeder Scope rendert seine Zeilen über die geteilten,
 * wiederverwendbaren Settings-Bausteine — kein Inline-Sonderwidget.
 */
@Composable
fun ReaderSettingsContent(viewModel: SettingsViewModel, query: String) {
    Column(verticalArrangement = Arrangement.spacedBy(EinkTokens.sectionGap)) {
        GeneralScope(viewModel, query)
        NovelScope(viewModel, query)
        WebtoonScope(viewModel, query)
        ComicScope(viewModel, query)
    }
}

/** Scope „Allgemein": Anzeige-Modus (Wert + Picker-Modal) und Reader-Presets. */
@Composable
private fun GeneralScope(viewModel: SettingsViewModel, query: String) {
    val s = LocalStrings.current
    val shellLayoutModeStr by viewModel.shellLayoutMode.collectAsState()
    val shellLayoutMode = runCatching { ShellLayoutMode.valueOf(shellLayoutModeStr) }.getOrDefault(ShellLayoutMode.AUTO)
    val presets by viewModel.readerPresets.collectAsState()

    // Genau EIN Modal gleichzeitig (E-Ink-Invariante): null = zu.
    var showShellLayoutPicker by remember { mutableStateOf(false) }
    var confirmPreset by remember { mutableStateOf<ReaderPreset?>(null) }
    val shellLayoutLabel: (ShellLayoutMode) -> String = { mode ->
        when (mode) {
            ShellLayoutMode.AUTO -> s.shellLayoutAuto
            ShellLayoutMode.COMPACT -> s.shellLayoutCompact
            ShellLayoutMode.EXPANDED -> s.shellLayoutExpanded
        }
    }

    Column {
        ScopeHeader(s.settingsScopeGeneral)
        Column(Modifier.padding(start = SettingsGroupIndent)) {
            // Form-Faktor des Home-Skeletts (Override). Der Anzeige-Modus lebt jetzt in der Darstellung.
            PickerRow(
                label = s.settingsShellLayout,
                value = shellLayoutLabel(shellLayoutMode),
                onClick = { showShellLayoutPicker = true },
                query = query,
            )
            // Reader-Presets: je installierten Preset eine ChoiceRow (analog Sprach-Picker).
            // Auswahl setzt confirmPreset → Bestätigungs-Modal → applyReaderPreset.
            if (presets.isEmpty()) {
                SettingsRow(label = s.readerPresetApply, query = query, helper = s.readerPresetNone) {}
            } else {
                presets.forEach { preset ->
                    ChoiceRow(
                        label = "${s.readerPresetApply}: ${preset.name}",
                        selected = false,
                        query = query,
                        dense = true,
                        onSelect = { confirmPreset = preset },
                    )
                }
            }
        }
    }

    if (showShellLayoutPicker) {
        PickerModal(
            title = s.settingsShellLayout,
            options = ShellLayoutMode.entries,
            selectedKey = shellLayoutMode.name,
            keyOf = { it.name },
            labelOf = shellLayoutLabel,
            onSelect = { viewModel.setShellLayoutMode(it) },
            onDismiss = { showShellLayoutPicker = false },
            closeLabel = s.close,
        )
    }

    confirmPreset?.let { p ->
        EinkModal(
            title = s.readerPresetConfirmTitle,
            onDismiss = { confirmPreset = null },
            confirmLabel = s.readerPresetApply,
            onConfirm = { viewModel.applyReaderPreset(p); confirmPreset = null },
            dismissLabel = s.cancel,
        ) {
            Text(s.readerPresetConfirmBody(p.name), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * Scope „Roman-Reader": die Roman-Typografie im nativen Settings-Stil. Stepper für Schrift,
 * Segment-Selektoren für Ränder/Ausrichtung/Silbentrennung, Wert+Picker-Modal für die
 * (lange) Schriftart-Liste. Gleiche Ranges/Presets/Fonts wie das In-Reader-Panel
 * ([NovelTypographyControls]) — alle aus [NovelSettings]/[NovelFonts] (SSOT). Das In-Reader-
 * Panel bleibt unberührt.
 */
@Composable
private fun NovelScope(viewModel: SettingsViewModel, query: String) {
    val s = LocalStrings.current
    val fontSizeEm by viewModel.novelFontSizeEm.collectAsState()
    val lineHeight by viewModel.novelLineHeight.collectAsState()
    val fontWeight by viewModel.novelFontWeight.collectAsState()
    val marginPreset by viewModel.novelMarginPreset.collectAsState()
    val textAlign by viewModel.novelTextAlign.collectAsState()
    val hyphenationLang by viewModel.novelHyphenationLang.collectAsState()
    val fontFamily by viewModel.novelFontFamily.collectAsState()
    val bookmarkMarkerStyle by viewModel.bookmarkMarkerStyle.collectAsState()
    val availableNovelFonts by viewModel.availableNovelFonts.collectAsState()
    val fontSampleFiles by viewModel.fontSampleFiles.collectAsState()

    // Precompute FontFamily per family key to avoid rebuilding on each recomposition.
    val fontFamilyMap = remember(fontSampleFiles) {
        fontSampleFiles.mapValues { (_, file) ->
            runCatching { FontFamily(Font(file)) }.getOrNull()
        }
    }

    // Genau EIN Modal gleichzeitig (E-Ink-Invariante).
    var showFontPicker by remember { mutableStateOf(false) }

    Column {
        ScopeHeader(s.settingsScopeNovel)
        Column(Modifier.padding(start = SettingsGroupIndent)) {
            // Schrift: Größe, Zeilenabstand, Gewicht — identisches Format zum In-Reader-Panel.
            FieldCaption(s.novelTextHeading)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                CompactStepperRow(
                    label = s.novelFontSize,
                    valueText = "${(fontSizeEm * 100).toInt()} %",
                    onDecrement = { viewModel.setNovelFontSizeEm((fontSizeEm - NovelSettings.FONT_SIZE_STEP).coerceAtLeast(NovelSettings.FONT_SIZE_MIN)) },
                    onIncrement = { viewModel.setNovelFontSizeEm((fontSizeEm + NovelSettings.FONT_SIZE_STEP).coerceAtMost(NovelSettings.FONT_SIZE_MAX)) },
                )
                CompactStepperRow(
                    label = s.novelLineHeight,
                    valueText = "${(lineHeight * 100).toInt()} %",
                    onDecrement = { viewModel.setNovelLineHeight((lineHeight - NovelSettings.LINE_HEIGHT_STEP).coerceAtLeast(NovelSettings.LINE_HEIGHT_MIN)) },
                    onIncrement = { viewModel.setNovelLineHeight((lineHeight + NovelSettings.LINE_HEIGHT_STEP).coerceAtMost(NovelSettings.LINE_HEIGHT_MAX)) },
                )
                CompactStepperRow(
                    label = s.novelFontWeight,
                    valueText = "+${(fontWeight - NovelSettings.FONT_WEIGHT_MIN) / NovelSettings.FONT_WEIGHT_STEP}",
                    onDecrement = { viewModel.setNovelFontWeight((fontWeight - NovelSettings.FONT_WEIGHT_STEP).coerceAtLeast(NovelSettings.FONT_WEIGHT_MIN)) },
                    onIncrement = { viewModel.setNovelFontWeight((fontWeight + NovelSettings.FONT_WEIGHT_STEP).coerceAtMost(NovelSettings.FONT_WEIGHT_MAX)) },
                )
            }
            // Ränder/Ausrichtung/Silbentrennung als Inline-Segment-Selektoren.
            SegmentedChoiceRow(
                label = s.novelMargin,
                options = listOf(
                    SegmentOption(NovelSettings.MARGIN_NARROW, s.novelMarginNarrow),
                    SegmentOption(NovelSettings.MARGIN_NORMAL, s.novelMarginNormal),
                    SegmentOption(NovelSettings.MARGIN_WIDE, s.novelMarginWide),
                ),
                selectedKey = marginPreset,
                onSelect = { viewModel.setNovelMarginPreset(it) },
                query = query,
            )
            SegmentedChoiceRow(
                label = s.novelTextAlign,
                options = listOf(
                    SegmentOption("LEFT", s.novelAlignLeft),
                    SegmentOption("JUSTIFY", s.novelAlignJustify),
                ),
                selectedKey = textAlign,
                onSelect = { viewModel.setNovelTextAlign(it) },
                query = query,
            )
            HyphenationPicker(
                value = hyphenationLang,
                onValue = { viewModel.setNovelHyphenationLang(it) },
                query = query,
            )
            SegmentedChoiceRow(
                label = s.novelBookmarks,
                options = listOf(
                    SegmentOption(BookmarkMarkerStyle.UNDERLINE.name, s.novelBookmarkMarkerUnderline),
                    SegmentOption(BookmarkMarkerStyle.MARGIN.name, s.novelBookmarkMarkerMargin),
                ),
                selectedKey = bookmarkMarkerStyle,
                onSelect = { viewModel.setBookmarkMarkerStyle(it) },
                query = query,
            )
            // Schriftart: lange Liste → Wert+Chevron → Picker-Modal.
            // Use availableNovelFonts for label lookup so plugin fonts show their real name.
            val currentFontLabel = availableNovelFonts.firstOrNull { it.family == fontFamily }?.label
                ?: NovelFonts.byFamily(fontFamily).label
            PickerRow(
                label = s.novelFontFamily,
                value = currentFontLabel,
                onClick = { showFontPicker = true },
                query = query,
            )
        }
    }

    if (showFontPicker) {
        PickerModal(
            title = s.novelFontFamily,
            options = availableNovelFonts,
            selectedKey = fontFamily,
            keyOf = { it.family },
            labelOf = { it.label },
            onSelect = { viewModel.setNovelFontFamily(it) },
            onDismiss = { showFontPicker = false },
            closeLabel = s.close,
            labelFontFamilyOf = { font -> fontFamilyMap[font.family] },
        )
    }
}

/** Scope „Webtoon": die Streifen-Überlappung. */
@Composable
private fun WebtoonScope(viewModel: SettingsViewModel, query: String) {
    val s = LocalStrings.current
    val overlap by viewModel.webtoonOverlapPercent.collectAsState()
    Column {
        ScopeHeader(s.settingsScopeWebtoon)
        Column(Modifier.padding(start = SettingsGroupIndent)) {
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
    }
}

/** Scope „Comic (Guided)": ML-Erkennung + Panel-Rahmen einblenden. */
@Composable
private fun ComicScope(viewModel: SettingsViewModel, query: String) {
    val s = LocalStrings.current
    val useMlDetection by viewModel.useMlDetection.collectAsState()
    val panelOverlay by viewModel.guidedPanelOverlay.collectAsState()
    Column {
        ScopeHeader(s.settingsScopeComic)
        Column(Modifier.padding(start = SettingsGroupIndent)) {
            SwitchRow(
                label = s.readerUseMlDetection,
                checked = useMlDetection,
                onCheckedChange = { viewModel.setUseMlDetection(it) },
                query = query,
            )
            SwitchRow(
                label = s.readerPanelOverlay,
                checked = panelOverlay,
                onCheckedChange = { viewModel.setGuidedPanelOverlay(it) },
                query = query,
            )
        }
    }
}

/**
 * Wandelt einen SAF-Tree-URI in einen voll qualifizierten, lesbaren Pfad
 * (z. B. `/storage/emulated/0/Download/LocalTest`). Best-effort: bei unbekanntem
 * Volume/Format fällt es auf den rohen URI-String zurück.
 */
private fun treeUriToDisplayPath(uriString: String): String = runCatching {
    val docId = DocumentsContract.getTreeDocumentId(Uri.parse(uriString))
    val parts = docId.split(":", limit = 2)
    val volume = parts[0]
    val relative = parts.getOrElse(1) { "" }
    val base = if (volume.equals("primary", ignoreCase = true)) "/storage/emulated/0" else "/storage/$volume"
    if (relative.isEmpty()) base else "$base/$relative"
}.getOrElse { uriString }

@Composable
fun DownloadsSettingsContent(viewModel: SettingsViewModel, query: String) {
    val s = LocalStrings.current
    val ctx = LocalContext.current
    val downloadDir by viewModel.downloadDir.collectAsState()
    val localFolder by viewModel.localFolderUri.collectAsState()

    fun takeTreePermission(uri: Uri) = ctx.contentResolver.takePersistableUriPermission(
        uri,
        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
    )
    fun folderName(uri: Uri): String =
        androidx.documentfile.provider.DocumentFile.fromTreeUri(ctx, uri)?.name ?: "Local"

    // Download-Ordner (Server-Downloads landen hier). Setzt den lokalen Ordner gleich mit,
    // damit geräte-lokale Werke standardmäßig im selben Ordner liegen (überschreibbar unten).
    val downloadPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri != null) {
            takeTreePermission(uri)
            viewModel.setBothFolders(folderName(uri), uri.toString())
        }
    }
    // Lokaler Ordner (geräte-lokale Werke ohne Server — intern die LOCAL-Quelle).
    val localPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri != null) {
            takeTreePermission(uri)
            viewModel.saveLocalFolder(folderName(uri), uri.toString())
        }
    }
    val externalOpenBehavior by viewModel.externalOpenBehavior.collectAsState()

    val downloadPath = downloadDir?.let { treeUriToDisplayPath(it) } ?: s.defaultFolder
    val localPath = localFolder?.let { treeUriToDisplayPath(it) } ?: s.localFolderNotSet

    Column(verticalArrangement = Arrangement.spacedBy(EinkTokens.sectionGap)) {
        SettingsGroup(s.downloadFolder, query) {
            FolderPickerRow(
                path = downloadPath,
                onClick = { downloadPicker.launch(null) },
                onReset = if (downloadDir != null) ({ viewModel.setDownloadDir(null) }) else null,
            )
        }

        SettingsGroup(s.serverKindLocal, query) {
            FolderPickerRow(
                path = localPath,
                onClick = { localPicker.launch(null) },
                onReset = if (localFolder != null) ({ viewModel.removeLocalFolder() }) else null,
            )
        }

        SettingsGroup(s.externalOpenSetting, query) {
            SegmentedChoiceRow(
                label = s.externalOpenSetting,
                options = listOf(
                    SegmentOption(ExternalOpenBehavior.ASK.name, s.externalOpenAsk),
                    SegmentOption(ExternalOpenBehavior.IMPORT.name, s.externalOpenImport),
                    SegmentOption(ExternalOpenBehavior.READ_ONLY.name, s.externalOpenReadOnly),
                ),
                selectedKey = externalOpenBehavior,
                onSelect = { viewModel.setExternalOpenBehavior(it) },
                query = query,
            )
        }
    }
}

@Composable
fun LanguageSettingsContent(viewModel: SettingsViewModel, query: String) {
    val s = LocalStrings.current
    val languageStr by viewModel.language.collectAsState()
    val installed by viewModel.availableLanguages.collectAsState()
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
        // Built-in-Codes nicht doppelt zeigen, falls ein Plugin de/en shadowt.
        installed.filterNot { it.code == Language.DE.code || it.code == Language.EN.code }.forEach { spec ->
            ChoiceRow(spec.name, selected = spec.code == languageStr, query = query, dense = true) {
                viewModel.setLanguage(spec.code)
            }
        }
    }
}

@Composable
fun AboutContent(query: String, viewModel: AppUpdateViewModel = hiltViewModel()) {
    val s = LocalStrings.current
    val updateState by viewModel.state.collectAsState()
    val installing by viewModel.installing.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val installResult by viewModel.result.collectAsState()
    SettingsGroup(s.appName, query) {
        HighlightText(
            s.aboutDevice, query, MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        AboutRow(s.versionLabel, BuildConfig.VERSION_NAME, query)
        AboutRow(s.aboutLicense, "AGPL-3.0-or-later", query)
        // Source code: a real, clickable link to the repo (display without scheme, target with https).
        AboutLinkRow(s.aboutSourceCode, s.aboutSourceCodeUrl, query)
        Spacer(Modifier.height(16.dp))
        UpdateSection(
            state = updateState,
            installing = installing,
            progress = progress,
            result = installResult,
            query = query,
            onCheck = { viewModel.check() },
            onInstall = { viewModel.install(it) },
        )
    }
}

/** Label on the left · clickable, underlined link on the right (opens `https://<value>` in the browser). */
@Composable
private fun AboutLinkRow(label: String, displayUrl: String, query: String) {
    val uriHandler = LocalUriHandler.current
    val accent = LocalDesignTokens.current.accent
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        HighlightText(
            displayUrl, query,
            MaterialTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.Underline),
            color = accent,
            modifier = Modifier.clickable { uriHandler.openUri("https://$displayUrl") },
        )
    }
}

/**
 * Update area: "Check for updates" button + status. When an update is available, instead shows
 * "Install update" (downloads the release APK + starts the OS installer).
 */
@Composable
private fun UpdateSection(
    state: AppUpdateState,
    installing: Boolean,
    progress: Float?,
    result: UpdateInstall?,
    query: String,
    onCheck: () -> Unit,
    onInstall: (ReleaseInfo) -> Unit,
) {
    val s = LocalStrings.current
    // Buttons + status centered horizontally. When an update is available, ONLY the install button
    // (no extra "check" button — would be doubled); otherwise the "check" button + status.
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (state is AppUpdateState.Available) {
            HighlightText(
                s.aboutUpdateAvailable(state.release.tag), query,
                MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.height(8.dp))
            // While downloading show "Lädt… NN %" (the APK is large — without the percent it looks frozen).
            val pct = progress?.let { " ${(it * 100).toInt()} %" }.orEmpty()
            EinkOutlinedButton(onClick = { onInstall(state.release) }, enabled = !installing) {
                AnimatedAppIcon(
                    imageVector = AppIcons.Download,
                    animation = IconAnimation.BobVertical,
                    running = installing,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (installing) s.aboutDownloading + pct else s.aboutInstallUpdate,
                    fontWeight = FontWeight.Bold,
                )
            }
            // Surface the outcome so a failure / missing permission is never silent.
            if (!installing) {
                when (result) {
                    UpdateInstall.STARTED -> { Spacer(Modifier.height(8.dp)); UpdateStatusLine(s.aboutUpdateStarted, query) }
                    UpdateInstall.NO_APK -> { Spacer(Modifier.height(8.dp)); UpdateStatusLine(s.aboutUpdateNoApk, query) }
                    UpdateInstall.FAILED -> { Spacer(Modifier.height(8.dp)); UpdateStatusLine(s.aboutUpdateFailed, query) }
                    UpdateInstall.NEEDS_PERMISSION -> { Spacer(Modifier.height(8.dp)); UpdateStatusLine(s.aboutUpdateNeedsPermission, query) }
                    null -> Unit
                }
            }
        } else {
            EinkOutlinedButton(onClick = onCheck, enabled = state != AppUpdateState.Checking) {
                AnimatedAppIcon(
                    imageVector = AppIcons.Refresh,
                    animation = IconAnimation.SpinClockwise,
                    running = state == AppUpdateState.Checking,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(s.aboutCheckUpdates, fontWeight = FontWeight.Bold)
            }
            when (state) {
                AppUpdateState.Checking -> { Spacer(Modifier.height(8.dp)); UpdateStatusLine(s.aboutChecking, query) }
                AppUpdateState.UpToDate -> { Spacer(Modifier.height(8.dp)); UpdateStatusLine(s.aboutUpToDate, query) }
                AppUpdateState.Unknown -> { Spacer(Modifier.height(8.dp)); UpdateStatusLine(s.aboutCheckFailed, query) }
                else -> Unit
            }
        }
    }
}

@Composable
private fun UpdateStatusLine(text: String, query: String) {
    HighlightText(
        text, query, MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Label-/Wert-Zeile im „Über"-Abschnitt: Label links (gedämpft), Wert rechts (suchbar). */
@Composable
private fun AboutRow(label: String, value: String, query: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        HighlightText(value, query, MaterialTheme.typography.bodyMedium)
    }
}
