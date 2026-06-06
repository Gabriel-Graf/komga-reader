package com.komgareader.app

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import com.komgareader.app.ui.library.LibraryScreen
import com.komgareader.app.ui.reader.ReaderRoute
import com.komgareader.app.ui.series.SeriesDetailScreen
import com.komgareader.app.ui.settings.SettingsScreen
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val themeModeStr by settingsViewModel.themeMode.collectAsState()
            val languageStr by settingsViewModel.language.collectAsState()

            val themeMode = runCatching { ThemeMode.valueOf(themeModeStr) }.getOrDefault(ThemeMode.SYSTEM)
            val language = if (languageStr == "en") Language.EN else Language.DE

            CompositionLocalProvider(LocalStrings provides stringsFor(language)) {
                KomgaReaderTheme(themeMode = themeMode) {
                    val nav = rememberNavController()
                    NavHost(navController = nav, startDestination = "library") {
                        composable("library") {
                            LibraryScreen(
                                onOpenSettings = { nav.navigate("settings") },
                                onOpenSeries = { seriesId -> nav.navigate("series/$seriesId") },
                            )
                        }
                        composable("settings") {
                            SettingsScreen(onBack = { nav.popBackStack() })
                        }
                        composable(
                            route = "series/{seriesId}",
                            arguments = listOf(navArgument("seriesId") { type = NavType.StringType }),
                        ) {
                            SeriesDetailScreen(
                                onBack = { nav.popBackStack() },
                                onOpenBook = { bookId, pageCount, format ->
                                    nav.navigate("reader/$bookId/$pageCount/$format")
                                },
                            )
                        }
                        composable(
                            route = "reader/{bookId}/{pageCount}/{format}",
                            arguments = listOf(
                                navArgument("bookId") { type = NavType.StringType },
                                navArgument("pageCount") { type = NavType.IntType },
                                navArgument("format") { type = NavType.StringType },
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
