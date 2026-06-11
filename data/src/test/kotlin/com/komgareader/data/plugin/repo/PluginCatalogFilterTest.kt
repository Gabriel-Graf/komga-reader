package com.komgareader.data.plugin.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginCatalogFilterTest {

    private fun installed(name: String, kind: PluginKind) =
        InstalledEntry(packageName = "pkg.$name", displayName = name, kind = kind)

    private fun discovered(name: String, kind: PluginKind, desc: String = "") =
        BrowserRow(
            item = BrowsableEntry(
                entry = RepoPluginEntry(packageName = "pkg.$name", name = name, description = desc),
                repoName = "Repo",
                repoUrl = "https://x/repo.json",
                kind = kind,
            ),
            state = InstallState.NOT_INSTALLED,
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
    fun `showDivider is false when discovered section is empty`() {
        val out = visibleRows(
            installed = listOf(installed("Kavita", PluginKind.SOURCE)),
            discovered = emptyList(),
            query = "", typeFilter = PluginTypeFilter.ALL,
        )
        assertEquals(false, out.showDivider)
    }
}
