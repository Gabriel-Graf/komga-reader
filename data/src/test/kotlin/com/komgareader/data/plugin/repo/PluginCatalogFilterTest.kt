package com.komgareader.data.plugin.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginCatalogFilterTest {

    private fun installed(name: String, kind: PluginKind) =
        InstalledEntry(packageName = "pkg.$name", displayName = name, kind = kind)

    private fun discovered(
        name: String,
        kind: PluginKind,
        desc: String = "",
        state: InstallState = InstallState.NOT_INSTALLED,
    ) =
        BrowserRow(
            item = BrowsableEntry(
                entry = RepoPluginEntry(packageName = "pkg.$name", name = name, description = desc),
                repoName = "Repo",
                repoUrl = "https://x/repo.json",
                kind = kind,
            ),
            state = state,
            compatible = true,
        )

    @Test
    fun `empty filters keep everything, installed and discovered split`() {
        val out = visibleRows(
            installed = listOf(installed("Kavita", PluginKind.SOURCE)),
            discovered = listOf(discovered("Comick", PluginKind.SOURCE)),
            query = "",
            typeFilter = PluginTypeFilter.ALL,
        )
        assertEquals(listOf("Kavita"), out.installed.map { it.displayName })
        assertEquals(listOf("Comick"), out.discovered.map { it.item.entry.name })
    }

    @Test
    fun `type filter SOURCES drops presets from both sections`() {
        val out = visibleRows(
            installed = listOf(installed("Kavita", PluginKind.SOURCE), installed("Sepia", PluginKind.PRESET)),
            discovered = listOf(discovered("Comick", PluginKind.SOURCE), discovered("Warm", PluginKind.PRESET)),
            query = "",
            typeFilter = PluginTypeFilter.SOURCES,
        )
        assertEquals(listOf("Kavita"), out.installed.map { it.displayName })
        assertEquals(listOf("Comick"), out.discovered.map { it.item.entry.name })
    }

    @Test
    fun `type filter UI_PACKS keeps only ui-packs in both sections`() {
        val out = visibleRows(
            installed = listOf(installed("MeinSkin", PluginKind.UI_PACK), installed("Kavita", PluginKind.SOURCE)),
            discovered = listOf(discovered("DarkPack", PluginKind.UI_PACK), discovered("Warm", PluginKind.PRESET)),
            query = "",
            typeFilter = PluginTypeFilter.UI_PACKS,
        )
        assertEquals(listOf("MeinSkin"), out.installed.map { it.displayName })
        assertEquals(listOf("DarkPack"), out.discovered.map { it.item.entry.name })
    }

    @Test
    fun `query matches name and description, case-insensitive, both sections`() {
        val out = visibleRows(
            installed = listOf(installed("Kavita", PluginKind.SOURCE), installed("Comick", PluginKind.SOURCE)),
            discovered = listOf(discovered("Warm", PluginKind.PRESET, desc = "kavita preset")),
            query = "kav",
            typeFilter = PluginTypeFilter.ALL,
        )
        assertEquals(listOf("Kavita"), out.installed.map { it.displayName })
        assertEquals(listOf("Warm"), out.discovered.map { it.item.entry.name })
    }

    @Test
    fun `showDivider is true when both installed and discovered are non-empty`() {
        val out = visibleRows(
            installed = listOf(installed("Kavita", PluginKind.SOURCE)),
            discovered = listOf(discovered("Comick", PluginKind.SOURCE)),
            query = "", typeFilter = PluginTypeFilter.ALL,
        )
        assertTrue(out.showDivider)
    }

    @Test
    fun `showDivider is false when installed section is empty`() {
        val out = visibleRows(
            installed = emptyList(),
            discovered = listOf(discovered("Comick", PluginKind.SOURCE)),
            query = "", typeFilter = PluginTypeFilter.ALL,
        )
        assertEquals(false, out.showDivider)
    }

    @Test
    fun `already-installed discovered rows are dropped from the discovered section`() {
        val out = visibleRows(
            installed = listOf(installed("Kavita", PluginKind.SOURCE)),
            discovered = listOf(
                discovered("Kavita", PluginKind.SOURCE, state = InstallState.INSTALLED),
                discovered("Comick", PluginKind.SOURCE, state = InstallState.NOT_INSTALLED),
            ),
            query = "", typeFilter = PluginTypeFilter.ALL,
        )
        // Installiertes erscheint nur oben, nicht doppelt unten.
        assertEquals(listOf("Comick"), out.discovered.map { it.item.entry.name })
    }

    @Test
    fun `update-available discovered rows stay in the discovered section`() {
        val out = visibleRows(
            installed = listOf(installed("Kavita", PluginKind.SOURCE)),
            discovered = listOf(discovered("Kavita", PluginKind.SOURCE, state = InstallState.UPDATE_AVAILABLE)),
            query = "", typeFilter = PluginTypeFilter.ALL,
        )
        // Ein verfügbares Update bleibt unten sichtbar (handlungsrelevant).
        assertEquals(listOf("Kavita"), out.discovered.map { it.item.entry.name })
    }

    @Test
    fun `showDivider is false when only installed rows remain after dropping installed-discovered`() {
        val out = visibleRows(
            installed = listOf(installed("Kavita", PluginKind.SOURCE)),
            discovered = listOf(discovered("Kavita", PluginKind.SOURCE, state = InstallState.INSTALLED)),
            query = "", typeFilter = PluginTypeFilter.ALL,
        )
        assertEquals(false, out.showDivider)
    }

    @Test
    fun `showDivider is false when discovered section is empty`() {
        val out = visibleRows(
            installed = listOf(installed("Kavita", PluginKind.SOURCE)),
            discovered = emptyList(),
            query = "", typeFilter = PluginTypeFilter.ALL,
        )
        assertEquals(false, out.showDivider)
    }
}
