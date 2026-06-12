package com.komgareader.app.ui.slots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.ui.components.DefaultDialog
import com.komgareader.app.ui.components.DefaultSeriesTile
import com.komgareader.app.ui.components.DialogState
import com.komgareader.app.ui.components.EinkSearchBar
import com.komgareader.app.ui.components.StandardTopAppBar
import com.komgareader.app.ui.components.TileState
import com.komgareader.app.ui.detail.DefaultDetailScaffold
import com.komgareader.app.ui.detail.DetailScaffoldState
import com.komgareader.app.ui.home.DefaultHomeHeader
import com.komgareader.app.ui.home.HomeHeaderState
import com.komgareader.app.ui.icons.AppIcons
import com.komgareader.app.ui.reader.DefaultReaderOverlay
import com.komgareader.app.ui.reader.ReaderOverlayState
import com.komgareader.app.ui.settings.DefaultSettings
import com.komgareader.app.ui.settings.SettingsState

/**
 * # UI-Slot-Naht (Layout-Ebene der modularen UI) — gebaute Regionen: Header, HomeHeader, Dialog, Settings, Tiles, Overlay, Detail
 *
 * Die [com.komgareader.app.ui.theme.UiPack]-Naht macht den **Look** auswechselbar (Farbe, Typo,
 * Token — „Theme zuerst"). Diese Naht ist das **„Layout danach"** aus `big-picture-and-goals.md`
 * (ui-modularity): adressierbare, **einzeln ersetzbare Chrome-Regionen**. Ein „UI-Pack" füllt eine,
 * mehrere oder alle Regionen; fehlt eine, fällt sie sauber auf das Default-Pack zurück (analog
 * `StubSource` bei den Quellen). Der **Host** rendert und **erzwingt die E-Ink-Invarianten**
 * (Bewegung/Akzent über `LocalDisplayBehavior`/`LocalDesignTokens`/`LocalEinkMode`) — ein Slot
 * liefert **Inhalt/Struktur, nie die Bewegungs-/Farb-Policy**.
 *
 * ## Vollständige (konzeptionelle) Slot-Liste
 *
 * Die sechs Chrome-Regionen, die je einzeln auswechselbar sind: **header · homeHeader · overlay ·
 * tiles · settings · dialog**. Dazu die siebte Region **detail**: das **Vollbild-Detail-Gerüst**
 * (Scaffold + Header über den `header`-Slot + optionaler Snackbar + Body) — keine eigene Chrome-
 * Region, sondern das geteilte Gerüst, das die Detail-Routen über den `header`-Slot **komponiert**.
 * Die ursprünglich genannte Region **nav** ist **kein** Region-Slot: das Navigations-Skelett
 * (Bottom-Bar/Drawer/Side-Rail) gehört dem **Shell-Pack** (`AppShellState`/`DefaultShell`/
 * `PhoneShell`, Form-Faktor-Naht, siehe `architecture-seams.md`), eine Ebene **über** den
 * Region-Slots — nicht als offener Region-Slot zu behaupten, was dort gelöst ist.
 *
 * ## Stand: sechs Chrome-Regionen + die detail-Region gebaut
 *
 * Gebaut sind die sechs Chrome-Regionen — **header** ([HeaderState]-Surface über der
 * zentralisierten [StandardTopAppBar], mit **optionaler** Such-Capability [HeaderSearch]:
 * Titel↔Suchfeld; suchlose Aufrufer bleiben über den Kompat-Pfad [ResolvedSlots.header] unverändert),
 * **homeHeader** ([HomeHeaderState]-Surface), **dialog** ([DialogState]-Surface, der eine
 * Onyx-Dialog-Rahmen hinter [DialogSlot]), **settings** ([SettingsState]-Surface, das
 * Settings-Skelett — Sidebar-Master-Detail vs. Accordion — hinter [SettingsSlot]), **tiles**
 * ([TileState]-Surface, die Serien-Kachel hinter [TilesSlot], in Bibliothek + Gruppen) und
 * **overlay** ([ReaderOverlayState]-Surface, die toggle­bare Reader-Chrome-Menüleiste hinter
 * [OverlaySlot]) — sowie **detail** ([DetailScaffoldState]-Surface, das Vollbild-Detail-Gerüst
 * hinter [DetailSlot], in SeriesDetail + GroupBrowse). Bewusst je *eine* Region end-to-end statt
 * aller auf einmal: jede war zuvor an genau **einer** Stelle zentralisiert
 * (`shared-structure-before-variants.md`: erst zentralisieren, dann hinter die Naht). Die
 * `ui-api`-Modul-Extraktion und ein APK-Pack-Lader bleiben Soll (Skins-Plan P2/P3). Der Vertrag ist
 * **in-tree und nicht eingefroren**.
 */

