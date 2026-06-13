package com.komgareader.domain.eink

/** What is currently on screen — drives which E-Ink profile is applied. */
enum class EinkContext { HOME, PAGED, WEBTOON, COMIC, NOVEL }

/**
 * A device-advertised E-Ink mode option (refresh or colour). [id] is stable and persisted;
 * [label] is the device's default display name (the app may override it via i18n for known
 * built-in ids).
 */
data class EinkModeOption(val id: String, val label: String)

/** A per-context profile. `null` on an axis means "leave the device/system default untouched". */
data class EinkContextProfile(
    val refreshModeId: String? = null,
    val colorModeId: String? = null,
)

/**
 * Merges a user override over a device default, per axis: a set override wins, an unset
 * (null) axis falls back to the device default. Pure — no device/Onyx knowledge.
 */
fun resolveEinkProfile(
    userOverride: EinkContextProfile?,
    deviceDefault: EinkContextProfile,
): EinkContextProfile = EinkContextProfile(
    refreshModeId = userOverride?.refreshModeId ?: deviceDefault.refreshModeId,
    colorModeId = userOverride?.colorModeId ?: deviceDefault.colorModeId,
)
