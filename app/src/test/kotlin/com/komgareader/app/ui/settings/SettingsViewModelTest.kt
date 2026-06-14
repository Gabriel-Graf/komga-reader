package com.komgareader.app.ui.settings

import com.komgareader.app.data.KomgaSourceProvider
import com.komgareader.app.data.PluginCatalog
import com.komgareader.app.data.SourceRegistration
import com.komgareader.app.data.SyncCoordinator
import io.mockk.mockk
import com.komgareader.domain.eink.EinkCapabilities
import com.komgareader.domain.eink.EinkContext
import com.komgareader.domain.eink.EinkContextProfile
import com.komgareader.domain.eink.EinkController
import com.komgareader.domain.model.CollectionKind
import com.komgareader.domain.model.CollectionMember
import com.komgareader.domain.model.ColorProfile
import com.komgareader.domain.model.SourceKind
import com.komgareader.domain.model.UserCollection
import com.komgareader.domain.render.NovelFonts
import com.komgareader.domain.model.ReadingSession
import com.komgareader.domain.model.ReadingStats
import com.komgareader.domain.repository.CollectionRepository
import com.komgareader.domain.repository.CollectionSyncLink
import com.komgareader.domain.repository.ColorProfileRepository
import com.komgareader.domain.repository.ReadingStatsRepository
import com.komgareader.domain.repository.ServerConfig
import com.komgareader.domain.repository.ServerRepository
import com.komgareader.domain.repository.SettingsRepository
import com.komgareader.domain.source.SourceManager
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

    private fun registration(): SourceRegistration =
        SourceRegistration(SourceManager(), KomgaSourceProvider(), mockk(relaxed = true))

    /** No-Op-Koordinator — Tests prüfen nicht den Sync-Trigger, nur die Server-Persistenz. */
    private fun noOpCoordinator(): SyncCoordinator =
        SyncCoordinator(
            fullSync = { emptyList() },
            pullOnlySync = {},
            scanLocal = {},
            fetchRepos = {},
            displayMode = { "EINK" },
        )

    private fun stubEinkController(): EinkController = object : EinkController {
        override val capabilities = EinkCapabilities(hasEink = false, canColor = false, canInvert = false)
        override val buttonEvents = kotlinx.coroutines.flow.flowOf<com.komgareader.domain.eink.ButtonEvent>()
        override fun refresh(region: com.komgareader.domain.eink.Region, mode: com.komgareader.domain.eink.RefreshMode) {}
        override fun setContrast(level: Int) {}
        override fun setInverted(inverted: Boolean) {}
        override fun applyRefreshMode(id: String?) {}
        override fun applyColorMode(id: String?) {}
        override fun defaultProfile(context: EinkContext) = EinkContextProfile()
    }

    private fun viewModel(servers: ServerRepository): SettingsViewModel {
        val collections = StubCollectionRepository()
        return SettingsViewModel(
            StubSettingsRepository(),
            servers,
            StubColorProfileRepository(),
            registration(),
            collections,
            noOpCoordinator(),
            mockk(relaxed = true),
            stubEinkController(),
            StubReadingStatsRepository(),
        )
    }

    private fun viewModel(settings: SettingsRepository): SettingsViewModel {
        val collections = StubCollectionRepository()
        return SettingsViewModel(
            settings,
            CapturingServerRepository(),
            StubColorProfileRepository(),
            registration(),
            collections,
            noOpCoordinator(),
            mockk(relaxed = true),
            stubEinkController(),
            StubReadingStatsRepository(),
        )
    }

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

    @Test
    fun `Novel-Setter delegieren an das SettingsRepository`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val repo = CapturingSettingsRepository()
            val vm = viewModel(repo)

            vm.setNovelFontSizeEm(1.4f)
            vm.setNovelLineHeight(1.3f)
            vm.setNovelFontWeight(600)
            vm.setNovelMarginPreset("WIDE")
            vm.setNovelTextAlign("LEFT")
            vm.setNovelHyphenationLang("de")
            vm.setNovelFontFamily("Literata")

            assertEquals(1.4f, repo.novelFontSizeEmValue)
            assertEquals(1.3f, repo.novelLineHeightValue)
            assertEquals(600, repo.novelFontWeightValue)
            assertEquals("WIDE", repo.novelMarginPresetValue)
            assertEquals("LEFT", repo.novelTextAlignValue)
            assertEquals("de", repo.novelHyphenationLangValue)
            assertEquals("Literata", repo.novelFontFamilyValue)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `Novel-Werte spiegeln die Flows des SettingsRepository`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val repo = CapturingSettingsRepository(
                fontSizeEm = 1.6f,
                lineHeight = 1.2f,
                marginPreset = "NARROW",
                fontFamily = "Bitter",
                textAlign = "LEFT",
                hyphenationLang = "en",
                fontWeight = 700,
            )
            val vm = viewModel(repo)

            assertEquals(1.6f, vm.novelFontSizeEm.value)
            assertEquals(1.2f, vm.novelLineHeight.value)
            assertEquals("NARROW", vm.novelMarginPreset.value)
            assertEquals("Bitter", vm.novelFontFamily.value)
            assertEquals("LEFT", vm.novelTextAlign.value)
            assertEquals("en", vm.novelHyphenationLang.value)
            assertEquals(700, vm.novelFontWeight.value)
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
    override val shellLayoutMode: Flow<String> = flowOf("AUTO")
    override val downloadDir: Flow<String?> = flowOf(null)
    override val guidedPanelOverlay: Flow<Boolean> = flowOf(false)
    override val useMlDetection: Flow<Boolean> = flowOf(true)
    override val activeColorProfileId: Flow<Long?> = flowOf(null)
    override val webtoonOverlapPercent: Flow<Int> = flowOf(25)
    override val chapterViewMode: Flow<String> = flowOf("LIST")
    override val librariesViewMode: Flow<String> = flowOf("LIST")
    override val collectionsViewMode: Flow<String> = flowOf("LARGE_TILE")
    override val novelFontSizeEm: Flow<Float> = flowOf(1.0f)
    override val novelLineHeight: Flow<Float> = flowOf(1.0f)
    override val novelMarginPreset: Flow<String> = flowOf("NORMAL")
    override val novelFontFamily: Flow<String> = flowOf(NovelFonts.DEFAULT)
    override val novelTextAlign: Flow<String> = flowOf("JUSTIFY")
    override val novelHyphenationLang: Flow<String> = flowOf("")
    override val novelFontWeight: Flow<Int> = flowOf(400)
    override val officialRepoEnabled: Flow<Boolean> = flowOf(true)
    override val activeUiPack: Flow<String> = flowOf("")
    override val lastSeenVersion: Flow<String> = flowOf("")
    override val einkContextProfiles: Flow<Map<EinkContext, EinkContextProfile>> = flowOf(emptyMap())
    override suspend fun setThemeMode(value: String) {}
    override suspend fun setLanguage(value: String) {}
    override suspend fun setDisplayMode(value: String) {}
    override suspend fun setShellLayoutMode(value: String) {}
    override suspend fun setDownloadDir(uri: String?) {}
    override suspend fun setGuidedPanelOverlay(value: Boolean) {}
    override suspend fun setUseMlDetection(value: Boolean) {}
    override suspend fun setActiveColorProfileId(id: Long) {}
    override suspend fun setWebtoonOverlapPercent(percent: Int) {}
    override suspend fun setChapterViewMode(mode: String) {}
    override suspend fun setLibrariesViewMode(mode: String) {}
    override suspend fun setCollectionsViewMode(mode: String) {}
    override suspend fun setNovelFontSizeEm(value: Float) {}
    override suspend fun setNovelLineHeight(value: Float) {}
    override suspend fun setNovelMarginPreset(preset: String) {}
    override suspend fun setNovelFontFamily(family: String) {}
    override suspend fun setNovelTextAlign(align: String) {}
    override suspend fun setNovelHyphenationLang(lang: String) {}
    override suspend fun setNovelFontWeight(value: Int) {}
    override suspend fun setOfficialRepoEnabled(enabled: Boolean) {}
    override suspend fun setActiveUiPack(packageName: String) {}
    override suspend fun setLastSeenVersion(version: String) {}
    override suspend fun setEinkContextProfile(context: EinkContext, profile: EinkContextProfile) {}
}