/**
 * Optionale Such-Fähigkeit eines Headers: Lupe-Toggle ([onOpen]/[onClose]) + Suchfeld-Zustand.
 * Ist [active] gesetzt, zeigt der Header das Suchfeld statt des Titels; sonst eine Lupe vor den
 * [HeaderState.actions]. Vorbild ist die `HomeHeaderSearch`-Capability der homeHeader-Region —
 * der Host besitzt die Logik, der Slot arrangiert nur.
 */
data class HeaderSearch(
    val active: Boolean,
    val query: String,
    val onQueryChange: (String) -> Unit,
    val onOpen: () -> Unit,
    val onClose: () -> Unit,
    val placeholder: String? = null,
)

/**
 * Capability-Surface der Header-Region: Titel + optionaler Zurück + Aktionen + optionale Suche
 * ([search]). Ein Pack rendert daraus die Top-Leiste. Der Look/die E-Ink-Invarianten bleiben
 * host-erzwungen — der Slot liefert nur Inhalt/Struktur.
 */
data class HeaderState(
    val title: String,
    val onBack: (() -> Unit)?,
    val actions: @Composable RowScope.() -> Unit = {},
    val search: HeaderSearch? = null,
)

/**
 * Vertrag der Header-Region. Trägt eine [HeaderState]-Surface (Titel · optionaler Zurück-Pfeil ·
 * rechte Aktions-Icons · optionale [HeaderSearch]) — kein Funktionsverlust gegenüber dem direkten
 * [StandardTopAppBar]-Aufruf. Ein alternatives Pack liefert einen anderen Header mit **derselben**
 * Aufrufform. Die bestehenden suchlosen Aufrufer bleiben über den **Kompat-Pfad**
 * [ResolvedSlots.header] (Extension) unverändert.
 */
typealias HeaderSlot = @Composable (state: HeaderState) -> Unit

/**
 * Vertrag der Home-Header-Region. Breiter als [HeaderSlot]: der Home-Header trägt Status-Cluster,
 * Suchfeld, generischen Filter-Slot und Tab-spezifische Aktionen — alles in [HomeHeaderState]
 * gekapselt. Ein Pack arrangiert diese Fähigkeiten; der Host (Core) baut die Surface und besitzt
 * die Logik („UI neu, Kernlogik gleich"). E-Ink-Invarianten bleiben host-erzwungen.
 */
typealias HomeHeaderSlot = @Composable (state: HomeHeaderState) -> Unit

/**
 * Vertrag der Dialog-Region. Ein Pack rendert den Onyx-Dialog-Rahmen aus der [DialogState]-Surface
 * (Titel · optionale Header-Aktion · scrollender Body · Abbrechen/Bestätigen) — dieselbe Aufrufform,
 * anderer Look. Die E-Ink-Invarianten (keine Animation) bleiben host-erzwungen, nicht Sache des Packs.
 */
typealias DialogSlot = @Composable (state: DialogState) -> Unit

/**
 * Vertrag der Settings-Region. Ein Pack ordnet die host-gebauten Sektionen aus der [SettingsState]-
 * Surface an (Sidebar-Master-Detail, Accordion, flache Liste …) und besitzt den Navigations-State
 * selbst — dieselbe Aufrufform, anderes Skelett. Die Sektions-Inhalte sind host-gebaut, das Pack
 * platziert sie nur. Die E-Ink-Invarianten (gegatete Animation) bleiben host-erzwungen.
 */
typealias SettingsSlot = @Composable (state: SettingsState) -> Unit

/**
 * Vertrag der Tiles-Region. Ein Pack rendert aus der [TileState]-Surface (Werk + Lokal-Status +
 * Navigations-Callbacks) eine **einzelne** Serien-Kachel — dieselbe Aufrufform, anderer Kachel-Look
 * (Badge, Titel-Stil, Rahmen). Das Grid/die Spaltenzahl bleibt Screen-Eigentum; der Slot tauscht nur
 * die Kachel. Der `modifier` trägt den Grid-Item-Layout-Modifier des Aufrufers (anders als
 * dialog/settings, die ganzflächig sind). Das Cover-Laden + der E-Ink-Filter bleiben host-erzwungen.
 */
