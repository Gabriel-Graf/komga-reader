package com.komgareader.app.ui.home

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import com.komgareader.app.ui.components.LocalEinkMode
import com.mikepenz.markdown.coil2.Coil2ImageTransformerImpl
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.model.markdownAnimations
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.hilt.navigation.compose.hiltViewModel
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.i18n.localizedContentType
import com.komgareader.app.ui.collections.CollectionDetailScreen
import com.komgareader.app.ui.collections.CollectionsScreen
import com.komgareader.app.ui.components.RotatingViewModeButton
import com.komgareader.app.ui.components.StatusCluster
import com.komgareader.app.ui.components.SyncIconButton
import com.komgareader.app.ui.components.TileViewMode
import com.komgareader.app.ui.components.PluginFilterMenu
import com.komgareader.app.ui.components.TypeFilterMenu
import com.komgareader.app.ui.components.tileViewModeOf
import com.komgareader.app.ui.groups.GroupsScreen
import com.komgareader.ui.icons.AppIcons
import com.komgareader.app.ui.library.LibraryScreen
import com.komgareader.app.ui.library.LibraryViewModel
import com.komgareader.app.ui.plugins.PluginsScreen
import com.komgareader.app.ui.settings.SettingsScreen
import com.komgareader.app.ui.settings.SettingsViewModel
import com.komgareader.app.ui.settings.AppUpdateViewModel
import com.komgareader.app.data.AppUpdateState
import com.komgareader.app.data.hasUpdate
import com.komgareader.app.ui.components.EinkInfoDialog
import com.komgareader.data.update.ReleaseInfo
import com.komgareader.ui.theme.EinkTokens
import kotlinx.coroutines.delay
import com.komgareader.ui.shell.AppShellState
import com.komgareader.ui.shell.ShellDestination
import com.komgareader.ui.shell.ShellDestinationId
import com.komgareader.app.ui.pack.shellOverride
import com.komgareader.app.ui.shell.ShellPackRegistry
import com.komgareader.ui.shell.resolveFormFactor
import com.komgareader.ui.slots.HomeHeaderFilter
import com.komgareader.ui.slots.HomeHeaderSearch
import com.komgareader.ui.slots.HomeHeaderState
import com.komgareader.ui.slots.SettingsSectionId
import com.komgareader.domain.model.ShellLayoutMode
import com.komgareader.ui.theme.LocalDesignTokens
import com.komgareader.data.plugin.repo.PluginTypeFilter
import com.komgareader.app.ui.shell.auroraShellOverride
import com.komgareader.domain.model.ContentType
import com.komgareader.domain.model.DisplayMode
import com.komgareader.app.ui.reader.EinkContextEffect
import com.komgareader.domain.eink.EinkContext

private const val TAB_LIBRARY = 0
private const val TAB_COLLECTIONS = 1
private const val TAB_GROUPS = 2
private const val TAB_PLUGINS = 3
private const val TAB_SETTINGS = 4