/**
 * Fängt die Novel-Setter ein und liefert konfigurierbare Novel-Flow-Werte — prüft, dass das VM
 * die 7 Settings spiegelt und seine Setter sauber durchreicht. Restliche Flows = Defaults.
 */
private class CapturingSettingsRepository(
    fontSizeEm: Float = 1.0f,
    lineHeight: Float = 1.0f,
    marginPreset: String = "NORMAL",
    fontFamily: String = "",
    textAlign: String = "LEFT",
    hyphenationLang: String = "",
    fontWeight: Int = 400,
) : SettingsRepository {
    var novelFontSizeEmValue: Float? = null
    var novelLineHeightValue: Float? = null
    var novelMarginPresetValue: String? = null
    var novelFontFamilyValue: String? = null
    var novelTextAlignValue: String? = null
    var novelHyphenationLangValue: String? = null
    var novelFontWeightValue: Int? = null

    override val themeMode: Flow<String> = flowOf("SYSTEM")
    override val language: Flow<String> = flowOf("de")
    override val displayMode: Flow<String> = flowOf("EINK")
    override val shellLayoutMode: Flow<String> = flowOf("AUTO")
    override val downloadDir: Flow<String?> = flowOf(null)
    override val guidedPanelOverlay: Flow<Boolean> = flowOf(false)
    override val useMlDetection: Flow<Boolean> = flowOf(true)
    override val activeColorProfileId: Flow<Long?> = flowOf(null)
    override val webtoonOverlapPercent: Flow<Int> = flowOf(25)
    override val chapterViewMode: Flow<String> = flowOf("LIST")
    override val librariesViewMode: Flow<String> = flowOf("LIST")
    override val collectionsViewMode: Flow<String> = flowOf("LARGE_TILE")
    override val novelFontSizeEm: Flow<Float> = flowOf(fontSizeEm)
    override val novelLineHeight: Flow<Float> = flowOf(lineHeight)
    override val novelMarginPreset: Flow<String> = flowOf(marginPreset)
    override val novelFontFamily: Flow<String> = flowOf(fontFamily)
    override val novelTextAlign: Flow<String> = flowOf(textAlign)
    override val novelHyphenationLang: Flow<String> = flowOf(hyphenationLang)
    override val novelFontWeight: Flow<Int> = flowOf(fontWeight)
    override val officialRepoEnabled: Flow<Boolean> = flowOf(true)
    override val activeUiPack: Flow<String> = flowOf("")
    override val lastSeenVersion: Flow<String> = flowOf("")
    override val einkContextProfiles: Flow<Map<EinkContext, EinkContextProfile>> = flowOf(emptyMap())
    override suspend fun setThemeMode(value: String) {}
    override suspend fun setLanguage(value: String) {}
    override suspend fun setDisplayMode(value: String) {}
    override suspend fun setShellLayoutMode(value: String) {}
    override suspend fun setDownloadDir(uri: String?) {}
    override suspend fun setGuidedPanelOverlay(value: Boolean) {}
    override suspend fun setUseMlDetection(value: Boolean) {}
    override suspend fun setActiveColorProfileId(id: Long) {}
    override suspend fun setWebtoonOverlapPercent(percent: Int) {}
    override suspend fun setChapterViewMode(mode: String) {}
    override suspend fun setLibrariesViewMode(mode: String) {}
    override suspend fun setCollectionsViewMode(mode: String) {}
    override suspend fun setNovelFontSizeEm(value: Float) { novelFontSizeEmValue = value }
    override suspend fun setNovelLineHeight(value: Float) { novelLineHeightValue = value }
    override suspend fun setNovelMarginPreset(preset: String) { novelMarginPresetValue = preset }
    override suspend fun setNovelFontFamily(family: String) { novelFontFamilyValue = family }
    override suspend fun setNovelTextAlign(align: String) { novelTextAlignValue = align }
    override suspend fun setNovelHyphenationLang(lang: String) { novelHyphenationLangValue = lang }
    override suspend fun setNovelFontWeight(value: Int) { novelFontWeightValue = value }
    override suspend fun setOfficialRepoEnabled(enabled: Boolean) {}
    override suspend fun setActiveUiPack(packageName: String) {}
    override suspend fun setLastSeenVersion(version: String) {}
    override suspend fun setEinkContextProfile(context: EinkContext, profile: EinkContextProfile) {}
}