typealias TilesSlot = @Composable (state: TileState, modifier: Modifier) -> Unit

/**
 * Vertrag der Overlay-Region: die toggle­bare Reader-Chrome-Menüleiste. **`BoxScope`-Extension**,
 * weil die Leiste sich im Reader-`Box` per `Modifier.align(Alignment.TopCenter)` positioniert (anders
 * als die übrigen Slots). Ein Pack rendert die Leiste aus der [ReaderOverlayState]-Surface (Titel ·
 * Navigations-/Shortcut-Callbacks · reader-spezifische Aktionen) — dieselbe Aufrufform, andere
 * Anordnung (z. B. Burger-Menü, Shortcuts links). Die Sichtbarkeit (chromeVisible) + der E-Ink-Scrim
 * bleiben **host-erzwungen** (das `ReaderScaffold` rendert nur bei `chromeVisible`), nicht Sache des Packs.
 */
typealias OverlaySlot = @Composable BoxScope.(state: ReaderOverlayState) -> Unit

/**
 * Vertrag der Detail-Region: das **Vollbild-Detail-Gerüst**. Ein Pack rahmt die host-gebauten Stücke
 * der [DetailScaffoldState]-Surface (Titel + Zurück + Header-Aktionen → header-Slot · optionaler
 * Snackbar-Host · padding-durchgereichter Body) — dieselbe Aufrufform, andere Rahmung (z. B. später
 * Master-Detail auf Tablet). Den Body baut der Host, das Pack platziert ihn nur. Der Header-Look + die
 * E-Ink-Invarianten bleiben über den header-Slot bzw. den Host erzwungen, nicht Sache des Packs.
 */
typealias DetailSlot = @Composable (state: DetailScaffoldState) -> Unit

/**
 * Ein Slot-Pack: pro Region ein optionaler Slot. `null` = diese Region nicht überschreiben → Default.
 * Künftige Regionen kommen als weitere **optionale** Felder hinzu, ohne bestehende Packs zu brechen
 * (additiv).
 */
data class UiSlotPack(
    val header: HeaderSlot? = null,
    val homeHeader: HomeHeaderSlot? = null,
    val dialog: DialogSlot? = null,
    val settings: SettingsSlot? = null,
    val tiles: TilesSlot? = null,
    val overlay: OverlaySlot? = null,
    val detail: DetailSlot? = null,
)

/** Aufgelöste Slots: jede Region garantiert non-null (Default eingesetzt, wo das Pack nichts liefert). */
data class ResolvedSlots(
    /** Header-Renderer. Bewusst `headerSlot` (nicht `header`) — sonst kollidiert die Property mit der
     *  Kompat-Extension [header], über die die suchlosen Aufrufer verbatim weiterlaufen. */
    val headerSlot: HeaderSlot,
    val homeHeader: HomeHeaderSlot,
    val dialog: DialogSlot,
    val settings: SettingsSlot,
    val tiles: TilesSlot,
    val overlay: OverlaySlot,
    val detail: DetailSlot,
)

/**
 * Auflöser der Slot-Naht: pro Region „Pack-Slot **oder** Default". **Pure Funktion** über nullbare
 * Referenzen → unit-testbar ohne Compose-Laufzeit (siehe `SlotFallbackTest`). Reicht die Referenz
 * unverändert durch (keine Wrapper), damit referenzielle Identität trägt.
 */
object UiSlots {
    fun resolve(pack: UiSlotPack): ResolvedSlots = ResolvedSlots(
        headerSlot = pack.header ?: DefaultSlots.header,
        homeHeader = pack.homeHeader ?: DefaultSlots.homeHeader,
        dialog = pack.dialog ?: DefaultSlots.dialog,
        settings = pack.settings ?: DefaultSlots.settings,
        tiles = pack.tiles ?: DefaultSlots.tiles,
        overlay = pack.overlay ?: DefaultSlots.overlay,
        detail = pack.detail ?: DefaultSlots.detail,
    )
}

