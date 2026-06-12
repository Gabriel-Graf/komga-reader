package com.komgareader.ui.slots

/**
 * Capability-Surface der Settings-Region: die host-gebauten [SettingsSection]s + der Such-[query].
 * Ein [SettingsSlot]-Pack ordnet sie an (Sidebar/Accordion/flach) und
 * besitzt den Navigations-State selbst (welche Sektion aktiv/aufgeklappt). Die Sektions-Inhalte
 * (`section.content`) sind host-gebaut — das Pack platziert sie nur, baut sie nie neu
 * („UI neu, Kernlogik gleich"). E-Ink-Invarianten bleiben host-erzwungen, nicht Teil dieser Surface.
 */
data class SettingsState(
    val sections: List<SettingsSection>,
    val query: String,
)