/**
 * App-Gerüst mit Onyx-Bottom-Menubar und persistenter Suchzeile in der TopBar.
 * Die Suche durchsucht standardmäßig die Bibliothek (Ergebnisse erscheinen dort);
 * auf dem Einstellungs-Tab durchsucht sie stattdessen die Einstellungen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSeries: (seriesId: String, sourceId: Long) -> Unit,
    onOpenGroup: (shelfId: Long, serverSourceId: Long) -> Unit,
) {
    val s = LocalStrings.current
    var selected by rememberSaveable { mutableIntStateOf(TAB_LIBRARY) }
    var query by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf("") }
    val typeFilter = rememberSaveable(
        saver = listSaver<MutableState<Set<ContentType>>, String>(
            save = { it.value.map(ContentType::name) },
            restore = { mutableStateOf(it.map(ContentType::valueOf).toSet()) },
        ),
    ) { mutableStateOf(emptySet()) }
    var downloadedOnly by rememberSaveable { mutableStateOf(false) }
    var filterMenuOpen by remember { mutableStateOf(false) }
    var filterAnchor by remember { mutableStateOf(IntOffset.Zero) }
    var pluginFilterMenuOpen by remember { mutableStateOf(false) }
    var pluginFilterAnchor by remember { mutableStateOf(IntOffset.Zero) }
    var showCreateGroup by remember { mutableStateOf(false) }
    var showCreateCollection by remember { mutableStateOf(false) }
    // Collections-Tab: Liste ⇄ Detail als interner Zustand (hochgezogen, damit die TopBar-Aktionen
    // wissen, ob die Liste oder ein Detail sichtbar ist).
    var openCollectionId by rememberSaveable { mutableStateOf<Long?>(null) }
    val libraryVm: LibraryViewModel = hiltViewModel()
    // Dieselbe VM-Instanz wie in den Tab-Screens (gemeinsamer NavBackStackEntry) — die TopBar
    // liest/setzt den persistierten Ansichtsmodus, die Screens rendern danach.
    val groupsVm: com.komgareader.app.ui.groups.GroupsViewModel = hiltViewModel()
    val collectionsVm: com.komgareader.app.ui.collections.CollectionsViewModel = hiltViewModel()
    val pluginsVm: com.komgareader.app.ui.plugins.PluginsViewModel = hiltViewModel()
    val settingsVm: SettingsViewModel = hiltViewModel()
    val updateVm: AppUpdateViewModel = hiltViewModel()
    val updateState by updateVm.state.collectAsState()
    val releaseNotes by updateVm.releaseNotes.collectAsState()
    // App start: check for updates + check whether one was just installed (→ "what's new" notes).
    LaunchedEffect(Unit) {
        updateVm.check()
        updateVm.checkJustUpdated()
    }
    // Start banner: shows the update message for 4s as soon as an update is found — exactly ONCE per
    // process (rememberSaveable survives rotation). E-Ink: appears/disappears instantly (no fade).
    var bannerShown by rememberSaveable { mutableStateOf(false) }
    var bannerVisible by remember { mutableStateOf(false) }
    LaunchedEffect(updateState) {
        if (updateState.hasUpdate && !bannerShown) {
            bannerShown = true
            bannerVisible = true
            delay(4000)
            bannerVisible = false
        }
    }
    // Deep link from the update banner: open the settings tab pre-selecting "About". Cleared on any
    // manual tab change (see selectTab) so it acts as a one-shot.
    var requestedSettingsSection by remember { mutableStateOf<SettingsSectionId?>(null) }
    val pluginTypeFilter by pluginsVm.typeFilter.collectAsState()
    var showRepoMgmt by remember { mutableStateOf(false) }
    val groupsViewMode = tileViewModeOf(groupsVm.viewMode.collectAsState().value, TileViewMode.LIST)
    val collectionsViewMode = tileViewModeOf(collectionsVm.viewMode.collectAsState().value, TileViewMode.LARGE_TILE)

    // Sync/reload buttons spin while their work runs (min-duration latch in each VM so even an
    // instant/offline sync shows a turn — a conflated true→false pulse never animates on E-Ink).
    val libraryRefreshing by libraryVm.refreshing.collectAsState()
    val collectionsSyncing by collectionsVm.syncing.collectAsState()
    val pluginsReloading by pluginsVm.reloading.collectAsState()

    val onSettingsTab = selected == TAB_SETTINGS

    fun submitSearch() {
        submitted = query
        // Medien-Suche zeigt ihre Treffer in der Bibliothek — dorthin wechseln.
        if (!onSettingsTab) selected = TAB_LIBRARY
    }

    // Geteilte Such-Capability — auf allen Tabs gleich (Placeholder/Clear je Tab variiert), genau wie
    // der bisherige eine HomeHeaderState in der TopBar. Wird in jeden Tab-Header eingesetzt.
    val sharedSearch = HomeHeaderSearch(
        query = query,
        onQueryChange = {
            query = it
            if (selected == TAB_PLUGINS) pluginsVm.setQuery(it)
        },
        onSubmit = { submitSearch() },
        placeholder = if (onSettingsTab) s.searchSettingsHint
            else if (selected == TAB_PLUGINS) s.pluginSearchHint
            else s.searchMediaHint,
        actionLabel = s.searchAction,
        clearLabel = s.clearSearch,
        onClear = {
            query = ""
            submitted = ""
            if (selected == TAB_PLUGINS) pluginsVm.setQuery("")
        },
        leading = if (selected == TAB_LIBRARY && (typeFilter.value.isNotEmpty() || downloadedOnly)) {
            {
                typeFilter.value.forEach { type ->
                    TypeFilterChip(
                        label = s.localizedContentType(type),
                        onRemove = { typeFilter.value = typeFilter.value - type },
                    )
                }
                if (downloadedOnly) {
                    TypeFilterChip(label = s.filterDownloaded, onRemove = { downloadedOnly = false })
                }
            }
        } else null,
    )

    fun headerOf(
        title: String = "",
        filter: HomeHeaderFilter? = null,
        menu: @Composable () -> Unit = {},
        actions: @Composable RowScope.() -> Unit = {},
    ) = HomeHeaderState(
        status = { StatusCluster() },
        title = title,
        search = sharedSearch,
        filter = filter,
        menu = menu,
        actions = actions,
    )

    val libraryHeader = headerOf(
        title = s.tabBrowse,
        filter = HomeHeaderFilter(
            icon = AppIcons.Filter,
            contentDescription = s.filterByType,
            onClick = { filterMenuOpen = true },
            onAnchor = { filterAnchor = it },
        ),
        menu = {
            if (filterMenuOpen && selected == TAB_LIBRARY) {
                TypeFilterMenu(
                    anchor = filterAnchor,
                    selected = typeFilter.value,
                    onToggle = { type ->
                        typeFilter.value =
                            if (type in typeFilter.value) typeFilter.value - type
                            else typeFilter.value + type
                    },
                    downloadedSelected = downloadedOnly,
                    onToggleDownloaded = { downloadedOnly = !downloadedOnly },
                    onDismiss = { filterMenuOpen = false },
                )
            }
        },
        actions = {
            SyncIconButton(
                onClick = { libraryVm.refresh() },
                syncing = libraryRefreshing,
                contentDescription = null,
            )
        },
    )

    // Sammlungen-Liste: „+" (neue Sammlung) + rotierender Ansichts-Button + manueller Voll-Sync.
    val collectionsHeader = headerOf(
        title = s.collections,
        actions = {
            IconButton(onClick = { showCreateCollection = true }) {
                Icon(AppIcons.Plus, contentDescription = s.newCollection)
            }
            RotatingViewModeButton(
                current = collectionsViewMode,
                onSelect = { collectionsVm.setViewMode(it.name) },
                listLabel = s.viewList,
                tileLabel = s.viewTile,
                largeTileLabel = s.viewLargeTile,
            )
            // Ganz rechts: manueller bidirektionaler Sync (push + pull) — dieselbe
            // Refresh-Mechanik wie der Bibliotheks-Reload, ruft den Voll-Sync.
            SyncIconButton(
                onClick = { collectionsVm.syncNow() },
                syncing = collectionsSyncing,
                contentDescription = s.collectionSyncNow,
            )
        },
    )

    // Bibliotheken: „+" (neue Bibliothek) + rotierender Ansichts-Button.
    val groupsHeader = headerOf(
        title = s.tabGroups,
        actions = {
            IconButton(onClick = { showCreateGroup = true }) {
                Icon(AppIcons.Plus, contentDescription = s.newGroup)
            }
            RotatingViewModeButton(
                current = groupsViewMode,
                onSelect = { groupsVm.setViewMode(it.name) },
                listLabel = s.viewList,
                tileLabel = s.viewTile,
                largeTileLabel = s.viewLargeTile,
            )
        },
    )

    // Plugins: „+" (externe Repos verwalten/hinzufügen) + manueller Reload (Repo-Fetch + Re-Scan).
    val pluginsHeader = headerOf(
        title = s.navPlugins,
        filter = HomeHeaderFilter(
            icon = AppIcons.Filter,
            contentDescription = s.filterByType,
            onClick = { pluginFilterMenuOpen = true },
            onAnchor = { pluginFilterAnchor = it },
        ),
        menu = {
            if (pluginFilterMenuOpen && selected == TAB_PLUGINS) {
                PluginFilterMenu(
                    anchor = pluginFilterAnchor,
                    selected = pluginTypeFilter,
                    onSelect = { pluginsVm.setTypeFilter(it) },
                    onDismiss = { pluginFilterMenuOpen = false },
                )
            }
        },
        actions = {
            IconButton(onClick = { showRepoMgmt = true }) {
                Icon(AppIcons.Plus, contentDescription = s.pluginManageRepos)
            }
            SyncIconButton(
                onClick = { pluginsVm.reload() },
                syncing = pluginsReloading,
                contentDescription = s.pluginReload,
            )
        },
    )

    val settingsHeader = headerOf(title = s.settingsTitle)

    // Im Sammlungs-Detail liefert CollectionDetailScreen seine EIGENE TopBar (Titel/Suche +
    // Aktionen) → die COLLECTIONS-Destination bekommt dort header = null (kein Shell-Header).
    val collectionDetailOpen = openCollectionId != null

    val destinations = listOf(
        ShellDestination(
            id = ShellDestinationId.LIBRARY,
            icon = AppIcons.Library,
            label = s.tabBrowse,
            header = libraryHeader,
            content = {
                LibraryScreen(
                    query = submitted,
                    typeFilter = typeFilter.value,
                    downloadedOnly = downloadedOnly,
                    onOpenSeries = onOpenSeries,
                    viewModel = libraryVm,
                )
            },
        ),
        ShellDestination(
            id = ShellDestinationId.COLLECTIONS,
            icon = AppIcons.Bookmark,
            label = s.collections,
            header = if (collectionDetailOpen) null else collectionsHeader,
            content = {
                val openId = openCollectionId
                if (openId == null) {
                    CollectionsScreen(
                        onOpenCollection = { openCollectionId = it },
                        onNewCollection = { showCreateCollection = true },
                        showCreateDialog = showCreateCollection,
                        onDismissCreate = { showCreateCollection = false },
                    )
                } else {
                    BackHandler { openCollectionId = null }
                    CollectionDetailScreen(
                        collectionId = openId,
                        onBack = { openCollectionId = null },
                        onOpenSeries = onOpenSeries,
                    )
                }
            },
        ),
        ShellDestination(
            id = ShellDestinationId.GROUPS,
            icon = AppIcons.Groups,
            label = s.tabGroups,
            header = groupsHeader,
            content = {
                GroupsScreen(
                    onOpenGroup = onOpenGroup,
                    showCreateDialog = showCreateGroup,
                    onDismissCreate = { showCreateGroup = false },
                )
            },
        ),
        ShellDestination(
            id = ShellDestinationId.PLUGINS,
            icon = AppIcons.Plugins,
            label = s.navPlugins,
            header = pluginsHeader,
            content = {
                PluginsScreen(
                    showRepoManagement = showRepoMgmt,
                    onRepoManagementDismiss = { showRepoMgmt = false },
                    viewModel = pluginsVm,
                )
            },
        ),
        ShellDestination(
            id = ShellDestinationId.SETTINGS,
            icon = AppIcons.Settings,
            label = s.settingsTitle,
            header = settingsHeader,
            content = {
                SettingsScreen(
                    query = if (onSettingsTab) query else submitted,
                    initialSection = requestedSettingsSection,
                )
            },
            // Dirty dot on the settings tab as soon as an app update is ready.
            badge = updateState.hasUpdate,
        ),
    )

    // Tab-Wechsel: Index setzen + Such-/Filter-/Dialog-Zustand zurücksetzen (unverändert übernommen).
    fun selectTab(idx: Int) {
        selected = idx
        query = ""
        submitted = ""
        typeFilter.value = emptySet()
        downloadedOnly = false
        filterMenuOpen = false
        pluginFilterMenuOpen = false
        showCreateGroup = false
        showCreateCollection = false
        openCollectionId = null
        showRepoMgmt = false
        pluginsVm.setQuery("")
        requestedSettingsSection = null
    }

    // Form-Faktor wählt das Skelett (compact → Drawer-Deskriptor, sonst Bottom-Bar-Deskriptor);
    // orthogonal zur Geräteklasse (die das Theme wählt). Die DeclarativeShell ordnet DIESELBE AppShellState an.
    // Der User-Override (ShellLayoutMode) schlägt die breitenbasierte Ableitung (AUTO).
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val shellLayoutModeStr by settingsVm.shellLayoutMode.collectAsState()
    // `layoutMode` statt `shellLayoutMode`, um Shadowing des Enum-Typs [ShellLayoutMode] zu vermeiden.
    val layoutMode = runCatching { ShellLayoutMode.valueOf(shellLayoutModeStr) }.getOrDefault(ShellLayoutMode.AUTO)
    // Externer UI-Pack (L2): ein gesetzter navStyle-Override SCHLÄGT den Form-Faktor-Default
    // (z. B. Drawer auch auf Boox-Breite). Fehlt der Pack/navStyle → Form-Faktor-Default.
    val activeUiPackId by settingsVm.activeUiPack.collectAsState()
    val uiPacks by settingsVm.availableUiPacks.collectAsState()
    val shellOverride = remember(activeUiPackId, uiPacks) {
        uiPacks.firstOrNull { it.packageName == activeUiPackId }?.shellOverride()
    }
    val displayModeStr by settingsVm.displayMode.collectAsState()
    val displayMode = runCatching { DisplayMode.valueOf(displayModeStr) }.getOrDefault(DisplayMode.EINK)
    // L2-UI-Pack-Override schlägt den Aurora-Default; ohne beides → Form-Faktor-Default.
    val effectiveOverride = shellOverride ?: auroraShellOverride(displayMode)
    val pack = ShellPackRegistry.forFormFactor(
        resolveFormFactor(layoutMode, configuration.screenWidthDp),
        effectiveOverride,
    )
    EinkContextEffect(EinkContext.HOME)
    // Banner content only when visible AND an update exists (stable local val for the smart-cast).
    val available = updateState as? AppUpdateState.Available
    pack.Render(
        AppShellState(
            destinations = destinations,
            selectedId = ShellDestinationId.entries[selected],
            onSelect = { id -> selectTab(ShellDestinationId.entries.indexOf(id)) },
            banner = if (bannerVisible && available != null) {
                {
                    // Tapping the banner jumps to Settings → "About" (straight to the update).
                    UpdateBanner(available.release.tag) {
                        selectTab(TAB_SETTINGS)
                        requestedSettingsSection = SettingsSectionId.ABOUT
                        bannerVisible = false
                    }
                }
            } else {
                null
            },
        ),
    )
    // Read-only "what's new" modal, shown once after an update was installed (based on the release body).
    releaseNotes?.let { notes ->
        ReleaseNotesDialog(notes) { updateVm.dismissReleaseNotes() }
    }
}

/**
 * Start banner "Update available: vX" — flat E-Ink look (1.5px border instead of shadow), centered
 * under the toolbar. Tapping it ([onClick]) jumps to the update. Pure display piece; the host owns
 * visibility and the 4-second timing.
 */
