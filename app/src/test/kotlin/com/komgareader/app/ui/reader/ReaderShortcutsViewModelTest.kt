package com.komgareader.app.ui.reader

import com.komgareader.app.data.EinkContextController
import com.komgareader.app.eink.HardwareButtonBus
import com.komgareader.app.eink.RecordingEinkController
import com.komgareader.domain.eink.ButtonEvent
import com.komgareader.domain.eink.EinkContext
import com.komgareader.domain.eink.EinkContextProfile
import com.komgareader.domain.eink.HardwareButton
import com.komgareader.domain.eink.PressKind
import com.komgareader.domain.eink.RefreshMode
import com.komgareader.domain.render.NovelFonts
import com.komgareader.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderShortcutsViewModelTest {

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private class StubSettings : SettingsRepository {
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

    private fun makeVm(bus: HardwareButtonBus, rec: RecordingEinkController): ReaderShortcutsViewModel =
        ReaderShortcutsViewModel(bus, EinkContextController(rec, StubSettings()))

    // ---------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------

    @Test
    fun `long volume up emits a home request`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val bus = HardwareButtonBus()
            val vm = makeVm(bus, RecordingEinkController())

            val received = mutableListOf<Unit>()
            val collector = launch { vm.homeRequests.collect { received.add(it) } }
            // yield so collector coroutine subscribes before we emit
            yield()

            bus.emit(ButtonEvent(HardwareButton.VOLUME_UP, PressKind.LONG))
            advanceUntilIdle()

            assertTrue(received.isNotEmpty(), "Expected a home request to be emitted")
            collector.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `long volume down triggers a full refresh`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val bus = HardwareButtonBus()
            val rec = RecordingEinkController()
            val vm = makeVm(bus, rec)
            // Collect homeRequests to keep the vm coroutine active
            val collector = launch { vm.homeRequests.collect {} }

            bus.emit(ButtonEvent(HardwareButton.VOLUME_DOWN, PressKind.LONG))
            advanceUntilIdle()

            assertEquals(RefreshMode.FULL, rec.lastRefreshMode)
            collector.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `short press is ignored by the shortcut layer`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val bus = HardwareButtonBus()
            val rec = RecordingEinkController()
            val vm = makeVm(bus, rec)

            val received = mutableListOf<Unit>()
            val collector = launch { vm.homeRequests.collect { received.add(it) } }

            bus.emit(ButtonEvent(HardwareButton.VOLUME_UP, PressKind.SHORT))
            bus.emit(ButtonEvent(HardwareButton.VOLUME_DOWN, PressKind.SHORT))
            advanceUntilIdle()

            assertTrue(received.isEmpty(), "Short presses must not emit home requests")
            assertNull(rec.lastRefreshMode, "Short presses must not trigger a refresh")
            collector.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }
}
