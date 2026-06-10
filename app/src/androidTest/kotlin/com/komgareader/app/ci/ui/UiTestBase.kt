package com.komgareader.app.ci.ui

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import com.komgareader.app.MainActivity
import com.komgareader.domain.repository.ServerConfig
import com.komgareader.domain.repository.ServerRepository
import dagger.hilt.android.testing.HiltAndroidRule
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import javax.inject.Inject

/**
 * Basis für alle Compose-UI-Integrationstests: isolierte Hilt-in-memory-DB (über [TestDataModule]),
 * Seed VOR dem Activity-Start, dann manueller Launch. Subklassen seeden in @Before via [seedServers]
 * und rufen [launch]. Selektion über sichtbare DE-Texte (keine testTags außer Reader-Roots).
 */
abstract class UiTestBase {

    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createEmptyComposeRule()

    @Inject lateinit var servers: ServerRepository

    /** Injiziert den Hilt-Graphen. In @Before VOR dem Seeden aufrufen. */
    fun inject() = hiltRule.inject()

    fun seedServers(vararg configs: ServerConfig) =
        runBlocking { configs.forEach { servers.save(it) } }

    /** Startet MainActivity — erst NACH dem Seeden aufrufen. */
    fun launch() { ActivityScenario.launch(MainActivity::class.java) }
}
