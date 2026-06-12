package com.komgareader.app.ui.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.ui.slots.LocalResolvedSlots
import com.komgareader.ui.slots.header

/**
 * Settings als **volle Seite über dem Reader** — derselbe [SettingsScreen] wie im
 * Bibliotheks-Tab (DRY, kein Duplikat), nur in einem [Scaffold] mit Header-Bar, deren
 * Zurück-Pfeil ([onBack]) an **erster Position links** in den darunter liegenden Reader
 * zurückführt.
 *
 * Session-Skopierung kommt automatisch aus dem Compose-Back-Stack: Die `settings`-Route
 * wird **über** der konkreten Reader-Route gepusht, also landet `popBackStack()` exakt im
 * Reader, aus dem sie geöffnet wurde. Wird ein anderes Werk geöffnet, entsteht eine neue
 * Reader-Route; die alte Settings/Reader-Paarung ist längst gepoppt → kein veralteter
 * Zurück-Link. Der Settings-**Tab** in `HomeScreen` bleibt unberührt (kein Zurück-zum-Reader).
 *
 * Die Header-Bar läuft über die Slot-Naht ([LocalResolvedSlots]) wie alle Unterseiten —
 * ein UI-Pack ersetzt sie konsistent. Suche bleibt der Einfachheit halber dem Tab
 * vorbehalten (statischer Titel hier); der Reader-Einstieg ist ein Shortcut, kein
 * Such-Einstieg.
 */
@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    Scaffold(
        modifier = modifier,
        topBar = { LocalResolvedSlots.current.header(strings.settingsTitle, onBack) {} },
    ) { padding ->
        SettingsScreen(
            query = "",
            modifier = Modifier.fillMaxSize().padding(padding),
        )
    }
}
