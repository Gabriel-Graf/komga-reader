package com.komgareader.app.ci.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec-Garantie „Plugin lädt UND ist in der UI integriert": das installierte Kavita-Quellen-Plugin
 * erscheint im Plugins-Tab. Server-los — listet nur installierte APKs (realer PluginHost aus AppModule).
 * Voraussetzung: das Kavita-Plugin-APK ist installiert. Sonst (z.B. CI ohne APK-Provisioning) wird
 * der Test übersprungen statt rot — Präkondition, kein Fehler.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class PluginTabUiTest : UiTestBase() {

    @Test fun installiertes_kavita_plugin_erscheint_im_plugins_tab() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val kavitaInstalled = runCatching {
            ctx.packageManager.getPackageInfo("com.komgareader.plugin.kavita", 0); true
        }.getOrDefault(false)
        assumeTrue("Kavita-Plugin-APK nicht installiert — übersprungen (CI-APK-Provisioning)", kavitaInstalled)

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
