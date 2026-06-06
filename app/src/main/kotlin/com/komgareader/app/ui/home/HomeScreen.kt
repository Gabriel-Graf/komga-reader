package com.komgareader.app.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.ui.components.BottomNavItem
import com.komgareader.app.ui.components.EinkBottomBar
import com.komgareader.app.ui.components.EinkSearchBar
import com.komgareader.app.ui.components.StatusCluster
import com.komgareader.app.ui.groups.GroupsScreen
import com.komgareader.app.ui.library.LibraryScreen
import com.komgareader.app.ui.library.LibraryViewModel
import com.komgareader.app.ui.plugins.PluginsScreen
import com.komgareader.app.ui.settings.SettingsLandingScreen
import com.komgareader.app.ui.settings.SettingsPage

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
    onOpenSettingsPage: (SettingsPage) -> Unit,
) {
    val s = LocalStrings.current
    var selected by rememberSaveable { mutableIntStateOf(TAB_LIBRARY) }
    var query by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf("") }
    var showCreateGroup by remember { mutableStateOf(false) }
    val libraryVm: LibraryViewModel = hiltViewModel()

    val items = remember(s) {
        listOf(
            BottomNavItem(Icons.Outlined.LibraryBooks, s.libraryTitle),
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
                title = {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        StatusCluster()
                        Spacer(Modifier.width(12.dp))
                        EinkSearchBar(
                            query = query,
                            onQueryChange = { query = it },
                            onSubmit = { submitSearch() },
                            placeholder = if (onSettingsTab) s.searchSettingsHint else s.searchMediaHint,
                            actionLabel = s.searchAction,
                            modifier = Modifier.weight(1f),
                        )
                    }
                },
                actions = {
                    when (selected) {
                        TAB_LIBRARY -> IconButton(onClick = { libraryVm.refresh() }) {
                            Icon(Icons.Outlined.Sync, contentDescription = null)
                        }
                        TAB_GROUPS -> IconButton(onClick = { showCreateGroup = true }) {
                            Icon(Icons.Outlined.Add, contentDescription = s.newGroup)
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
                    showCreateGroup = false
                },
            )
        },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            when (selected) {
                TAB_LIBRARY -> LibraryScreen(query = submitted, onOpenSeries = onOpenSeries, viewModel = libraryVm)
                TAB_GROUPS -> GroupsScreen(
                    onOpenGroup = onOpenGroup,
                    showCreateDialog = showCreateGroup,
                    onDismissCreate = { showCreateGroup = false },
                )
                TAB_PLUGINS -> PluginsScreen()
                else -> SettingsLandingScreen(query = submitted, onOpenPage = onOpenSettingsPage)
            }
        }
    }
}
