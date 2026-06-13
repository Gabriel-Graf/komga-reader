package com.komgareader.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.i18n.Strings
import com.komgareader.app.ui.components.PickerModal
import com.komgareader.app.ui.components.PickerRow
import com.komgareader.app.ui.components.ScopeHeader
import com.komgareader.app.ui.components.SettingsGroupIndent
import com.komgareader.domain.eink.EinkContext
import com.komgareader.domain.eink.EinkContextProfile
import com.komgareader.domain.eink.EinkModeOption
import com.komgareader.ui.theme.EinkTokens

/**
 * Settings content for the "E-Ink Dynamics" section.
 *
 * Renders one [ScopeHeader] per [EinkContext] with two [PickerRow]s:
 * - Refresh mode (from [SettingsViewModel.einkRefreshModes])
 * - Colour mode (from [SettingsViewModel.einkColorModes])
 *
 * Each axis prepends a "Device default" option (null persisted value).
 * Selecting an option immediately calls the appropriate VM setter.
 * Only called when at least one capability axis is non-empty (Boox).
 */
@Composable
fun EinkDynamicsSettingsContent(viewModel: SettingsViewModel) {
    val s = LocalStrings.current
    val profiles by viewModel.einkContextProfiles.collectAsState()
    val refreshModes = viewModel.einkRefreshModes
    val colorModes = viewModel.einkColorModes

    // One PickerModal open at a time — E-Ink invariant: null = closed.
    var openPicker by remember { mutableStateOf<OpenPicker?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(EinkTokens.sectionGap)) {
        EinkContext.entries.forEach { ctx ->
            val profile = profiles[ctx] ?: EinkContextProfile()
            ContextScope(
                s = s,
                context = ctx,
                profile = profile,
                refreshModes = refreshModes,
                colorModes = colorModes,
                onOpenRefreshPicker = { openPicker = OpenPicker.Refresh(ctx) },
                onOpenColorPicker = { openPicker = OpenPicker.Color(ctx) },
            )
        }
    }

    // Render the single active PickerModal outside the Column (no re-entry).
    when (val picker = openPicker) {
        is OpenPicker.Refresh -> {
            val profile = profiles[picker.context] ?: EinkContextProfile()
            val options = buildModeOptions(s.einkModeDeviceDefault, refreshModes) { refreshModeLabel(s, it) }
            PickerModal(
                title = "${contextLabel(s, picker.context)} – ${s.einkAxisRefresh}",
                options = options,
                selectedKey = profile.refreshModeId ?: KEY_DEVICE_DEFAULT,
                keyOf = { it.key },
                labelOf = { it.label },
                onSelect = { key ->
                    viewModel.setEinkRefreshMode(picker.context, key.nullIfDeviceDefault())
                },
                onDismiss = { openPicker = null },
                closeLabel = s.close,
            )
        }
        is OpenPicker.Color -> {
            val profile = profiles[picker.context] ?: EinkContextProfile()
            val options = buildModeOptions(s.einkModeDeviceDefault, colorModes) { colorModeLabel(s, it) }
            PickerModal(
                title = "${contextLabel(s, picker.context)} – ${s.einkAxisColor}",
                options = options,
                selectedKey = profile.colorModeId ?: KEY_DEVICE_DEFAULT,
                keyOf = { it.key },
                labelOf = { it.label },
                onSelect = { key ->
                    viewModel.setEinkColorMode(picker.context, key.nullIfDeviceDefault())
                },
                onDismiss = { openPicker = null },
                closeLabel = s.close,
            )
        }
        null -> Unit
    }
}

// ---------------------------------------------------------------------------
// Internal helpers
// ---------------------------------------------------------------------------

/** Discriminated union for which picker is currently open. */
private sealed interface OpenPicker {
    data class Refresh(val context: EinkContext) : OpenPicker
    data class Color(val context: EinkContext) : OpenPicker
}

/** A (key, label) pair fed into [PickerModal]. */
private data class ModeEntry(val key: String, val label: String)

/** Sentinel key for the "Device default" (null) choice. */
private const val KEY_DEVICE_DEFAULT = "__device_default__"

private fun String.nullIfDeviceDefault(): String? =
    if (this == KEY_DEVICE_DEFAULT) null else this

/**
 * Prepend the "Device default" entry before the device-advertised modes.
 *
 * [labelOf] maps each [EinkModeOption] to its display label — pass [refreshModeLabel] or
 * [colorModeLabel] so that known mode ids show the i18n label instead of the raw device string.
 */
private fun buildModeOptions(
    defaultLabel: String,
    modes: List<EinkModeOption>,
    labelOf: (EinkModeOption) -> String,
): List<ModeEntry> =
    buildList {
        add(ModeEntry(KEY_DEVICE_DEFAULT, defaultLabel))
        modes.forEach { add(ModeEntry(it.id, labelOf(it))) }
    }

/** Localised label for each [EinkContext]. */
private fun contextLabel(s: Strings, context: EinkContext): String = when (context) {
    EinkContext.HOME -> s.einkContextHome
    EinkContext.PAGED -> s.einkContextPaged
    EinkContext.WEBTOON -> s.einkContextWebtoon
    EinkContext.COMIC -> s.einkContextComic
    EinkContext.NOVEL -> s.einkContextNovel
}

/** Localised label for a known mode id; falls back to the device-supplied label. */
private fun refreshModeLabel(s: Strings, option: EinkModeOption): String = when (option.id) {
    "hd" -> s.einkRefreshHd
    "balanced" -> s.einkRefreshBalanced
    "regal" -> s.einkRefreshRegal
    "speed" -> s.einkRefreshSpeed
    "ultra" -> s.einkRefreshUltra
    else -> option.label
}

private fun colorModeLabel(s: Strings, option: EinkModeOption): String = when (option.id) {
    "system" -> s.einkColorSystem
    "color" -> s.einkColorColor
    "mono" -> s.einkColorMono
    else -> option.label
}

/** Returns the display label for a mode id on the refresh axis (device default → s.einkModeDeviceDefault). */
private fun refreshDisplayLabel(s: Strings, id: String?, modes: List<EinkModeOption>): String =
    if (id == null) s.einkModeDeviceDefault
    else modes.firstOrNull { it.id == id }?.let { refreshModeLabel(s, it) } ?: id

private fun colorDisplayLabel(s: Strings, id: String?, modes: List<EinkModeOption>): String =
    if (id == null) s.einkModeDeviceDefault
    else modes.firstOrNull { it.id == id }?.let { colorModeLabel(s, it) } ?: id

/** One context block: a scope header + up to two picker rows (one per non-empty axis). */
@Composable
private fun ContextScope(
    s: Strings,
    context: EinkContext,
    profile: EinkContextProfile,
    refreshModes: List<EinkModeOption>,
    colorModes: List<EinkModeOption>,
    onOpenRefreshPicker: () -> Unit,
    onOpenColorPicker: () -> Unit,
) {
    Column {
        ScopeHeader(contextLabel(s, context))
        Column(Modifier.padding(start = SettingsGroupIndent)) {
            if (refreshModes.isNotEmpty()) {
                PickerRow(
                    label = s.einkAxisRefresh,
                    value = refreshDisplayLabel(s, profile.refreshModeId, refreshModes),
                    onClick = onOpenRefreshPicker,
                )
            }
            if (colorModes.isNotEmpty()) {
                PickerRow(
                    label = s.einkAxisColor,
                    value = colorDisplayLabel(s, profile.colorModeId, colorModes),
                    onClick = onOpenColorPicker,
                )
            }
        }
    }
}
