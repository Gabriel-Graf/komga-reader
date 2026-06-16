package com.komgareader.app

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import coil.Coil
import coil.ImageLoader
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import com.komgareader.app.data.ExternalBookOpener
import com.komgareader.app.data.ExternalOpenTarget
import com.komgareader.app.eink.HardwareButtonBus
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.i18n.resolveStrings
import com.komgareader.app.ui.components.EinkConfirmDialog
import com.komgareader.app.ui.components.EinkModal
import com.komgareader.app.ui.components.LocalColorProfile
import com.komgareader.app.ui.components.LocalDisplayBehavior
import com.komgareader.app.ui.components.LocalEinkMode
import com.komgareader.app.ui.components.LocalImageFilter
import com.komgareader.app.ui.components.LocalOnHome
import com.komgareader.app.ui.components.toColorFilterOrNull
import com.komgareader.app.ui.groups.GroupBrowseRoute
import com.komgareader.app.ui.home.HomeScreen
import com.komgareader.app.ui.pack.toIconPack
import com.komgareader.app.ui.pack.tokenOverride
import com.komgareader.app.ui.pack.toUiPackOrNull
import com.komgareader.app.ui.reader.ReaderRoute
import com.komgareader.app.ui.series.SeriesDetailScreen
import com.komgareader.app.ui.settings.SettingsRoute
import com.komgareader.app.ui.settings.SettingsViewModel
import com.komgareader.app.ui.theme.KomgaReaderTheme
import com.komgareader.app.ui.theme.ThemeMode
import com.komgareader.domain.eink.EinkController
import com.komgareader.domain.model.DisplayMode
import com.komgareader.domain.model.ExternalOpenBehavior
import com.komgareader.domain.model.displayBehaviorFor
import com.komgareader.app.ui.components.AuroraSeriesTile
import com.komgareader.ui.icons.ActiveIconPack
import com.komgareader.ui.icons.DefaultIconPack
import com.komgareader.ui.slots.UiSlotPack
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
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

    @Inject lateinit var syncCoordinator: com.komgareader.app.data.SyncCoordinator

    /** Wires an externally-opened book file into the reader (ephemeral open + optional import). */
    @Inject lateinit var externalOpener: ExternalBookOpener

    /**
     * The content:// URI of a VIEW intent waiting to be handled. Compose-observable so the
     * ephemeral-open driver in [setContent] reacts whether the intent arrived at cold start
     * ([onCreate]) or while the activity was already running ([onNewIntent]).
     */
    private val pendingExternalUri = mutableStateOf<Uri?>(null)

    private fun captureViewIntent(intent: android.content.Intent?) {
        if (intent?.action == android.content.Intent.ACTION_VIEW) {
            intent.data?.let { pendingExternalUri.value = it }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        captureViewIntent(intent)
    }

    /**
     * Lets us post the "update installed — tap to open" notification (the reliable relaunch path,
     * since background-activity-launch is blocked). On API 33+ POST_NOTIFICATIONS must be granted at
     * runtime, otherwise the notification is silently dropped.
     */
    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    // Volume keys page-turn only. Long-press / double-press shortcuts were removed: the Onyx Boox Go
    // Color 7 Gen2 firmware intercepts volume keys in its own policy layer before the app, so a held
    // press collapses to a tap and never reaches app KeyEvents nor an AccessibilityService key filter
    // (both verified on device). Only system apps reading /dev/input directly can use the hold, which a
    // sideloaded app cannot. We consume the key on down and emit the page turn on up.
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> true // consume; emit on key-up
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> {
                com.komgareader.app.eink.volumeButtonEvent(keyCode)?.let { buttonBus.emit(it) }
                true
            }
            else -> super.onKeyUp(keyCode, event)
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
        ensureNotificationPermission()
        captureViewIntent(intent)
        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val themeModeStr by settingsViewModel.themeMode.collectAsState()
            val languageStr by settingsViewModel.language.collectAsState()
            val displayModeStr by settingsViewModel.displayMode.collectAsState()
            val activeColorProfile by settingsViewModel.activeColorProfile.collectAsState()

            val themeMode = runCatching { ThemeMode.valueOf(themeModeStr) }.getOrDefault(ThemeMode.SYSTEM)
            val installedLanguages by settingsViewModel.availableLanguages.collectAsState()

            // Externer UI-Pack (L2): aktives Paket auflösen. Icons (prozess-global) + Theme-Token
            // werden hier zentral angewandt (wie resolveStrings); Shell-Override macht HomeScreen selbst.
            val activeUiPackId by settingsViewModel.activeUiPack.collectAsState()
            val uiPacks by settingsViewModel.availableUiPacks.collectAsState()
            val activeUiPack = remember(activeUiPackId, uiPacks) {
                uiPacks.firstOrNull { it.packageName == activeUiPackId }
            }
            // ActiveIconPack ist prozess-global (I1) und NICHT recompose-reaktiv: beim App-Start mit
            // persistiertem Pack greift es, bevor die Screens komponieren; ein LIVE gewechselter Pack
            // greift erst, wenn die icon-lesenden Screens neu komponieren (Tab-Wechsel) bzw. nach Neustart.
            LaunchedEffect(activeUiPack) {
                ActiveIconPack.current = activeUiPack?.toIconPack() ?: DefaultIconPack
            }
            val tokenOverride = remember(activeUiPack) { activeUiPack?.tokenOverride() }
            val externalPack = remember(activeUiPack) { activeUiPack?.toUiPackOrNull() }
            // remember: LocalStrings ist ein staticCompositionLocalOf (kein Gleichheits-Check) — ohne
            // remember alloziert resolveStrings bei jeder Recomposition (z.B. Color-Profile-Wechsel) eine
            // neue MapBackedStrings-Instanz und recomposed bei aktivem Sprach-Plugin den ganzen Baum.
            val activeStrings = remember(languageStr, installedLanguages) {
                resolveStrings(languageStr, installedLanguages)
            }
            // Geräteklasse auf zwei orthogonalen Achsen ableiten (Bewegung ⟂ Akzentfarbe):
            // Bewegung folgt der User-Wahl, Akzentfarbe der Hardware (Kaleido). LocalEinkMode
            // bleibt die abgeleitete Brücke (!allowsMotion) für bestehende Animations-Gates.
            val displayMode = runCatching { DisplayMode.valueOf(displayModeStr) }.getOrDefault(DisplayMode.EINK)
            val displayBehavior = displayBehaviorFor(displayMode, einkController.capabilities)

            CompositionLocalProvider(
                LocalStrings provides activeStrings,
                LocalDisplayBehavior provides displayBehavior,
                LocalEinkMode provides !displayBehavior.allowsMotion,
                LocalImageFilter provides activeColorProfile.toColorFilterOrNull(),
                LocalColorProfile provides activeColorProfile,
            ) {
                // Aurora-Card-Kacheln nur im Smartphone-Modus (LCD); E-Ink behält die Default-Kachel.
                val slotPack = remember(displayMode) {
                    if (displayMode == DisplayMode.SMARTPHONE) {
                        UiSlotPack(tiles = { s, m -> AuroraSeriesTile(s, m) })
                    } else {
                        UiSlotPack()
                    }
                }
                KomgaReaderTheme(themeMode = themeMode, slotPack = slotPack, tokenOverride = tokenOverride, externalPack = externalPack) {
                    val nav = rememberNavController()
                    LaunchedEffect(Unit) { syncCoordinator.onAppStart() }
                    // „Zur Bibliothek" app-weit verfügbar (Home-Button im Detail-Header u. a.):
                    // räumt bis zur Graph-Wurzel ab, sodass Home wieder oben liegt statt sich zu stapeln.
                    val onHome: () -> Unit = {
                        nav.navigate("home") {
                            popUpTo(nav.graph.startDestinationId) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                    CompositionLocalProvider(LocalOnHome provides onHome) {
                    NavHost(navController = nav, startDestination = "home") {
                        composable("home") {
                            HomeScreen(
                                onOpenSeries = { seriesId, sourceId -> nav.navigate("series/$seriesId/$sourceId") },
                                onOpenGroup = { shelfId, _ -> nav.navigate("group/$shelfId") },
                            )
                        }
                        composable(
                            route = "series/{seriesId}/{sourceId}",
                            arguments = listOf(
                                navArgument("seriesId") { type = NavType.StringType },
                                navArgument("sourceId") { type = NavType.LongType },
                            ),
                        ) {
                            SeriesDetailScreen(
                                onBack = { nav.popBackStack() },
                                onOpenBook = { bookId, sourceId, pageCount, format, forceStream, viewerMode ->
                                    nav.navigate("reader/$bookId/$sourceId/$pageCount/$format/$forceStream/$viewerMode")
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
                                onOpenSeries = { seriesId, sourceId ->
                                    nav.navigate("series_vm/$seriesId/$shelfId/$sourceId")
                                },
                            )
                        }
                        composable(
                            route = "series_vm/{seriesId}/{shelfId}/{sourceId}",
                            arguments = listOf(
                                navArgument("seriesId") { type = NavType.StringType },
                                navArgument("shelfId") { type = NavType.LongType },
                                navArgument("sourceId") { type = NavType.LongType },
                            ),
                        ) {
                            SeriesDetailScreen(
                                onBack = { nav.popBackStack() },
                                onOpenBook = { bookId, sourceId, pageCount, format, forceStream, viewerMode ->
                                    nav.navigate("reader/$bookId/$sourceId/$pageCount/$format/$forceStream/$viewerMode")
                                },
                            )
                        }
                        composable(
                            route = "reader/{bookId}/{sourceId}/{pageCount}/{format}/{stream}",
                            arguments = listOf(
                                navArgument("bookId") { type = NavType.StringType },
                                navArgument("sourceId") { type = NavType.LongType },
                                navArgument("pageCount") { type = NavType.IntType },
                                navArgument("format") { type = NavType.StringType },
                                navArgument("stream") { type = NavType.BoolType; defaultValue = false },
                            ),
                        ) {
                            ReaderRoute(
                                onBack = { nav.popBackStack() },
                                // Zur Bibliothek: den Reader-Stack bis 'home' (inklusiv-false) abräumen,
                                // sodass die Bibliothek wieder oben liegt statt sich zu stapeln.
                                onHome = {
                                    nav.navigate("home") {
                                        // Auf die Graph-Wurzel räumen statt auf den String "home":
                                        // robust, falls der Reader je ohne home darunter erreicht wird
                                        // (Deep-Link/Notification) — popUpTo("home") wäre dann ein No-Op.
                                        popUpTo(nav.graph.startDestinationId) { inclusive = false }
                                        launchSingleTop = true
                                    }
                                },
                                // Einstellungen: ÜBER dem aktuellen Reader pushen — der Back-Stack hält
                                // den Reader darunter, sodass SettingsRoute.onBack genau dorthin zurückführt.
                                onSettings = { nav.navigate("settings") },
                            )
                        }
                        composable(
                            route = "reader/{bookId}/{sourceId}/{pageCount}/{format}/{stream}/{viewerMode}",
                            arguments = listOf(
                                navArgument("bookId") { type = NavType.StringType },
                                navArgument("sourceId") { type = NavType.LongType },
                                navArgument("pageCount") { type = NavType.IntType },
                                navArgument("format") { type = NavType.StringType },
                                navArgument("stream") { type = NavType.BoolType; defaultValue = false },
                                navArgument("viewerMode") { type = NavType.StringType; defaultValue = "PAGED" },
                            ),
                        ) {
                            ReaderRoute(
                                onBack = { nav.popBackStack() },
                                // Zur Bibliothek: den Reader-Stack bis 'home' (inklusiv-false) abräumen,
                                // sodass die Bibliothek wieder oben liegt statt sich zu stapeln.
                                onHome = {
                                    nav.navigate("home") {
                                        // Auf die Graph-Wurzel räumen statt auf den String "home":
                                        // robust, falls der Reader je ohne home darunter erreicht wird
                                        // (Deep-Link/Notification) — popUpTo("home") wäre dann ein No-Op.
                                        popUpTo(nav.graph.startDestinationId) { inclusive = false }
                                        launchSingleTop = true
                                    }
                                },
                                // Einstellungen: ÜBER dem aktuellen Reader pushen — der Back-Stack hält
                                // den Reader darunter, sodass SettingsRoute.onBack genau dorthin zurückführt.
                                onSettings = { nav.navigate("settings") },
                            )
                        }
                        // Settings als volle Seite über dem Reader (DRY: derselbe SettingsScreen wie der
                        // Bibliotheks-Tab). Über den Reader gepusht → Zurück landet im selben Reader.
                        composable("settings") {
                            SettingsRoute(onBack = { nav.popBackStack() })
                        }
                    }

                    // --- Exit confirmation ------------------------------------------------------
                    // At the library root, the system Back gesture would finish the activity. Catch
                    // it and confirm via the shared EinkModal before leaving the app.
                    val currentRoute = nav.currentBackStackEntryAsState().value?.destination?.route
                    var showExit by remember { mutableStateOf(false) }
                    BackHandler(enabled = currentRoute == "home") { showExit = true }
                    if (showExit) {
                        val exitStrings = LocalStrings.current
                        EinkConfirmDialog(
                            title = exitStrings.exitConfirmTitle,
                            confirmLabel = exitStrings.exitConfirmLeave,
                            onConfirm = {
                                showExit = false
                                this@MainActivity.finish()
                            },
                            dismissLabel = exitStrings.cancel,
                            onDismiss = { showExit = false },
                        )
                    }

                    // --- External book open (VIEW intent) driver -------------------------------
                    // Captured by captureViewIntent; here we prepare the ephemeral reader route,
                    // navigate, and then honour the configured behaviour (read-only / import /
                    // ask). The prompt is the shared EinkModal.
                    val externalBehavior by settingsViewModel.externalOpenBehavior.collectAsState()
                    var promptTarget by remember { mutableStateOf<ExternalOpenTarget?>(null) }
                    var promptUri by remember { mutableStateOf<Uri?>(null) }
                    val externalScope = rememberCoroutineScope()
                    val externalStrings = LocalStrings.current

                    // Fallback when no folder is configured yet: let the user pick one, then import.
                    // The picked tree becomes both the download and local folder (setBothFolders).
                    val importPicker = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.OpenDocumentTree(),
                    ) { tree: Uri? ->
                        val uri = promptUri
                        if (tree != null && uri != null) {
                            contentResolver.takePersistableUriPermission(
                                tree,
                                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                            )
                            settingsViewModel.setBothFolders(
                                tree.lastPathSegment?.substringAfterLast('/') ?: "Ordner", tree.toString(),
                            )
                            val fileName = promptTarget?.fileName ?: "buch"
                            promptTarget = null
                            promptUri = null
                            externalScope.launch {
                                externalOpener.importToFolder(uri, tree, fileName)
                                syncCoordinator.onManualReload()
                            }
                        }
                    }

                    LaunchedEffect(pendingExternalUri.value) {
                        val uri = pendingExternalUri.value ?: return@LaunchedEffect
                        pendingExternalUri.value = null
                        val target = externalOpener.prepareEphemeral(uri)
                        if (target == null) return@LaunchedEffect // unsupported file: silently ignore
                        nav.navigate(target.route)
                        when (runCatching { ExternalOpenBehavior.valueOf(externalBehavior) }
                            .getOrDefault(ExternalOpenBehavior.ASK)) {
                            ExternalOpenBehavior.READ_ONLY -> {}
                            ExternalOpenBehavior.IMPORT -> {
                                val folder = externalOpener.configuredFolder()
                                if (folder != null) {
                                    externalOpener.importToFolder(uri, folder, target.fileName)
                                    syncCoordinator.onManualReload()
                                } else {
                                    promptUri = uri
                                    promptTarget = target
                                }
                            }
                            ExternalOpenBehavior.ASK -> {
                                promptUri = uri
                                promptTarget = target
                            }
                        }
                    }

                    promptTarget?.let { target ->
                        var rememberChoice by remember(target) { mutableStateOf(false) }
                        EinkModal(
                            title = externalStrings.externalOpenTitle,
                            onDismiss = {
                                if (rememberChoice) {
                                    settingsViewModel.setExternalOpenBehavior(ExternalOpenBehavior.READ_ONLY.name)
                                }
                                promptTarget = null
                                promptUri = null
                            },
                            onConfirm = {
                                if (rememberChoice) {
                                    settingsViewModel.setExternalOpenBehavior(ExternalOpenBehavior.IMPORT.name)
                                }
                                val uri = promptUri
                                val fileName = target.fileName
                                promptTarget = null
                                if (uri != null) externalScope.launch {
                                    val folder = externalOpener.configuredFolder()
                                    if (folder != null) {
                                        externalOpener.importToFolder(uri, folder, fileName)
                                        syncCoordinator.onManualReload()
                                        promptUri = null
                                    } else {
                                        // No folder yet: keep promptUri for the picker callback.
                                        importPicker.launch(null)
                                    }
                                }
                            },
                            confirmLabel = externalStrings.externalOpenImport,
                            dismissLabel = externalStrings.externalOpenReadOnly,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = rememberChoice, onCheckedChange = { rememberChoice = it })
                                Text(externalStrings.externalOpenRemember)
                            }
                        }
                    }
                    }
                }
            }
        }
    }
}
