package com.komgareader.app.ci.ui

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.komgareader.app.MainActivity
import com.komgareader.app.ci.CiFixtures
import com.komgareader.app.ci.CiKomga
import com.komgareader.domain.repository.ServerRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * Beweist die UI-Test-Infrastruktur end-to-end: in-memory-DB (Hilt) + vor-Start-Seeding eines
 * CI-Servers + echtes MainActivity-Compose-UI + Live-Bibliothek aus der CI-Komga.
 *
 * `createEmptyComposeRule` startet KEINE Activity automatisch — wir seeden zuerst die DB und
 * starten MainActivity erst danach manuell, sonst läse die App die (leere) DB vor dem Seed.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class HiltUiSmokeTest {

    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createEmptyComposeRule()

    @Inject lateinit var servers: ServerRepository

    @Before fun seedAndLaunch() {
        hiltRule.inject()
        runBlocking { servers.save(CiKomga.A) }   // in-memory-DB → isoliert
        ActivityScenario.launch(MainActivity::class.java)
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
