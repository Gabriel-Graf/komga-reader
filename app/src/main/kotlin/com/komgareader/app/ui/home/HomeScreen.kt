package com.komgareader.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.i18n.localizedContentType
import com.komgareader.app.ui.components.BottomNavItem
import com.komgareader.app.ui.components.EinkBottomBar
import com.komgareader.app.ui.components.EinkSearchBar
import com.komgareader.app.ui.components.StatusCluster
import com.komgareader.app.ui.components.TypeFilterMenu
import com.komgareader.app.ui.groups.GroupsScreen
import com.komgareader.app.ui.library.LibraryScreen
import com.komgareader.app.ui.library.LibraryViewModel
import com.komgareader.app.ui.plugins.PluginsScreen
import com.komgareader.app.ui.settings.SettingsScreen
import com.komgareader.domain.model.ContentType

private const val TAB_LIBRARY = 0
private const val TAB_GROUPS = 1
private const val TAB_PLUGINS = 2
private const val TAB_SETTINGS = 3

/**
 * App-Gerüst mit Onyx-Bottom-Menubar und persistenter Suchzeile in der TopBar.
 * Die Suche durchsucht standardmäßig die Bibliothek (Ergebnisse erscheinen dort);
 * auf dem Einstellungs-Tab durchsucht sie stattdessen die Einstellungen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSeries: (seriesId: String) -> Unit,
    onOpenGroup: (shelfId: Long, serverSourceId: Long) -> Unit,
) {
    val s = LocalStrings.current
    var selected by rememberSaveable { mutableIntStateOf(TAB_LIBRARY) }
    var query by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf("") }
    val typeFilter = rememberSaveable(
        saver = listSaver<MutableState<Set<ContentType>>, Int>(
            save = { it.value.map(ContentType::ordinal) },
            restore = { mutableStateOf(it.map { o -> ContentType.entries[o] }.toSet()) },
        ),
    ) { mutableStateOf(emptySet()) }
    var filterMenuOpen by remember { mutableStateOf(false) }
    var filterAnchor by remember { mutableStateOf(IntOffset.Zero) }
    var showCreateGroup by remember { mutableStateOf(false) }
    val libraryVm: LibraryViewModel = hiltViewModel()

    val items = remember(s) {
        listOf(
            BottomNavItem(Icons.Outlined.LibraryBooks, s.tabBrowse),
            BottomNavItem(Icons.Outlined.Dashboard, s.tabGroups),
            BottomNavItem(Icons.Outlined.Extension, s.navPlugins),
            BottomNavItem(Icons.Outlined.Settings, s.settingsTitle),
        )
    }

    val onSettingsTab = selected == TAB_SETTINGS

    fun submitSearch() {
        submitted = query
        // Medien-Suche zeigt ihre Treffer in der Bibliothek — dorthin wechseln.
        if (!onSettingsTab) selected = TAB_LIBRARY
    }

    Scaffold(
        topBar = {
            TopAppBar(
                // Volle Breite: Status links, Suche echt mittig (feste Breite), Aktion rechts.
                title = {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        StatusCluster(modifier = Modifier.align(Alignment.CenterStart))
                        EinkSearchBar(
                            query = query,
                            onQueryChange = { query = it },
                            onSubmit = { submitSearch() },
                            placeholder = if (onSettingsTab) s.searchSettingsHint else s.searchMediaHint,
                            actionLabel = s.searchAction,
                            clearLabel = s.clearSearch,
                            onClear = { query = ""; submitted = "" },
                            leading = if (selected == TAB_LIBRARY && typeFilter.value.isNotEmpty()) {
                                {
                                    typeFilter.value.forEach { type ->
                                        FilterChip(
                                            label = s.localizedContentType(type),
                                            onRemove = { typeFilter.value = typeFilter.value - type },
                                        )
                                    }
                                }
                            } else {
                                null
                            },
                            modifier = Modifier.fillMaxWidth(0.6f).widthIn(max = 360.dp),
                        )
                        Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
                            if (selected == TAB_LIBRARY) {
                                IconButton(
                                    onClick = { filterMenuOpen = true },
                                    modifier = Modifier.onGloballyPositioned { coords ->
                                        val pos = coords.positionInWindow()
                                        filterAnchor = IntOffset(
                                            (pos.x + coords.size.width).toInt(),
                                            (pos.y + coords.size.height).toInt(),
                                        )
                                    },
                                ) {
                                    Icon(Icons.Outlined.FilterList, contentDescription = s.filterByType)
                                }
                            }
                            when (selected) {
                                TAB_LIBRARY -> IconButton(onClick = { libraryVm.refresh() }) {
                                    Icon(Icons.Outlined.Sync, contentDescription = null)
                                }
                                TAB_GROUPS -> IconButton(onClick = { showCreateGroup = true }) {
                                    Icon(Icons.Outlined.Add, contentDescription = s.newGroup)
                                }
                            }
                        }
                        if (filterMenuOpen && selected == TAB_LIBRARY) {
                            TypeFilterMenu(
                                anchor = filterAnchor,
                                selected = typeFilter.value,
                                onToggle = { type ->
                                    typeFilter.value =
                                        if (type in typeFilter.value) typeFilter.value - type
                                        else typeFilter.value + type
                                },
                                onDismiss = { filterMenuOpen = false },
                            )
                        }
}
                },
            )
        },
        bottomBar = {
            EinkBottomBar(
                items = items,
                selectedIndex = selected,
                onSelect = { idx ->
                    selected = idx
                    query = ""
                    submitted = ""
                    typeFilter.value = emptySet()
                    filterMenuOpen = false
                    showCreateGroup = false
                },
            )
        },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            when (selected) {
                TAB_LIBRARY -> LibraryScreen(
                    query = submitted,
                    typeFilter = typeFilter.value,
                    onOpenSeries = onOpenSeries,
                    viewModel = libraryVm,
                )
                TAB_GROUPS -> GroupsScreen(
                    onOpenGroup = onOpenGroup,
                    showCreateDialog = showCreateGroup,
                    onDismissCreate = { showCreateGroup = false },
                )
                TAB_PLUGINS -> PluginsScreen()
                else -> SettingsScreen(query = if (onSettingsTab) query else submitted)
            }
        }
    }
}

/** Kompakter E-Ink-Filter-Chip im Suchfeld: Label + ✕ zum Entfernen des Typs. */
@Composable
private fun FilterChip(label: String, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.onSurface)
            .clickable(onClick = onRemove)
            .padding(start = 8.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.surface,
        )
        Icon(
            Icons.Outlined.Close,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.surface,
            modifier = Modifier.size(14.dp),
        )
    }
}
