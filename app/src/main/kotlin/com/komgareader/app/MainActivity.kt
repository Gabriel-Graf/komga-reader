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
import coil.Coil
import coil.ImageLoader
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
import com.komgareader.app.ui.components.LocalColorProfile
import com.komgareader.app.ui.components.LocalDisplayBehavior
import com.komgareader.app.ui.components.LocalEinkMode
import com.komgareader.app.ui.components.LocalImageFilter
import com.komgareader.app.ui.components.toColorFilterOrNull
import com.komgareader.app.ui.groups.GroupBrowseRoute
import com.komgareader.app.ui.home.HomeScreen
import com.komgareader.app.ui.reader.ReaderRoute
import com.komgareader.app.ui.series.SeriesDetailScreen
import com.komgareader.app.ui.settings.SettingsViewModel
import com.komgareader.app.ui.theme.KomgaReaderTheme
import com.komgareader.app.ui.theme.ThemeMode
import com.komgareader.domain.eink.ButtonEvent
import com.komgareader.domain.eink.EinkController
import com.komgareader.domain.eink.HardwareButton
import com.komgareader.domain.model.DisplayMode
import com.komgareader.domain.model.displayBehaviorFor
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var buttonBus: HardwareButtonBus

    /** Geräte-Naht (Boox vs. No-Op) — liefert die [com.komgareader.domain.eink.EinkCapabilities]. */
    @Inject lateinit var einkController: EinkController

    /**
     * Der per Hilt gebaute [coil.ImageLoader] mit den Quellen-Naht-Fetchern
     * ([com.komgareader.app.data.coil.SourcePageFetcher]/[com.komgareader.app.data.coil.SourceCoverFetcher]).
     * Wird in [onCreate] als Coils prozessweiter Loader gesetzt — sonst nutzen die Compose-`AsyncImage`
     * Coils Default-Singleton, der `SourceImage`/`SourceCover` NICHT auflöst (Cover blank, Seiten schwarz).
     */
    @Inject lateinit var imageLoader: ImageLoader

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
        // Den Naht-fähigen ImageLoader als Coils prozessweiten Loader setzen, BEVOR irgendein
        // AsyncImage komponiert — sonst lädt Coils Default-Singleton SourceImage/SourceCover nicht.
        Coil.setImageLoader(imageLoader)
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
            // Geräteklasse auf zwei orthogonalen Achsen ableiten (Bewegung ⟂ Akzentfarbe):
            // Bewegung folgt der User-Wahl, Akzentfarbe der Hardware (Kaleido). LocalEinkMode
            // bleibt die abgeleitete Brücke (!allowsMotion) für bestehende Animations-Gates.
            val displayMode = runCatching { DisplayMode.valueOf(displayModeStr) }.getOrDefault(DisplayMode.EINK)
            val displayBehavior = displayBehaviorFor(displayMode, einkController.capabilities)

            CompositionLocalProvider(
                LocalStrings provides stringsFor(language),
                LocalDisplayBehavior provides displayBehavior,
                LocalEinkMode provides !displayBehavior.allowsMotion,
                LocalImageFilter provides activeColorProfile.toColorFilterOrNull(),
                LocalColorProfile provides activeColorProfile,
            ) {
                KomgaReaderTheme(themeMode = themeMode) {
                    val nav = rememberNavController()
                    NavHost(navController = nav, startDestination = "home") {
                        composable("home") {
                            HomeScreen(
                                onOpenSeries = { seriesId -> nav.navigate("series/$seriesId") },
                                onOpenGroup = { shelfId, _ -> nav.navigate("group/$shelfId") },
                            )
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
