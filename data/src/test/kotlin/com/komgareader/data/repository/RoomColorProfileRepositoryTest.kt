package com.komgareader.data.repository

import com.komgareader.data.db.ColorProfileDao
import com.komgareader.data.db.ColorProfileEntity
import com.komgareader.domain.model.ColorProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** In-Memory-Fake des DAO — testet die Mapping-/Fallback-Logik ohne echtes Room. */
private class FakeColorProfileDao : ColorProfileDao {
    val rows = MutableStateFlow<List<ColorProfileEntity>>(emptyList())
    override fun observeAll(): Flow<List<ColorProfileEntity>> = rows
    override fun observeById(id: Long): Flow<ColorProfileEntity?> =
        rows.map { list -> list.firstOrNull { it.id == id } }
    override suspend fun upsert(entity: ColorProfileEntity): Long {
        val id = if (entity.id == 0L) (rows.value.maxOfOrNull { it.id } ?: 0L) + 1L else entity.id
        rows.value = rows.value.filterNot { it.id == id } + entity.copy(id = id)
        return id
    }
    override suspend fun deleteCustom(id: Long) {
        rows.value = rows.value.filterNot { it.id == id && !it.builtIn }
    }
}

/** Fake der Aktiv-Pointer-Quelle. */
private class FakeActivePointer {
    val flow = MutableStateFlow<Long?>(null)
}

class RoomColorProfileRepositoryTest {

    private fun repo(dao: ColorProfileDao, pointer: FakeActivePointer) =
        RoomColorProfileRepository(dao, pointer.flow) { pointer.flow.value = it }

    @Test
    fun `observeActive fällt auf OFF zurück wenn kein Pointer gesetzt`() = runTest {
        val dao = FakeColorProfileDao()
        dao.rows.value = listOf(ColorProfileEntity(1, "Aus", 1f, 1f, 0f, true))
        val active = repo(dao, FakeActivePointer()).observeActive().first()
        assertEquals(ColorProfile.OFF, active)
    }

    @Test
    fun `observeActive liefert das Profil zum Pointer`() = runTest {
        val dao = FakeColorProfileDao()
        dao.rows.value = listOf(
            ColorProfileEntity(1, "Aus", 1f, 1f, 0f, true),
            ColorProfileEntity(2, "Boox Go Color 7 Gen2", 1.4f, 1.15f, 0.05f, true),
        )
        val pointer = FakeActivePointer().also { it.flow.value = 2L }
        val active = repo(dao, pointer).observeActive().first()
        assertEquals(2L, active.id)
        assertEquals(1.4f, active.saturation)
    }

    @Test
    fun `observeAll mappt Entities zu Domain-Profilen`() = runTest {
        val dao = FakeColorProfileDao()
        dao.rows.value = listOf(ColorProfileEntity(2, "Custom", 1.2f, 1.1f, 0f, false))
        val all = repo(dao, FakeActivePointer()).observeAll().first()
        assertEquals(1, all.size)
        assertEquals("Custom", all[0].name)
        assertEquals(false, all[0].builtIn)
    }

    @Test
    fun `upsert eines Built-ins wird abgelehnt`() = runTest {
        val dao = FakeColorProfileDao()
        val r = repo(dao, FakeActivePointer())
        try {
            r.upsert(ColorProfile(id = 1, name = "x", saturation = 1f, contrast = 1f, brightness = 0f, builtIn = true))
            kotlin.test.fail("erwartete IllegalArgumentException")
        } catch (_: IllegalArgumentException) { /* erwartet */ }
    }
}
