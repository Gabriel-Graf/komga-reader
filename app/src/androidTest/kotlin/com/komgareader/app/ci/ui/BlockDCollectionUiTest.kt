package com.komgareader.app.ci.ui

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.komgareader.app.ci.CiKomga
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test
import org.junit.runner.RunWith

/** Spec §9 Block D — Sammlung über das UI anlegen (D14, nur Erstellen; Sync separat). */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class BlockDCollectionUiTest : UiTestBase() {

    @Test fun d14_sammlung_anlegen() {
        inject(); seedServers(CiKomga.A); launch()

        composeRule.onNodeWithText("Sammlungen").performClick()
        composeRule.onNodeWithContentDescription("Neue Sammlung").performClick()
        composeRule.onNodeWithText("Name der Sammlung").performTextInput("Meine Sammlung")
        composeRule.onNodeWithText("Erstellen").performClick()

        // Die neue Sammlung muss in der Liste erscheinen.
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("Meine Sammlung", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