/** Minimal-Stub: leere Sammlungen; Schreib-Operationen werden in diesen Tests nicht ausgeübt. */
private class StubCollectionRepository : CollectionRepository {
    override val collections: Flow<List<UserCollection>> = flowOf(emptyList())
    override fun syncLinks(collectionId: Long): Flow<List<CollectionSyncLink>> = flowOf(emptyList())
    override suspend fun create(name: String, kind: CollectionKind): Long = 0
    override suspend fun rename(collectionId: Long, name: String) {}
    override suspend fun delete(collectionId: Long) {}
    override suspend fun setMembers(collectionId: Long, members: List<CollectionMember>) {}
    override suspend fun updateMemberTitles(collectionId: Long, members: List<CollectionMember>) {}
    override suspend fun addMember(collectionId: Long, member: CollectionMember) {}
    override suspend fun removeMember(collectionId: Long, sourceId: Long, remoteId: String) {}
    override suspend fun updateSyncLink(link: CollectionSyncLink) {}
    override suspend fun get(collectionId: Long): UserCollection? = null
    override suspend fun removeSource(sourceId: Long) {}
}

/** Minimal-Stub: always returns empty stats; record is a no-op. */
private class StubReadingStatsRepository : ReadingStatsRepository {
    override suspend fun record(session: ReadingSession) {}
    override fun observeStats(): kotlinx.coroutines.flow.Flow<ReadingStats> =
        flowOf(ReadingStats())
}

/** Minimal-Stub: aktives Profil ist OFF; Schreib-Operationen werden im Test nicht ausgeübt. */
private class StubColorProfileRepository : ColorProfileRepository {
    override fun observeAll(): Flow<List<ColorProfile>> = flowOf(emptyList())
    override fun observeActive(): Flow<ColorProfile> = flowOf(ColorProfile.OFF)
    override suspend fun upsert(profile: ColorProfile): Long = 0
    override suspend fun delete(id: Long) {}
    override suspend fun deleteByPluginPackage(pkg: String) {}
    override suspend fun setActive(id: Long) {}
}
