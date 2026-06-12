package com.komgareader.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.komgareader.app.ui.components.TileViewMode
import com.komgareader.app.ui.components.PluginFilterMenu
import com.komgareader.app.ui.components.TypeFilterMenu
import com.komgareader.app.ui.components.tileViewModeOf
import com.komgareader.app.ui.groups.GroupsScreen
import com.komgareader.app.ui.icons.AppIcons
import com.komgareader.app.ui.library.LibraryScreen
import com.komgareader.app.ui.library.LibraryViewModel
import com.komgareader.app.ui.plugins.PluginsScreen
import com.komgareader.app.ui.settings.SettingsScreen
import com.komgareader.app.ui.shell.AppShellState
import com.komgareader.app.ui.shell.DefaultShell
import com.komgareader.app.ui.shell.ShellDestination
import com.komgareader.app.ui.shell.ShellDestinationId
import com.komgareader.app.ui.theme.LocalDesignTokens
import com.komgareader.data.plugin.repo.PluginTypeFilter
import com.komgareader.domain.model.ContentType

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
    val pluginTypeFilter by pluginsVm.typeFilter.collectAsState()
    var showRepoMgmt by remember { mutableStateOf(false) }
    val groupsViewMode = tileViewModeOf(groupsVm.viewMode.collectAsState().value, TileViewMode.LIST)
    val collectionsViewMode = tileViewModeOf(collectionsVm.viewMode.collectAsState().value, TileViewMode.LARGE_TILE)

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
        filter: HomeHeaderFilter? = null,
        menu: @Composable () -> Unit = {},
        actions: @Composable RowScope.() -> Unit = {},
    ) = HomeHeaderState(
        status = { StatusCluster() },
        search = sharedSearch,
        filter = filter,
        menu = menu,
        actions = actions,
    )

    val libraryHeader = headerOf(
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
            IconButton(onClick = { libraryVm.refresh() }) {
                Icon(AppIcons.Refresh, contentDescription = null)
            }
        },
    )

    // Sammlungen-Liste: „+" (neue Sammlung) + rotierender Ansichts-Button + manueller Voll-Sync.
    val collectionsHeader = headerOf(
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
            IconButton(onClick = { collectionsVm.syncNow() }) {
                Icon(AppIcons.Refresh, contentDescription = s.collectionSyncNow)
            }
        },
    )

    // Bibliotheken: „+" (neue Bibliothek) + rotierender Ansichts-Button.
    val groupsHeader = headerOf(
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

    val pluginsHeader = headerOf(
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
    )

    val settingsHeader = headerOf()

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
            content = { SettingsScreen(query = if (onSettingsTab) query else submitted) },
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
    }

    // Phase A: verhaltens-erhaltend IMMER DefaultShell (auf jeder Breite). Die Form-Faktor-Auswahl
    // (ShellPackRegistry.forFormFactor(formFactorFor(screenWidthDp))) wird erst in C1 hier verdrahtet,
    // wenn PhoneShell einen echten Body hat — sonst rendert compact (<600dp) die leere PhoneShell.
    // StubSource-Prinzip: kein leeres Pack greift, bevor es real ist.
    val pack = DefaultShell
    pack.Render(
        AppShellState(
            destinations = destinations,
            selectedId = ShellDestinationId.entries[selected],
            onSelect = { id -> selectTab(ShellDestinationId.entries.indexOf(id)) },
        ),
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
