package com.komgareader.app.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komgareader.app.data.PluginCatalog
import com.komgareader.app.data.SourceRegistration
import com.komgareader.app.data.SyncCoordinator
import com.komgareader.app.data.pluginServerConfig
import com.komgareader.plugin.host.DiscoveredPlugin
import com.komgareader.domain.eink.EinkContext
import com.komgareader.domain.eink.EinkContextProfile
import com.komgareader.domain.eink.EinkController
import com.komgareader.domain.eink.EinkModeOption
import com.komgareader.domain.model.BookmarkMarkerStyle
import com.komgareader.domain.model.ColorProfile
import com.komgareader.domain.model.ShellLayoutMode
import com.komgareader.domain.model.SourceKind
import com.komgareader.domain.render.NovelFonts
import com.komgareader.domain.repository.CollectionRepository
import com.komgareader.domain.repository.ColorProfileRepository
import com.komgareader.domain.repository.KomgaUrl
import com.komgareader.domain.repository.ServerConfig
import com.komgareader.domain.repository.ServerRepository
import com.komgareader.domain.repository.ReadingStatsRepository
import com.komgareader.domain.repository.SettingsRepository
import com.komgareader.domain.model.ReadingStats
import com.komgareader.domain.model.ReaderPreset
import com.komgareader.domain.usecase.ReaderPresetSink
import com.komgareader.domain.usecase.applyReaderPreset
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val servers: ServerRepository,
    private val colorProfiles: ColorProfileRepository,
    private val registration: SourceRegistration,
    private val collections: CollectionRepository,
    private val coordinator: SyncCoordinator,
    private val catalog: PluginCatalog,
    private val einkController: EinkController,
    @ApplicationContext private val context: Context,
    private val readingStats: ReadingStatsRepository,
) : ViewModel() {
    val themeMode = settings.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, "SYSTEM")
    val language = settings.language.stateIn(viewModelScope, SharingStarted.Eagerly, "de")
    val statsState: kotlinx.coroutines.flow.StateFlow<ReadingStats> =
        readingStats.observeStats()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReadingStats())
    val displayMode = settings.displayMode.stateIn(viewModelScope, SharingStarted.Eagerly, "EINK")
    val shellLayoutMode =
        settings.shellLayoutMode.stateIn(viewModelScope, SharingStarted.Eagerly, ShellLayoutMode.AUTO.name)
    val downloadDir = settings.downloadDir.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * SAF-Tree-URI des lokalen Ordners (die eine `SourceKind.LOCAL`-Quelle) oder null.
     * Abgeleitet aus der Server-Liste — der lokale Ordner wird unter „Downloads" verwaltet,
     * intern bleibt es eine LOCAL-Quelle.
     */
    val localFolderUri = servers.configs
        .map { list -> list.firstOrNull { it.kind == SourceKind.LOCAL }?.baseUrl }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val guidedPanelOverlay = settings.guidedPanelOverlay.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val useMlDetection = settings.useMlDetection.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    // E-Ink Dynamics: device capabilities (static) + per-context overrides (persistent).
    val einkRefreshModes: List<EinkModeOption> = einkController.capabilities.refreshModes
    val einkColorModes: List<EinkModeOption> = einkController.capabilities.colorModes
    val einkContextProfiles = settings.einkContextProfiles
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val webtoonOverlapPercent =
        settings.webtoonOverlapPercent.stateIn(viewModelScope, SharingStarted.Eagerly, 25)
    // Roman-Typografie (global). Gespiegelt aus demselben SettingsRepository wie das In-Reader-Panel
    // — eine Quelle, beide Mount-Punkte schreiben hierher (DRY). Defaults = SettingsRepository-Defaults.
    val novelFontSizeEm = settings.novelFontSizeEm.stateIn(viewModelScope, SharingStarted.Eagerly, 1.0f)
    val novelLineHeight = settings.novelLineHeight.stateIn(viewModelScope, SharingStarted.Eagerly, 1.0f)
    val novelMarginPreset = settings.novelMarginPreset.stateIn(viewModelScope, SharingStarted.Eagerly, "NORMAL")
    val novelFontFamily = settings.novelFontFamily.stateIn(viewModelScope, SharingStarted.Eagerly, NovelFonts.DEFAULT)
    val novelTextAlign = settings.novelTextAlign.stateIn(viewModelScope, SharingStarted.Eagerly, "JUSTIFY")
    val novelHyphenationLang = settings.novelHyphenationLang.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val novelFontWeight = settings.novelFontWeight.stateIn(viewModelScope, SharingStarted.Eagerly, 400)
    val bookmarkMarkerStyle =
        settings.bookmarkMarkerStyle.stateIn(viewModelScope, SharingStarted.Eagerly, BookmarkMarkerStyle.UNDERLINE.name)
    /** Alle konfigurierten Server (mehrere gleichzeitig, gemischte Quellenarten). */
    val serverList = servers.configs.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val activeColorProfile = colorProfiles.observeActive()
        .stateIn(viewModelScope, SharingStarted.Eagerly, ColorProfile.OFF)

    val availableLanguages = catalog.languagePlugins
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val readerPresets = catalog.readerPresetPlugins
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Aktiver UI-Pack (packageName, "" = keiner) + die installierten UI-Packs für den Picker. */
    val activeUiPack = settings.activeUiPack.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val availableUiPacks = catalog.uiPackPlugins.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Entdeckte Quellen-Plugins (für den „Plugin"-Segment im Add-Server-Modal). */
    val sourcePlugins = catalog.sources
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val availableNovelFonts =
        catalog.allNovelFonts.stateIn(viewModelScope, SharingStarted.Eagerly, NovelFonts.ALL)
    val fontSampleFiles =
        catalog.fontSampleFiles.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /**
     * Persistiert eine bestätigte Plugin-Quelle als [ServerConfig] (kind = PLUGIN).
     * Spiegelt [PluginsViewModel.addPluginSource] — beide nutzen [pluginServerConfig] (DRY).
     */
    fun addPluginSource(plugin: DiscoveredPlugin, values: Map<String, String>) =
        viewModelScope.launch {
            servers.save(pluginServerConfig(plugin, values))
            coordinator.onServerChanged()
        }.let {}

    /**
     * Persists a local-folder source (kind = LOCAL): the SAF tree-uri is stored in [ServerConfig.baseUrl];
     * a local folder needs no url/apiKey/credentials. The picker already took the persistable permission.
     */
    /**
     * Setzt (oder ersetzt) den lokalen Ordner — die eine `SourceKind.LOCAL`-Quelle. Upsert über die
     * bestehende Rowid, damit kein zweiter LOCAL-Eintrag entsteht (V1 = genau ein lokaler Ordner).
     * Wird unter „Downloads" aufgerufen; die persistierbaren Leserechte nimmt die UI vor dem Aufruf.
     */
    fun saveLocalFolder(name: String, treeUri: String) = viewModelScope.launch {
        val existingId = servers.configs.first().firstOrNull { it.kind == SourceKind.LOCAL }?.id ?: 0L
        servers.save(ServerConfig(name = name, baseUrl = treeUri, kind = SourceKind.LOCAL, id = existingId))
        coordinator.onServerChanged()
    }.let {}

    /** Entfernt den lokalen Ordner (LOCAL-Quelle): SAF-Recht freigeben, Eintrag + dessen Daten löschen. */
    fun removeLocalFolder() = viewModelScope.launch {
        val cfg = servers.configs.first().firstOrNull { it.kind == SourceKind.LOCAL } ?: return@launch
        releaseTreePermission(cfg.baseUrl)
        servers.remove(cfg.id)
        registration.sourceIdOf(cfg)?.let { collections.removeSource(it) }
        // Mirror the change into the downloads table (the local rows are now stale → cleared).
        coordinator.onServerChanged()
    }.let {}

    /** Setzt Download-Ordner UND lokalen Ordner auf denselben SAF-Ordner. */
    fun setBothFolders(name: String, treeUri: String) {
        setDownloadDir(treeUri)
        saveLocalFolder(name, treeUri)
    }

    private fun releaseTreePermission(treeUri: String) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                android.net.Uri.parse(treeUri),
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }

    fun applyReaderPreset(preset: ReaderPreset) {
        applyReaderPreset(
            preset.overrides,
            ReaderPresetSink(
                setDisplayMode = ::setDisplayMode,
                setWebtoonOverlapPercent = ::setWebtoonOverlap,
                setNovelFontSizeEm = ::setNovelFontSizeEm,
                setNovelLineHeight = ::setNovelLineHeight,
                setNovelMarginPreset = ::setNovelMarginPreset,
                setNovelFontFamily = ::setNovelFontFamily,
                setNovelTextAlign = ::setNovelTextAlign,
                setNovelHyphenationLang = ::setNovelHyphenationLang,
                setNovelFontWeight = ::setNovelFontWeight,
                setGuidedPanelOverlay = ::setGuidedPanelOverlay,
            ),
        )
    }

    fun setTheme(value: String) = viewModelScope.launch { settings.setThemeMode(value) }.let {}
    fun setLanguage(value: String) = viewModelScope.launch { settings.setLanguage(value) }.let {}
    fun setDisplayMode(value: String) = viewModelScope.launch { settings.setDisplayMode(value) }.let {}
    fun setShellLayoutMode(value: String) =
        viewModelScope.launch { settings.setShellLayoutMode(value) }.let {}
    fun setActiveUiPack(packageName: String) =
        viewModelScope.launch { settings.setActiveUiPack(packageName) }.let {}
    fun setDownloadDir(uri: String?) = viewModelScope.launch { settings.setDownloadDir(uri) }.let {}
    fun setGuidedPanelOverlay(value: Boolean) = viewModelScope.launch { settings.setGuidedPanelOverlay(value) }.let {}
    fun setUseMlDetection(value: Boolean) = viewModelScope.launch { settings.setUseMlDetection(value) }.let {}

    fun setEinkRefreshMode(context: EinkContext, id: String?) {
        val current = einkContextProfiles.value[context] ?: EinkContextProfile()
        viewModelScope.launch {
            settings.setEinkContextProfile(context, current.copy(refreshModeId = id))
        }
    }

    fun setEinkColorMode(context: EinkContext, id: String?) {
        val current = einkContextProfiles.value[context] ?: EinkContextProfile()
        viewModelScope.launch {
            settings.setEinkContextProfile(context, current.copy(colorModeId = id))
        }
    }

    fun setWebtoonOverlap(percent: Int) =
        viewModelScope.launch { settings.setWebtoonOverlapPercent(percent) }.let {}
    fun setNovelFontSizeEm(value: Float) = viewModelScope.launch { settings.setNovelFontSizeEm(value) }.let {}
    fun setNovelLineHeight(value: Float) = viewModelScope.launch { settings.setNovelLineHeight(value) }.let {}
    fun setNovelFontWeight(value: Int) = viewModelScope.launch { settings.setNovelFontWeight(value) }.let {}
    fun setNovelMarginPreset(preset: String) = viewModelScope.launch { settings.setNovelMarginPreset(preset) }.let {}
    fun setNovelTextAlign(align: String) = viewModelScope.launch { settings.setNovelTextAlign(align) }.let {}
    fun setNovelHyphenationLang(lang: String) = viewModelScope.launch { settings.setNovelHyphenationLang(lang) }.let {}
    fun setNovelFontFamily(family: String) = viewModelScope.launch { settings.setNovelFontFamily(family) }.let {}
    fun setBookmarkMarkerStyle(value: String) =
        viewModelScope.launch { settings.setBookmarkMarkerStyle(value) }.let {}
    /**
     * Legt eine Verbindung an ([id] == 0) oder aktualisiert sie in-place ([id] != 0 = Bearbeiten).
     * [save] ist ein Upsert über die Rowid — der Bearbeiten-Pfad reicht einfach die bestehende [id] durch.
     */
    fun saveServer(
        name: String,
        baseUrl: String,
        apiKey: String,
        username: String,
        password: String,
        kind: SourceKind = SourceKind.KOMGA,
        id: Long = 0,
    ) = viewModelScope.launch {
        servers.save(
            ServerConfig(
                // OPDS-URLs nicht Komga-normalisieren (Feed-Pfad bleibt wie eingegeben).
                name = name,
                baseUrl = if (kind == SourceKind.OPDS) baseUrl.trim() else KomgaUrl.normalize(baseUrl),
                apiKey = apiKey.trimToNull(),
                username = username.trimToNull(),
                password = password.trimToNull(),
                kind = kind,
                id = id,
            )
        )
        // Server verbunden (neu/aktualisiert) → dessen Sammlungen NUR pullen (nie lokale pushen).
        coordinator.onServerChanged()
    }.let {}

    private fun String.trimToNull(): String? = trim().ifBlank { null }

    /** Entfernt eine Server-Verbindung (per Rowid) und räumt die lokalen Sammlungs-Daten dieser Quelle auf. */
    fun removeServer(id: Long) = viewModelScope.launch {
        val cfg = servers.configs.first().firstOrNull { it.id == id }
        // Local-folder source: release the persisted SAF permission so we don't leak grants.
        if (cfg?.kind == SourceKind.LOCAL) {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    android.net.Uri.parse(cfg.baseUrl),
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        servers.remove(id)
        cfg?.let { registration.sourceIdOf(it) }?.let { collections.removeSource(it) }
    }.let {}
}
