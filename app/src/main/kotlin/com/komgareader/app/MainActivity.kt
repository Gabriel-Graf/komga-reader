package com.komgareader.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.komgareader.app.i18n.Language
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.i18n.stringsFor
import com.komgareader.app.ui.library.LibraryScreen
import com.komgareader.app.ui.settings.SettingsScreen
import com.komgareader.app.ui.settings.SettingsViewModel
import com.komgareader.app.ui.theme.KomgaReaderTheme
import com.komgareader.app.ui.theme.ThemeMode
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
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
                            LibraryScreen(onOpenSettings = { nav.navigate("settings") })
                        }
                        composable("settings") {
                            SettingsScreen(onBack = { nav.popBackStack() })
                        }
                    }
                }
            }
        }
    }
}
