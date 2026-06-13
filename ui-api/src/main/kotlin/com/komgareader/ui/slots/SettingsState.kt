package com.komgareader.ui.slots

/**
 * Capability surface of the settings region: the host-built [SettingsSection]s + the search [query].
 * A [SettingsSlot] pack arranges them (sidebar/accordion/flat) and owns the navigation state itself
 * (which section is active/expanded). The section contents (`section.content`) are host-built — the
 * pack only places them, never rebuilds them ("new UI, same core logic"). E-Ink invariants stay
 * host-enforced, not part of this surface.
 *
 * [initialSectionId] is an optional host hint to open a specific section first (e.g. a deep link from
 * the update banner to "About"); null = the pack's own default.
 */
data class SettingsState(
    val sections: List<SettingsSection>,
    val query: String,
    val initialSectionId: SettingsSectionId? = null,
)
