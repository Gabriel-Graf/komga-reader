package com.komgareader.data.repository

import com.komgareader.data.db.SettingEntity
import com.komgareader.data.db.SettingsDao
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
}
