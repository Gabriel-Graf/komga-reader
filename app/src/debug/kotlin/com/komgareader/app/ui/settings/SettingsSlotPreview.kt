package com.komgareader.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.komgareader.app.ui.icons.AppIcons
import com.komgareader.app.ui.slots.LocalResolvedSlots
import com.komgareader.app.ui.slots.SettingsSlot
import com.komgareader.app.ui.slots.UiSlotPack
import com.komgareader.app.ui.slots.UiSlots
import com.komgareader.app.ui.theme.EinkTokens

/**
 * Swap-Beweis: ein alternatives Settings-Skelett — eine **flache Einzel-Scroll-Liste** aller
 * Sektionen (Titel-Header + `section.content(query)` untereinander, ohne Sidebar/Accordion), das
 * dieselbe [SettingsState]-Surface anders anordnet, ohne eine Aufrufstelle anzufassen. Belegt, dass
 * ein UI-Pack das Settings-Layout über die `settings`-Region ersetzen kann, während die Call-Sites
 * unverändert `SettingsScreen(...)` rufen. NUR Debug/Preview, keine Nutzer-Einstellung.
 */
@Composable
fun AlternativeSettings(state: SettingsState) {
    val visible = if (state.query.isBlank()) {
        state.sections
    } else {
        state.sections.filter { sectionMatches(it.searchTerms, state.query) }
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(EinkTokens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        visible.forEach { section ->
            Text(section.title, style = MaterialTheme.typography.titleLarge)
            // section.scrollable wird in diesem Swap-Beweis bewusst ignoriert (kein scrollable=false-
            // Abschnitt im Fake-State). Produktive Packs müssen scrollable auswerten (vgl. DefaultSettings).
            section.content(state.query)
        }
    }
}

/**
 * Zeigt, dass dieselbe [SettingsState]-Surface über ein Pack mit alternativem `settings`-Slot das
 * [AlternativeSettings]-Skelett rendert — gleiche Surface, anderes Layout, Call-Site unverändert.
 * Gespeist mit einer kleinen Fake-[SettingsState] (Dummy-Sektionen), da `hiltViewModel()` im Preview
 * nicht verfügbar ist.
 */
@Preview(widthDp = 1264, heightDp = 600)
@Composable
private fun AlternativeSettingsPreview() {
    val alternative: SettingsSlot = { state -> AlternativeSettings(state) }
    val fakeState = SettingsState(
        sections = listOf(
            SettingsSection(
                id = SettingsSectionId.APPEARANCE,
                icon = AppIcons.Contrast,
                title = "Darstellung",
                searchTerms = listOf("Darstellung"),
                content = { Text("Inhalt der Sektion Darstellung.") },
            ),
            SettingsSection(
                id = SettingsSectionId.ABOUT,
                icon = AppIcons.Info,
                title = "Über",
                searchTerms = listOf("Über"),
                content = { Text("Inhalt der Sektion Über.") },
            ),
        ),
        query = "",
    )
    CompositionLocalProvider(
        LocalResolvedSlots provides UiSlots.resolve(UiSlotPack(settings = alternative)),
    ) {
        LocalResolvedSlots.current.settings(fakeState)
    }
}
