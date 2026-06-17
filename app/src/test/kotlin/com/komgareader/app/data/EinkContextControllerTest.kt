package com.komgareader.app.data

import com.komgareader.app.eink.RecordingEinkController
import com.komgareader.domain.eink.EinkContext
import com.komgareader.domain.eink.EinkContextProfile
import com.komgareader.domain.eink.EinkController
import com.komgareader.domain.eink.RefreshMode
import com.komgareader.domain.eink.Region
import com.komgareader.domain.eink.EinkCapabilities
import com.komgareader.domain.eink.ButtonEvent
import com.komgareader.domain.render.NovelFonts
import com.komgareader.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EinkContextControllerTest {

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private class StubSettings : SettingsRepository {
        override val themeMode: Flow<String> = flowOf("SYSTEM")
        override val language: Flow<String> = flowOf("de")
        override val displayMode: Flow<String> = flowOf("EINK")
        override val shellLayoutMode: Flow<String> = flowOf("AUTO")
        override val bookmarkMarkerStyle: Flow<String> = flowOf("UNDERLINE")
        override val externalOpenBehavior: Flow<String> = flowOf("ASK")
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
        override suspend fun setBookmarkMarkerStyle(value: String) {}
        override suspend fun setExternalOpenBehavior(value: String) {}
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
        override val screenSaverMode: Flow<String> = flowOf("OFF")
        override val screenSaverCustomUri: Flow<String> = flowOf("")
        override val screenSaverFillCrop: Flow<Boolean> = flowOf(false)
        override suspend fun setScreenSaverMode(value: String) {}
        override suspend fun setScreenSaverCustomUri(uri: String) {}
        override suspend fun setScreenSaverFillCrop(value: Boolean) {}
    }

    private fun controllerWith(eink: EinkController) = EinkContextController(
        controller = eink,
        settings = StubSettings(),
    )

    // ---------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------

    @Test
    fun `manualFullRefresh issues a FULL refresh`() = runTest {
        val fake = RecordingEinkController()
        controllerWith(fake).manualFullRefresh()
        assertEquals(RefreshMode.FULL, fake.lastRefreshMode)
    }

    @Test
    fun `manualFullRefresh passes a zero region`() = runTest {
        val fake = RecordingEinkController()
        controllerWith(fake).manualFullRefresh()
        assertEquals(Region(0, 0, 0, 0), fake.lastRefreshRegion)
    }

    @Test
    fun `manualFullRefresh on no-eink device does not throw`() = runTest {
        val noOp = object : EinkController {
            override val capabilities = EinkCapabilities(hasEink = false, canColor = false, canInvert = false)
            override val buttonEvents: Flow<ButtonEvent> = emptyFlow()
            override fun refresh(region: Region, mode: RefreshMode) {}
            override fun setContrast(level: Int) {}
            override fun setInverted(inverted: Boolean) {}
            override fun applyRefreshMode(id: String?) {}
            override fun applyColorMode(id: String?) {}
            override fun defaultProfile(context: EinkContext) = EinkContextProfile()
        }
        // Must not throw — NoOp simply absorbs the call
        controllerWith(noOp).manualFullRefresh()
    }
}
