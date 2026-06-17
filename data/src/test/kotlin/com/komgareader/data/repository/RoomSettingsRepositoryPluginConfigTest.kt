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
import kotlin.test.assertNull

private class FakeSettingsDaoForPluginConfig : SettingsDao {
    val store = MutableStateFlow<Map<String, String>>(emptyMap())
    override fun observe(key: String): Flow<String?> = store.map { it[key] }
    override suspend fun put(entity: SettingEntity) { store.value = store.value + (entity.key to entity.value) }
    override suspend fun delete(key: String) { store.value = store.value - key }
}

class RoomSettingsRepositoryPluginConfigTest {
    @Test
    fun plugin_config_round_trips_under_namespaced_key() = runTest {
        val dao = FakeSettingsDaoForPluginConfig()
        val repo = RoomSettingsRepository(dao)
        val pkg = "com.komgareader.model.panel.yolo"
        assertNull(repo.pluginConfig(pkg, "min_confidence").let { flow -> flow.first() })
        repo.setPluginConfig(pkg, "min_confidence", "0.4")
        assertEquals("0.4", repo.pluginConfig(pkg, "min_confidence").first())
        assertEquals("0.4", dao.store.value["plugincfg:$pkg:min_confidence"])
    }
}
