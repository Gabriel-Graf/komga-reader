package com.komgareader.data.repository

import com.komgareader.data.db.ColorProfileDao
import com.komgareader.data.db.ColorProfileEntity
import com.komgareader.domain.model.ColorProfile
import com.komgareader.domain.model.DitherMode
import com.komgareader.domain.repository.ColorProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Room-gestützte Profilverwaltung. Der Aktiv-Pointer lebt in der Settings-KV-Tabelle und
 * wird hier als Flow + Setter hereingereicht (von der DI verdrahtet), damit dieses Repo
 * nur eine Verantwortung hat.
 */
class RoomColorProfileRepository(
    private val dao: ColorProfileDao,
    private val activePointer: Flow<Long?>,
    private val setActivePointer: suspend (Long) -> Unit,
) : ColorProfileRepository {

    override fun observeAll(): Flow<List<ColorProfile>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeActive(): Flow<ColorProfile> =
        combine(dao.observeAll(), activePointer) { list, activeId ->
            list.firstOrNull { it.id == activeId }?.toDomain() ?: ColorProfile.OFF
        }

    override suspend fun upsert(profile: ColorProfile): Long {
        require(!profile.builtIn) { "Built-in-Profile dürfen nicht verändert werden" }
        return dao.upsert(profile.toEntity())
    }

    override suspend fun delete(id: Long) = dao.deleteCustom(id)

    override suspend fun deleteByPluginPackage(pkg: String) = dao.deleteByPluginPackage(pkg)

    override suspend fun setActive(id: Long) = setActivePointer(id)
}

private fun ColorProfileEntity.toDomain() = ColorProfile(
    id = id, name = name, saturation = saturation, contrast = contrast, brightness = brightness,
    blackPoint = blackPoint, whitePoint = whitePoint, gamma = gamma,
    sharpenAmount = sharpenAmount, sharpenRadius = sharpenRadius,
    ditherMode = runCatching { DitherMode.valueOf(ditherMode) }.getOrDefault(DitherMode.NONE),
    ditherLevels = ditherLevels, builtIn = builtIn, pluginPackage = pluginPackage,
)

private fun ColorProfile.toEntity() = ColorProfileEntity(
    id = id, name = name, saturation = saturation, contrast = contrast, brightness = brightness,
    blackPoint = blackPoint, whitePoint = whitePoint, gamma = gamma,
    sharpenAmount = sharpenAmount, sharpenRadius = sharpenRadius,
    ditherMode = ditherMode.name, ditherLevels = ditherLevels, builtIn = builtIn, pluginPackage = pluginPackage,
)
