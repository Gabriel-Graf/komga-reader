package com.komgareader.data.eink

import com.komgareader.domain.eink.EinkContext
import com.komgareader.domain.eink.EinkContextProfile
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

@Serializable
private data class ProfileDto(val refreshModeId: String? = null, val colorModeId: String? = null)

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
private val mapSerializer = MapSerializer(String.serializer(), ProfileDto.serializer())

/** Serialises user overrides to a compact JSON object keyed by EinkContext name. */
fun encodeEinkContextProfiles(map: Map<EinkContext, EinkContextProfile>): String {
    val dto = map.entries.associate { (k, v) -> k.name to ProfileDto(v.refreshModeId, v.colorModeId) }
    return json.encodeToString(mapSerializer, dto)
}

/** Parses overrides; null/blank/invalid -> empty map; unknown context keys are skipped. */
fun decodeEinkContextProfiles(raw: String?): Map<EinkContext, EinkContextProfile> {
    if (raw.isNullOrBlank()) return emptyMap()
    val parsed = runCatching { json.decodeFromString(mapSerializer, raw) }.getOrNull()
        ?: return emptyMap()
    return parsed.mapNotNull { (key, dto) ->
        val ctx = runCatching { EinkContext.valueOf(key) }.getOrNull() ?: return@mapNotNull null
        ctx to EinkContextProfile(dto.refreshModeId, dto.colorModeId)
    }.toMap()
}
