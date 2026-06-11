package com.komgareader.app.ci.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec-Garantie „Plugin lädt UND ist in der UI integriert": das installierte Kavita-Quellen-Plugin
 * erscheint im Plugins-Tab. Server-los — listet nur installierte APKs (realer PluginHost aus AppModule).
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class PluginTabUiTest : UiTestBase() {

    @Test fun installiertes_kavita_plugin_erscheint_im_plugins_tab() {
        inject()
        launch()   // kein Server nötig — Plugin-Discovery ist serverunabhängig

        // Zum Plugins-Tab.
        composeRule.onNodeWithText("Plugins").performClick()

        // Das installierte Kavita-Plugin muss als Zeile erscheinen (displayName "Kavita").
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodesWithText("Kavita", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Kavita", substring = true).assertIsDisplayed()
    }
}
