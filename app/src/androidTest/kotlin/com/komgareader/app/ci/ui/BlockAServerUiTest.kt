package com.komgareader.app.ci.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.komgareader.app.ci.CiFixtures
import com.komgareader.app.ci.CiKomga
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test
import org.junit.runner.RunWith

/** Spec §9 Block A — Server über das echte UI hinzufügen (A1) und entfernen (A4). */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class BlockAServerUiTest : UiTestBase() {

    /**
     * A1: Nutzer fügt über das UI einen Server hinzu (Felder ausfüllen → Speichern) und sieht
     * danach die Live-Bibliothek. Kein DB-Seeding — der echte Add-Server-Flow IST der Test.
     */
    @Test fun a1_server_ueber_ui_hinzufuegen_zeigt_bibliothek() {
        inject(); launch()   // ohne Seed starten — leere Bibliothek

        // Zu den Einstellungen → Verbindung → "+".
        composeRule.onNodeWithText("Einstellungen").performClick()
        composeRule.onNodeWithText("Verbindung").performClick()
        composeRule.onNodeWithContentDescription("Server hinzufügen").performClick()

        // Felder ausfüllen (Basic Auth gegen CI-Komga-A).
        composeRule.onNodeWithText("Anzeigename").performTextInput("CI-A")
        composeRule.onNodeWithText("Server-URL").performTextInput(CiKomga.A.baseUrl)
        composeRule.onNodeWithText("Benutzername").performTextInput(CiKomga.ADMIN_USER)
        composeRule.onNodeWithText("Passwort").performTextInput(CiKomga.ADMIN_PASS)
        composeRule.onNodeWithText("Speichern").performClick()

        // Zurück zur Bibliothek („Stöbern") → Live-Inhalt muss erscheinen.
        composeRule.onNodeWithText("Stöbern").performClick()
        composeRule.waitUntil(20_000) {
            composeRule.onAllNodesWithText(CiFixtures.MANGA_SERIES, substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * A4: Vorkonfigurierten Server über das UI entfernen → Bibliothek fällt auf den Leerzustand,
     * App bricht nicht.
     */
    @Test fun a4_server_entfernen_zeigt_leerzustand() {
        inject()
        seedServers(CiKomga.A)
        launch()

        // Warten bis Bibliothek geladen, dann entfernen.
        composeRule.waitUntil(20_000) {
            composeRule.onAllNodesWithText(CiFixtures.MANGA_SERIES, substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Einstellungen").performClick()
        composeRule.onNodeWithText("Verbindung").performClick()
        composeRule.onNodeWithContentDescription("Entfernen").performClick()

        // Zurück zur Bibliothek → Leerzustand-Text.
        composeRule.onNodeWithText("Stöbern").performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("Noch keine Inhalte", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }
}
