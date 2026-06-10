package com.komgareader.app.ci.ui

import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.komgareader.app.ci.CiFixtures
import com.komgareader.app.ci.CiKomga
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Beweist die UI-Test-Infrastruktur end-to-end: in-memory-DB (Hilt) + vor-Start-Seeding eines
 * CI-Servers + echtes MainActivity-Compose-UI + Live-Bibliothek aus der CI-Komga.
 *
 * `createEmptyComposeRule` startet KEINE Activity automatisch — wir seeden zuerst die DB und
 * starten MainActivity erst danach manuell, sonst läse die App die (leere) DB vor dem Seed.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class HiltUiSmokeTest : UiTestBase() {

    @Before fun seedAndLaunch() {
        inject()
        seedServers(CiKomga.A)   // in-memory-DB → isoliert
        launch()
    }

    /** A1 (UI-Smoke): nach dem Start erscheint die Live-Bibliothek mit der Manga-Serie. */
    @Test fun bibliothek_zeigt_seeded_server_inhalt() {
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithText(CiFixtures.MANGA_SERIES, substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText(CiFixtures.MANGA_SERIES, substring = true)
            .fetchSemanticsNodes().isNotEmpty().let { found ->
                assert(found) { "Manga-Serie '${CiFixtures.MANGA_SERIES}' muss in der Bibliothek erscheinen" }
            }
    }
}
