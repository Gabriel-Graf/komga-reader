package com.komgareader.data.repository

import com.komgareader.data.db.SettingEntity
import com.komgareader.data.db.SettingsDao
import com.komgareader.domain.render.NovelFonts
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** In-Memory-Fake des SettingsDao — kein Room nötig (reiner Unit-Test). */
private class FakeSettingsDao : SettingsDao {
    private val store = MutableStateFlow<Map<String, String>>(emptyMap())
    override fun observe(key: String): Flow<String?> = store.map { it[key] }
    override suspend fun put(entity: SettingEntity) {
        store.value = store.value + (entity.key to entity.value)
    }
    override suspend fun delete(key: String) {
        store.value = store.value - key
    }
}

class RoomSettingsRepositoryTest {

    @Test
    fun `chapterViewMode default ist GRID ohne gesetzten Wert`() = runTest {
        val repo = RoomSettingsRepository(FakeSettingsDao())
        assertEquals("GRID", repo.chapterViewMode.first())
    }

    @Test
    fun `setChapterViewMode persistiert und Flow liefert neuen Wert`() = runTest {
        val repo = RoomSettingsRepository(FakeSettingsDao())
        repo.setChapterViewMode("LIST")
        assertEquals("LIST", repo.chapterViewMode.first())
    }

    @Test
    fun `novelFontSizeEm default ist 1_0 ohne gesetzten Wert`() = runTest {
        val repo = RoomSettingsRepository(FakeSettingsDao())
        assertEquals(1.0f, repo.novelFontSizeEm.first())
    }

    @Test
    fun `setNovelFontSizeEm persistiert und Flow liefert neuen Wert`() = runTest {
        val repo = RoomSettingsRepository(FakeSettingsDao())
        repo.setNovelFontSizeEm(1.4f)
        assertEquals(1.4f, repo.novelFontSizeEm.first())
    }

    @Test
    fun `novelLineHeight default ist 1_0 ohne gesetzten Wert`() = runTest {
        val repo = RoomSettingsRepository(FakeSettingsDao())
        assertEquals(1.0f, repo.novelLineHeight.first())
    }

    @Test
    fun `setNovelLineHeight persistiert und Flow liefert neuen Wert`() = runTest {
        val repo = RoomSettingsRepository(FakeSettingsDao())
        repo.setNovelLineHeight(1.5f)
        assertEquals(1.5f, repo.novelLineHeight.first())
    }

    @Test
    fun `novelMarginPreset default ist NORMAL ohne gesetzten Wert`() = runTest {
        val repo = RoomSettingsRepository(FakeSettingsDao())
        assertEquals("NORMAL", repo.novelMarginPreset.first())
    }

    @Test
    fun `setNovelMarginPreset persistiert und Flow liefert neuen Wert`() = runTest {
        val repo = RoomSettingsRepository(FakeSettingsDao())
        repo.setNovelMarginPreset("WIDE")
        assertEquals("WIDE", repo.novelMarginPreset.first())
    }

    @Test
    fun `novelFontFamily default ist der registrierte Familienname DejaVu Sans`() = runTest {
        val repo = RoomSettingsRepository(FakeSettingsDao())
        assertEquals(NovelFonts.DEFAULT, repo.novelFontFamily.first())
        assertEquals("DejaVu Sans", repo.novelFontFamily.first())
    }

    @Test
    fun `setNovelFontFamily persistiert und Flow liefert neuen Wert`() = runTest {
        val repo = RoomSettingsRepository(FakeSettingsDao())
        repo.setNovelFontFamily("Literata")
        assertEquals("Literata", repo.novelFontFamily.first())
    }

    @Test
    fun `novelTextAlign default ist JUSTIFY ohne gesetzten Wert`() = runTest {
        val repo = RoomSettingsRepository(FakeSettingsDao())
        assertEquals("JUSTIFY", repo.novelTextAlign.first())
    }

    @Test
    fun `setNovelTextAlign persistiert und Flow liefert neuen Wert`() = runTest {
        val repo = RoomSettingsRepository(FakeSettingsDao())
        repo.setNovelTextAlign("LEFT")
        assertEquals("LEFT", repo.novelTextAlign.first())
    }

    @Test
    fun `novelHyphenationLang default ist auto ohne gesetzten Wert`() = runTest {
        val repo = RoomSettingsRepository(FakeSettingsDao())
        assertEquals("auto", repo.novelHyphenationLang.first())
    }

    @Test
    fun `setNovelHyphenationLang persistiert und Flow liefert neuen Wert`() = runTest {
        val repo = RoomSettingsRepository(FakeSettingsDao())
        repo.setNovelHyphenationLang("de")
        assertEquals("de", repo.novelHyphenationLang.first())
    }

    @Test fun `novel hyphenation defaults to auto when unset`() = runTest {
        assertEquals("auto", RoomSettingsRepository(FakeSettingsDao()).novelHyphenationLang.first())
    }

    @Test
    fun `useMlDetection defaults true and round-trips`() = runTest {
        val repo = RoomSettingsRepository(FakeSettingsDao())
        assertEquals(true, repo.useMlDetection.first())   // Default true (Schlüssel fehlt)
        repo.setUseMlDetection(false)
        assertEquals(false, repo.useMlDetection.first())
        repo.setUseMlDetection(true)
        assertEquals(true, repo.useMlDetection.first())
    }

    @Test
    fun `frontlight level defaults to -1 when unset`() = runTest {
        assertEquals(-1, RoomSettingsRepository(FakeSettingsDao()).frontlightLevel.first())
    }

    @Test
    fun `frontlight level round-trips`() = runTest {
        val repo = RoomSettingsRepository(FakeSettingsDao())
        repo.setFrontlightLevel(42)
        assertEquals(42, repo.frontlightLevel.first())
    }
}
