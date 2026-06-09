package com.komgareader.data.repository

import com.komgareader.data.db.ColorProfileDao
import com.komgareader.data.db.ColorProfileEntity
import com.komgareader.data.plugin.ColorPresetImporter
import com.komgareader.domain.model.ColorProfile
import com.komgareader.domain.model.DitherMode
import com.komgareader.plugin.ColorPresetSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
        dao.rows.value = listOf(ColorProfileEntity(id = 1, name = "Aus", saturation = 1f, contrast = 1f, brightness = 0f, builtIn = true))
        val active = repo(dao, FakeActivePointer()).observeActive().first()
        assertEquals(ColorProfile.OFF, active)
    }

    @Test
    fun `observeActive liefert das Profil zum Pointer`() = runTest {
        val dao = FakeColorProfileDao()
        dao.rows.value = listOf(
            ColorProfileEntity(id = 1, name = "Aus", saturation = 1f, contrast = 1f, brightness = 0f, builtIn = true),
            ColorProfileEntity(id = 2, name = "Boox Go Color 7 Gen2", saturation = 1.4f, contrast = 1.15f, brightness = 0.05f, builtIn = true),
        )
        val pointer = FakeActivePointer().also { it.flow.value = 2L }
        val active = repo(dao, pointer).observeActive().first()
        assertEquals(2L, active.id)
        assertEquals(1.4f, active.saturation)
    }

    @Test
    fun `observeAll mappt Entities zu Domain-Profilen`() = runTest {
        val dao = FakeColorProfileDao()
        dao.rows.value = listOf(ColorProfileEntity(id = 2, name = "Custom", saturation = 1.2f, contrast = 1.1f, brightness = 0f, builtIn = false))
        val all = repo(dao, FakeActivePointer()).observeAll().first()
        assertEquals(1, all.size)
        assertEquals("Custom", all[0].name)
        assertEquals(false, all[0].builtIn)
    }

    @Test
    fun `upsert und observeAll erhalten die Phase-2-Felder`() = runTest {
        val dao = FakeColorProfileDao()
        val repo = repo(dao, FakeActivePointer())
        val id = repo.upsert(
            ColorProfile(
                id = 0, name = "P2", saturation = 1f, contrast = 1f, brightness = 0f,
                blackPoint = 0.1f, whitePoint = 0.9f, gamma = 1.3f,
                sharpenAmount = 0.5f, sharpenRadius = 2,
                ditherMode = DitherMode.FLOYD_STEINBERG, ditherLevels = 8, builtIn = false,
            ),
        )
        val loaded = repo.observeAll().first().first { it.id == id }
        assertEquals(0.1f, loaded.blackPoint)
        assertEquals(0.9f, loaded.whitePoint)
        assertEquals(1.3f, loaded.gamma)
        assertEquals(0.5f, loaded.sharpenAmount)
        assertEquals(2, loaded.sharpenRadius)
        assertEquals(DitherMode.FLOYD_STEINBERG, loaded.ditherMode)
        assertEquals(8, loaded.ditherLevels)
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

    @Test
    fun `importiertes Preset landet in der DB und ist löschbar`() = runTest {
        val dao = FakeColorProfileDao()
        val r = repo(dao, FakeActivePointer())
        val spec = ColorPresetSpec(abiVersion = 1, name = "Kaleido Warm", saturation = 1.3f, contrast = 1.1f, brightness = 0.0f)
        val profile = ColorPresetImporter.toProfileOrNull(spec)!!
        val id = r.upsert(profile)
        assertTrue(id > 0, "Upsert muss eine gültige ID zurückgeben")
        val all = r.observeAll().first()
        assertTrue(all.any { it.id == id && it.name == "Kaleido Warm" && !it.builtIn })
        r.delete(id)
        val afterDelete = r.observeAll().first()
        assertTrue(afterDelete.none { it.id == id }, "Preset muss nach delete verschwunden sein")
    }
}
