package com.komgareader.app

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.komgareader.app.eink.HardwareButtonBus
import com.komgareader.app.i18n.Language
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.i18n.stringsFor
import com.komgareader.app.ui.components.LocalEinkMode
import com.komgareader.app.ui.components.LocalImageFilter
import com.komgareader.app.ui.components.toColorFilterOrNull
import com.komgareader.app.ui.groups.GroupBrowseRoute
import com.komgareader.app.ui.home.HomeScreen
import com.komgareader.app.ui.reader.ReaderRoute
import com.komgareader.app.ui.series.SeriesDetailScreen
import com.komgareader.app.ui.settings.AboutScreen
import com.komgareader.app.ui.settings.AppearanceSettingsScreen
import com.komgareader.app.ui.settings.ConnectionSettingsScreen
import com.komgareader.app.ui.settings.DownloadsSettingsScreen
import com.komgareader.app.ui.settings.LanguageSettingsScreen
import com.komgareader.app.ui.settings.ReaderSettingsScreen
import com.komgareader.app.ui.settings.SettingsPage
import com.komgareader.app.ui.settings.SettingsViewModel
import com.komgareader.app.ui.theme.KomgaReaderTheme
import com.komgareader.app.ui.theme.ThemeMode
import com.komgareader.domain.eink.ButtonEvent
import com.komgareader.domain.eink.HardwareButton
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var buttonBus: HardwareButtonBus

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                buttonBus.emit(ButtonEvent(HardwareButton.PAGE_PREV))
                true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                buttonBus.emit(ButtonEvent(HardwareButton.PAGE_NEXT))
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    /** Blendet Status- UND Navigationsleiste dauerhaft aus → echtes Vollbild ohne weiße System-Streifen. */
    private fun enterFullscreen() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterFullscreen()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enterFullscreen()
        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val themeModeStr by settingsViewModel.themeMode.collectAsState()
            val languageStr by settingsViewModel.language.collectAsState()
            val displayModeStr by settingsViewModel.displayMode.collectAsState()
            val activeColorProfile by settingsViewModel.activeColorProfile.collectAsState()

            val themeMode = runCatching { ThemeMode.valueOf(themeModeStr) }.getOrDefault(ThemeMode.SYSTEM)
            val language = if (languageStr == "en") Language.EN else Language.DE
            val isEink = displayModeStr != "SMARTPHONE" // Default E-Ink

            CompositionLocalProvider(
                LocalStrings provides stringsFor(language),
                LocalEinkMode provides isEink,
                LocalImageFilter provides activeColorProfile.toColorFilterOrNull(),
            ) {
                KomgaReaderTheme(themeMode = themeMode) {
                    val nav = rememberNavController()
                    NavHost(navController = nav, startDestination = "home") {
                        composable("home") {
                            HomeScreen(
                                onOpenSeries = { seriesId -> nav.navigate("series/$seriesId") },
                                onOpenGroup = { shelfId, _ -> nav.navigate("group/$shelfId") },
                                onOpenSettingsPage = { page -> nav.navigate(settingsRoute(page)) },
                            )
                        }
                        composable("settings/connection") {
                            ConnectionSettingsScreen(onBack = { nav.popBackStack() })
                        }
                        composable("settings/appearance") {
                            AppearanceSettingsScreen(onBack = { nav.popBackStack() })
                        }
                        composable("settings/reader") {
                            ReaderSettingsScreen(onBack = { nav.popBackStack() })
                        }
                        composable("settings/downloads") {
                            DownloadsSettingsScreen(onBack = { nav.popBackStack() })
                        }
                        composable("settings/language") {
                            LanguageSettingsScreen(onBack = { nav.popBackStack() })
                        }
                        composable("settings/about") {
                            AboutScreen(onBack = { nav.popBackStack() })
                        }
                        composable(
                            route = "series/{seriesId}",
                            arguments = listOf(navArgument("seriesId") { type = NavType.StringType }),
                        ) {
                            SeriesDetailScreen(
                                onBack = { nav.popBackStack() },
                                onOpenBook = { bookId, pageCount, format, forceStream, viewerMode ->
                                    nav.navigate("reader/$bookId/$pageCount/$format/$forceStream/$viewerMode")
                                },
                            )
                        }
                        composable(
                            route = "group/{shelfId}",
                            arguments = listOf(navArgument("shelfId") { type = NavType.LongType }),
                        ) { backEntry ->
                            val shelfId = backEntry.arguments?.getLong("shelfId") ?: return@composable
                            GroupBrowseRoute(
                                shelfId = shelfId,
                                onBack = { nav.popBackStack() },
                                onOpenSeries = { seriesId ->
                                    nav.navigate("series_vm/$seriesId/$shelfId")
                                },
                            )
                        }
                        composable(
                            route = "series_vm/{seriesId}/{shelfId}",
                            arguments = listOf(
                                navArgument("seriesId") { type = NavType.StringType },
                                navArgument("shelfId") { type = NavType.LongType },
                            ),
                        ) {
                            SeriesDetailScreen(
                                onBack = { nav.popBackStack() },
                                onOpenBook = { bookId, pageCount, format, forceStream, viewerMode ->
                                    nav.navigate("reader/$bookId/$pageCount/$format/$forceStream/$viewerMode")
                                },
                            )
                        }
                        composable(
                            route = "reader/{bookId}/{pageCount}/{format}/{stream}",
                            arguments = listOf(
                                navArgument("bookId") { type = NavType.StringType },
                                navArgument("pageCount") { type = NavType.IntType },
                                navArgument("format") { type = NavType.StringType },
                                navArgument("stream") { type = NavType.BoolType; defaultValue = false },
                            ),
                        ) {
                            ReaderRoute(onBack = { nav.popBackStack() })
                        }
                        composable(
                            route = "reader/{bookId}/{pageCount}/{format}/{stream}/{viewerMode}",
                            arguments = listOf(
                                navArgument("bookId") { type = NavType.StringType },
                                navArgument("pageCount") { type = NavType.IntType },
                                navArgument("format") { type = NavType.StringType },
                                navArgument("stream") { type = NavType.BoolType; defaultValue = false },
                                navArgument("viewerMode") { type = NavType.StringType; defaultValue = "PAGED" },
                            ),
                        ) {
                            ReaderRoute(onBack = { nav.popBackStack() })
                        }
                    }
                }
            }
        }
    }
}

/** Mappt eine Settings-Unterseite auf ihre NavHost-Route. */
private fun settingsRoute(page: SettingsPage): String = when (page) {
    SettingsPage.CONNECTION -> "settings/connection"
    SettingsPage.APPEARANCE -> "settings/appearance"
    SettingsPage.READER -> "settings/reader"
    SettingsPage.DOWNLOADS -> "settings/downloads"
    SettingsPage.LANGUAGE -> "settings/language"
    SettingsPage.ABOUT -> "settings/about"
}
