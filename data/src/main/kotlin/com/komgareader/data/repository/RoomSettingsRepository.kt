package com.komgareader.data.repository

import com.komgareader.data.db.SettingEntity
import com.komgareader.data.db.SettingsDao
import com.komgareader.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomSettingsRepository(private val dao: SettingsDao) : SettingsRepository {
    override val themeMode: Flow<String> = dao.observe(KEY_THEME).map { it ?: "SYSTEM" }
    override val language: Flow<String> = dao.observe(KEY_LANG).map { it ?: "de" }
    // Default EINK: Ziel-Gerät ist ein Onyx-Boox.
    override val displayMode: Flow<String> = dao.observe(KEY_DISPLAY).map { it ?: "EINK" }
    override val downloadDir: Flow<String?> = dao.observe(KEY_DOWNLOAD_DIR)
    override val activeColorProfileId: Flow<Long?> = dao.observe(KEY_ACTIVE_COLOR_PROFILE)
        .map { it?.toLongOrNull() }

    override suspend fun setThemeMode(value: String) = dao.put(SettingEntity(KEY_THEME, value))
    override suspend fun setLanguage(value: String) = dao.put(SettingEntity(KEY_LANG, value))
    override suspend fun setDisplayMode(value: String) = dao.put(SettingEntity(KEY_DISPLAY, value))
    override suspend fun setDownloadDir(uri: String?) {
        if (uri == null) dao.delete(KEY_DOWNLOAD_DIR)
        else dao.put(SettingEntity(KEY_DOWNLOAD_DIR, uri))
    }

    override suspend fun setActiveColorProfileId(id: Long) =
        dao.put(SettingEntity(KEY_ACTIVE_COLOR_PROFILE, id.toString()))

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_LANG = "language"
        const val KEY_DISPLAY = "display_mode"
        const val KEY_DOWNLOAD_DIR = "download_dir"
        const val KEY_ACTIVE_COLOR_PROFILE = "active_color_profile_id"
    }
}
