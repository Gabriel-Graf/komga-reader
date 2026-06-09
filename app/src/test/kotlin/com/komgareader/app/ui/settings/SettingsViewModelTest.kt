package com.komgareader.app.ui.settings

import com.komgareader.domain.model.ColorProfile
import com.komgareader.domain.model.SourceKind
import com.komgareader.domain.repository.ColorProfileRepository
import com.komgareader.domain.repository.ServerConfig
import com.komgareader.domain.repository.ServerRepository
import com.komgareader.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    /** Fängt die zuletzt gespeicherte Verbindung ein — nur [save] ist hier relevant. */
    private class CapturingServerRepository : ServerRepository {
        var saved: ServerConfig? = null
        override val configs: Flow<List<ServerConfig>> = flowOf(emptyList())
        override val config: Flow<ServerConfig?> = flowOf(null)
        override suspend fun save(config: ServerConfig) { saved = config }
        override suspend fun remove(id: Long) = error("not used")
        override suspend fun clear() = error("not used")
    }

    private fun viewModel(servers: ServerRepository): SettingsViewModel =
        SettingsViewModel(StubSettingsRepository(), servers, StubColorProfileRepository())

    @Test
    fun `saveServer mit id 0 speichert eine neue Verbindung mit id 0`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val repo = CapturingServerRepository()
            val vm = viewModel(repo)

            vm.saveServer("Heim", "http://h", "", "", "", SourceKind.KOMGA, id = 0)

            assertEquals(0L, repo.saved?.id)
            assertEquals("Heim", repo.saved?.name)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `saveServer mit id 7 speichert die bearbeitete Verbindung mit id 7`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val repo = CapturingServerRepository()
            val vm = viewModel(repo)

            vm.saveServer("Heim", "http://h", "", "", "", SourceKind.KOMGA, id = 7L)

            assertEquals(7L, repo.saved?.id)
        } finally {
            Dispatchers.resetMain()
        }
    }
}

/** Minimal-Stub: alle Flows mit Default-Werten, Setter sind No-Ops — das VM liest nur beim Konstruieren. */
private class StubSettingsRepository : SettingsRepository {
    override val themeMode: Flow<String> = flowOf("SYSTEM")
    override val language: Flow<String> = flowOf("de")
    override val displayMode: Flow<String> = flowOf("EINK")
    override val downloadDir: Flow<String?> = flowOf(null)
    override val guidedPanelOverlay: Flow<Boolean> = flowOf(false)
    override val activeColorProfileId: Flow<Long?> = flowOf(null)
    override val webtoonOverlapPercent: Flow<Int> = flowOf(25)
    override val chapterViewMode: Flow<String> = flowOf("LIST")
    override val novelFontSizeEm: Flow<Float> = flowOf(1.0f)
    override val novelLineHeight: Flow<Float> = flowOf(1.0f)
    override val novelMarginPreset: Flow<String> = flowOf("NORMAL")
    override val novelFontFamily: Flow<String> = flowOf("")
    override val novelTextAlign: Flow<String> = flowOf("LEFT")
    override val novelHyphenationLang: Flow<String> = flowOf("")
    override val novelFontWeight: Flow<Int> = flowOf(400)
    override val deviceManagedRefresh: Flow<Boolean> = flowOf(true)
    override suspend fun setThemeMode(value: String) {}
    override suspend fun setLanguage(value: String) {}
    override suspend fun setDisplayMode(value: String) {}
    override suspend fun setDownloadDir(uri: String?) {}
    override suspend fun setGuidedPanelOverlay(value: Boolean) {}
    override suspend fun setActiveColorProfileId(id: Long) {}
    override suspend fun setWebtoonOverlapPercent(percent: Int) {}
    override suspend fun setChapterViewMode(mode: String) {}
    override suspend fun setNovelFontSizeEm(value: Float) {}
    override suspend fun setNovelLineHeight(value: Float) {}
    override suspend fun setNovelMarginPreset(preset: String) {}
    override suspend fun setNovelFontFamily(family: String) {}
    override suspend fun setNovelTextAlign(align: String) {}
    override suspend fun setNovelHyphenationLang(lang: String) {}
    override suspend fun setNovelFontWeight(value: Int) {}
    override suspend fun setDeviceManagedRefresh(value: Boolean) {}
}

/** Minimal-Stub: aktives Profil ist OFF; Schreib-Operationen werden im Test nicht ausgeübt. */
private class StubColorProfileRepository : ColorProfileRepository {
    override fun observeAll(): Flow<List<ColorProfile>> = flowOf(emptyList())
    override fun observeActive(): Flow<ColorProfile> = flowOf(ColorProfile.OFF)
    override suspend fun upsert(profile: ColorProfile): Long = 0
    override suspend fun delete(id: Long) {}
    override suspend fun setActive(id: Long) {}
}