@Composable
private fun UpdateBanner(version: String, onClick: () -> Unit) {
    val s = LocalStrings.current
    Row(
        Modifier
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surface)
            .border(EinkTokens.strongBorder, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.Download, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(s.aboutUpdateAvailable(version), style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Read-only "what's new" modal shown once after an update was installed. Renders the GitHub release
 * notes (tag description). [ReleaseNotesBody] is the single swap point for richer (markdown) rendering.
 */
@Composable
private fun ReleaseNotesDialog(release: ReleaseInfo, onDismiss: () -> Unit) {
    val s = LocalStrings.current
    EinkInfoDialog(
        title = s.aboutWhatsNew(release.tag),
        onDismiss = onDismiss,
        closeLabel = s.close,
    ) {
        ReleaseNotesBody(release.body)
    }
}

/**
 * Renders the GitHub release body as Markdown (headings, lists, links), matching the plugin info
 * modal. Text-size animation is E-Ink-gated (no motion on E-Ink, `animation-gating.md`).
 */
@Composable
private fun ReleaseNotesBody(body: String) {
    val eink = LocalEinkMode.current
    Markdown(
        content = body,
        imageTransformer = Coil2ImageTransformerImpl,
        animations = markdownAnimations(animateTextSize = { if (eink) this else animateContentSize() }),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Kompakter Filter-Chip im Suchfeld: aktiv gesetzter Filter (Label + ✕ zum Entfernen). Akzentfarbe = aktiv. */
@Composable
private fun TypeFilterChip(label: String, onRemove: () -> Unit) {
    val tokens = LocalDesignTokens.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(tokens.accent)
            .clickable(onClick = onRemove)
            .padding(start = 8.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = tokens.onAccent,
        )
        Icon(
            AppIcons.Close,
            contentDescription = null,
            tint = tokens.onAccent,
            modifier = Modifier.size(14.dp),
        )
    }
}
