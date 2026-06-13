package com.komgareader.ui.slots

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

/** Die einzelnen Settings-Sektionen. Reihenfolge = Sidebar-/Accordion-Reihenfolge. */
enum class SettingsSectionId { CONNECTION, APPEARANCE, COLOR_FILTER, READER, EINK_DYNAMICS, DOWNLOADS, LANGUAGE, ABOUT }

/**
 * Eine Settings-Sektion: Metadaten + such-Terme + ihr Inhalt.
 *
 * @param scrollable false, wenn die Sektion ihr eigenes Scrollen verwaltet (z. B. Farbfilter
 *   pinnt die Vorschau und scrollt nur die Regler) — der Host gibt dann eine gebundene Höhe
 *   statt selbst zu scrollen.
 */
data class SettingsSection(
    val id: SettingsSectionId,
    val icon: ImageVector,
    val title: String,
    val searchTerms: List<String>,
    val scrollable: Boolean = true,
    val content: @Composable (query: String) -> Unit,
)