/**
 * Das mitgelieferte Default-Pack — der heutige Onyx-Look, verhaltensgleich zu den bisherigen
 * direkten Aufrufen. Fehlt einem Community-Pack ein Slot, greift dieser Wert.
 */
object DefaultSlots {
    val header: HeaderSlot = { state ->
        DefaultHeader(state)
    }
    val homeHeader: HomeHeaderSlot = { state ->
        DefaultHomeHeader(state)
    }
    val dialog: DialogSlot = { state ->
        DefaultDialog(state)
    }
    val settings: SettingsSlot = { state ->
        DefaultSettings(state)
    }
    val tiles: TilesSlot = { state, modifier ->
        DefaultSeriesTile(state, modifier)
    }
    val overlay: OverlaySlot = { state ->
        // Lambda-Receiver ist BoxScope → ruft die BoxScope-Extension mit implizitem Receiver auf.
        DefaultReaderOverlay(state)
    }
    val detail: DetailSlot = { state ->
        DefaultDetailScaffold(state)
    }
}

/**
 * App-weit bereitgestellte, **aufgelöste** Slots. Default = das mitgelieferte Pack. Der Host
 * ([com.komgareader.app.ui.theme.KomgaReaderTheme]) stellt hier das aktive Pack bereit — analog
 * [com.komgareader.app.ui.theme.LocalUiPack]. Consumer lesen `LocalResolvedSlots.current.header(...)`
 * statt [StandardTopAppBar] direkt aufzurufen.
 */
val LocalResolvedSlots = staticCompositionLocalOf { UiSlots.resolve(UiSlotPack()) }

/**
 * Kompat-Form für Header **ohne** Suche: baut die [HeaderState]-Surface und ruft den
 * [ResolvedSlots.headerSlot]. Hält die bestehenden `current.header(title, onBack){ actions }`-Aufrufer
 * (SubPageScaffold, SettingsRoute, DetailScaffold, Preview) **unverändert**. Der such-fähige Pfad ruft
 * `headerSlot(HeaderState(…, search = …))` direkt (siehe [DefaultDetailScaffold] / `CollectionDetailScreen`).
 */
@Composable
fun ResolvedSlots.header(
    title: String,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit = {},
) = headerSlot(HeaderState(title, onBack, actions))

/**
 * Der mitgelieferte such-fähige Onyx-Renderer der Header-Region. Ohne aktive Suche eine
 * [StandardTopAppBar] (bei vorhandener [HeaderSearch] mit vorgelagerter Lupe); bei `search.active`
 * tritt — wie zuvor im Sammlungs-Detail — ein zentriertes Suchfeld an die Stelle des Titels (Back
 * links, Aktionen rechts). Verhaltensgleich zum früheren `CollectionDetailHeader`. E-Ink-Invarianten
 * bleiben host-erzwungen (kein Fade, instant).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefaultHeader(state: HeaderState) {
    val search = state.search
    if (search != null && search.active) {
        val strings = LocalStrings.current
        TopAppBar(
            title = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (state.onBack != null) {
                        IconButton(onClick = state.onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                            Icon(AppIcons.Back, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    val focus = remember { FocusRequester() }
                    // Erst schließen, wenn das Feld den Fokus WIEDER verliert (nicht beim initialen
                    // Nicht-Fokus, der sonst sofort schließt) und leer ist.
                    var wasFocused by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { focus.requestFocus() }
                    EinkSearchBar(
                        query = search.query,
                        onQueryChange = search.onQueryChange,
                        // Action-Icon der Bar (immer sichtbar) = X → schließt die Suche zurück zum Titel.
                        onSubmit = search.onClose,
                        actionIcon = AppIcons.Close,
                        actionLabel = strings.clearSearch,
                        placeholder = search.placeholder ?: state.title,
                        modifier = Modifier
                            .fillMaxWidth(0.74f)
                            .focusRequester(focus)
                            .onFocusChanged {
                                if (it.isFocused) wasFocused = true
                                else if (wasFocused && search.query.isBlank()) search.onClose()
                            },
                    )
                }
            },
        )
    } else {
        StandardTopAppBar(
            title = state.title,
            onBack = state.onBack,
            actions = {
                if (search != null) {
                    IconButton(onClick = search.onOpen) {
                        Icon(
                            AppIcons.Search,
                            contentDescription = search.placeholder ?: state.title,
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                state.actions(this)
            },
        )
    }
}
