package com.komgareader.app.ui.plugins

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.i18n.StringsEn
import com.komgareader.app.ui.theme.KomgaReaderTheme
import com.komgareader.data.plugin.repo.BrowsableEntry
import com.komgareader.data.plugin.repo.BrowserRow
import com.komgareader.data.plugin.repo.InstallState
import com.komgareader.data.plugin.repo.PluginKind
import com.komgareader.data.plugin.repo.RepoPluginEntry
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI test for [PluginInfoModal] (no network) covering the two README branches:
 * (a) [ReadmeState.Empty] renders the entry description fallback;
 * (b) [ReadmeState.Loaded] renders the markdown text.
 *
 * The modal reads MaterialTheme/[LocalStrings]/[com.komgareader.app.ui.components.LocalEinkMode];
 * [KomgaReaderTheme] supplies MaterialTheme + design tokens + resolved slots, and [LocalStrings]
 * is provided explicitly. The asserted text lives inside the [com.komgareader.app.ui.components.EinkInfoDialog]
 * window, which the default (merged) semantics tree still exposes to [onNodeWithText].
 */
class PluginInfoModalTest {
    @get:Rule val rule = createComposeRule()

    private fun row(description: String = "", license: String = "") = BrowserRow(
        item = BrowsableEntry(
            entry = RepoPluginEntry(
                packageName = "com.x",
                name = "Demo Plugin",
                description = description,
                versionName = "1.2",
                apkUrl = "x.apk",
                fingerprint = "ab",
                versionCode = 1,
                license = license,
            ),
            repoName = "R",
            repoUrl = "https://r/repo.json",
            kind = PluginKind.SOURCE,
        ),
        state = InstallState.NOT_INSTALLED,
        compatible = true,
    )

    @Test fun emptyReadmeShowsDescription() {
        rule.setContent {
            CompositionLocalProvider(LocalStrings provides StringsEn) {
                KomgaReaderTheme {
                    PluginInfoModal(
                        row = row(description = "Eine Demo-Beschreibung."),
                        readme = ReadmeState.Empty,
                        onDismiss = {},
                    )
                }
            }
        }
        // The description is a raw data field from the entry (not a localised string), so it renders
        // verbatim regardless of the provided StringsEn locale.
        rule.onNodeWithText("Eine Demo-Beschreibung.").assertIsDisplayed()
    }

    @Test fun loadedReadmeShowsMarkdownText() {
        rule.setContent {
            CompositionLocalProvider(LocalStrings provides StringsEn) {
                KomgaReaderTheme {
                    PluginInfoModal(
                        row = row(),
                        readme = ReadmeState.Loaded("Hallo Welt Markdown"),
                        onDismiss = {},
                    )
                }
            }
        }
        rule.onNodeWithText("Hallo Welt Markdown", substring = true).assertIsDisplayed()
    }
}
