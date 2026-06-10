package com.komgareader.app.ci.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.komgareader.app.ci.CiFixtures
import com.komgareader.app.ci.CiKomga
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec §9 Block B/C — Bibliothek→Serie→Reader (B7) und deterministischer Reader-Dispatch (C9/C11).
 * Manga(CBZ) → Paged-Reader, Novel(EPUB) → Novel-Reader (Kern-Invariante #4).
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class BlockCReaderDispatchUiTest : UiTestBase() {

    private fun openFirstBookOf(seriesTitle: String) {
        composeRule.waitUntil(20_000) {
            composeRule.onAllNodesWithText(seriesTitle, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        // B7: Tile antippen → SeriesDetail.
        composeRule.onAllNodesWithText(seriesTitle, substring = true)[0].performClick()
        // „Lesen" öffnet den Reader.
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodesWithText("Lesen").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Lesen").performClick()
    }

    /** C9: Manga (CBZ, kein Shelf-Tag) → Paged-Reader. */
    @Test fun c9_manga_oeffnet_paged_reader() {
        inject(); seedServers(CiKomga.A); launch()
        openFirstBookOf(CiFixtures.MANGA_SERIES)
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodesWithTag("reader_paged").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("reader_paged").assertIsDisplayed()
    }

    /** C11: Novel (EPUB) → Novel-Reader (Format schlägt alles). */
    @Test fun c11_novel_oeffnet_novel_reader() {
        inject(); seedServers(CiKomga.A); launch()
        openFirstBookOf(CiFixtures.NOVELS_A.first())  // "Alpha-Novel"
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodesWithTag("reader_novel").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("reader_novel").assertIsDisplayed()
    }
}
