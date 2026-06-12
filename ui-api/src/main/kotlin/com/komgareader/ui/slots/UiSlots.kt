package com.komgareader.ui.slots

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * # UI-Slot-Naht (Layout-Ebene der modularen UI) — gebaute Regionen: Header, HomeHeader, Dialog, Settings, Tiles, Overlay, Detail, ReaderChrome
 *
 * Die [com.komgareader.ui.theme.UiPack]-Naht macht den **Look** auswechselbar (Farbe, Typo,
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
 * Und die achte Region **readerChrome**: das **ganze Reader-Gerüst** (`ReaderScaffold` —
 * Vollbild-Hintergrund, Tap-Zonen, Hints, Status-Fuß, persistentBars, Start-Hinweis und der schon
 * slot-ifizierte `overlay`). Die Reader-**Engines** + die `Viewer`-Naht (Naht B) bleiben **Core,
 * draußen** — ein readerChrome-Pack berührt weder Refresh-Scheduler noch Engine-Navigation.
 * Die ursprünglich genannte Region **nav** ist **kein** Region-Slot: das Navigations-Skelett
 * (Bottom-Bar/Drawer/Side-Rail) gehört dem **Shell-Pack** (`AppShellState`/`DeclarativeShell`
 * + `ShellDescriptor`, Form-Faktor-Naht, siehe `architecture-seams.md`), eine Ebene **über** den
 * Region-Slots — nicht als offener Region-Slot zu behaupten, was dort gelöst ist.
 *
 * ## Stand: sechs Chrome-Regionen + die detail- + readerChrome-Region gebaut, als `:ui-api`-Modul-Vertrag
 *
 * Gebaut sind die sechs Chrome-Regionen — **header** ([HeaderState]-Surface über der
 * zentralisierten `StandardTopAppBar`, mit **optionaler** Such-Capability [HeaderSearch]:
 * Titel↔Suchfeld; suchlose Aufrufer bleiben über den Kompat-Pfad [ResolvedSlots.header] unverändert),
 * **homeHeader** ([HomeHeaderState]-Surface), **dialog** ([DialogState]-Surface, der eine
 * Onyx-Dialog-Rahmen hinter [DialogSlot]), **settings** ([SettingsState]-Surface, das
 * Settings-Skelett — Sidebar-Master-Detail vs. Accordion — hinter [SettingsSlot]), **tiles**
 * ([TileState]-Surface, die Serien-Kachel hinter [TilesSlot], in Bibliothek + Gruppen) und
 * **overlay** ([ReaderOverlayState]-Surface, die toggle­bare Reader-Chrome-Menüleiste hinter
 * [OverlaySlot]) — sowie **detail** ([DetailScaffoldState]-Surface, das Vollbild-Detail-Gerüst
 * hinter [DetailSlot]) und **readerChrome** ([ReaderScaffoldState]-Surface, das ganze Reader-Gerüst
 * hinter [ReaderChromeSlot]). Die Verträge + entkoppelten Surfaces leben seit A1 im Modul `:ui-api`;
 * die **gekoppelten Default-Renderer** (Onyx-Look, an app-i18n/-Komponenten) bleiben in `:app`. Der
 * Vertrag ist **noch nicht eingefroren** (kein ABI-Gate — kommt mit dem Pack-Lader L1/L2).
 */

/**
 * Optionale Such-Fähigkeit eines Headers: Lupe-Toggle ([onOpen]/[onClose]) + Suchfeld-Zustand.
 * Ist [active] gesetzt, zeigt der Header das Suchfeld statt des Titels; sonst eine Lupe vor den
 * [HeaderState.actions]. Vorbild ist die [HomeHeaderSearch]-Capability der homeHeader-Region —
 * der Host besitzt die Logik, der Slot arrangiert nur.
 */
data class HeaderSearch(
    val active: Boolean,
    val query: String,
    val onQueryChange: (String) -> Unit,
    val onOpen: () -> Unit,
    val onClose: () -> Unit,
    val placeholder: String? = null,
    /** Beschreibt die **Aktion** der Lupe (a11y), nicht das Feld. Fällt sonst auf `searchAction` zurück. */
    val actionLabel: String? = null,
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
 * `StandardTopAppBar`-Aufruf. Ein alternatives Pack liefert einen anderen Header mit **derselben**
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
 * Vertrag der ReaderChrome-Region: das **ganze Reader-Gerüst** (`ReaderScaffold`). Ein Pack
 * arrangiert daraus die Lese-Oberfläche aus den host-gebauten Stücken der [ReaderScaffoldState]-
 * Surface (Vollbild-Hintergrund · Tap-Zonen · Hints · Chrome-Menüleiste über die `overlay`-Region ·
 * Status-Fuß · persistentBars · Start-Hinweis · der eigentliche Inhalt) — dieselbe Aufrufform,
 * andere Anordnung (z. B. Footer oben, anderes Gerüst). Die Reader-**Engines** + die `Viewer`-Naht
 * (Naht B: Refresh-Scheduler, Engine-Navigation) bleiben **Core und außerhalb der Surface** — die
 * Surface trägt nur die abgeleiteten `chromeVisible`/`onToggleChrome`. Der E-Ink-Scrim + die
 * Animation-Gating-Pfade bleiben host-erzwungen, nicht Sache des Packs.
 */
typealias ReaderChromeSlot = @Composable (state: ReaderScaffoldState) -> Unit

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
    val readerChrome: ReaderChromeSlot? = null,
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
    val readerChrome: ReaderChromeSlot,
)

/**
 * Auflöser der Slot-Naht: pro Region „Pack-Slot **oder** Default". **Pure Funktion** über nullbare
 * Referenzen → unit-testbar ohne Compose-Laufzeit (siehe `SlotFallbackTest`). Reicht die Referenz
 * unverändert durch (keine Wrapper), damit referenzielle Identität trägt.
 */
object UiSlots {
    /**
     * Pure: pro Region „Pack-Slot oder Default". [defaults] liefert der Host (app-`DefaultSlots`),
     * weil der Onyx-Look an app-i18n/-Komponenten koppelt und daher nicht in `:ui-api` liegt.
     */
    fun resolve(pack: UiSlotPack, defaults: ResolvedSlots): ResolvedSlots = ResolvedSlots(
        headerSlot = pack.header ?: defaults.headerSlot,
        homeHeader = pack.homeHeader ?: defaults.homeHeader,
        dialog = pack.dialog ?: defaults.dialog,
        settings = pack.settings ?: defaults.settings,
        tiles = pack.tiles ?: defaults.tiles,
        overlay = pack.overlay ?: defaults.overlay,
        detail = pack.detail ?: defaults.detail,
        readerChrome = pack.readerChrome ?: defaults.readerChrome,
    )
}

/**
 * App-weit bereitgestellte, **aufgelöste** Slots. **Error-Default**: der Host
 * ([com.komgareader.app.ui.theme.KomgaReaderTheme]) stellt das aktive, aufgelöste Pack immer bereit
 * (er kennt die app-`DefaultSlots`, die hier nicht liegen). Consumer lesen
 * `LocalResolvedSlots.current.header(...)` statt eine Top-Bar direkt aufzurufen.
 */
val LocalResolvedSlots = staticCompositionLocalOf<ResolvedSlots> {
    error("Kein ResolvedSlots bereitgestellt — in KomgaReaderTheme wrappen.")
}

/**
 * Kompat-Form für Header **ohne** Suche: baut die [HeaderState]-Surface und ruft den
 * [ResolvedSlots.headerSlot]. Hält die bestehenden `current.header(title, onBack){ actions }`-Aufrufer
 * (SubPageScaffold, SettingsRoute, DetailScaffold, Preview) **unverändert**. Der such-fähige Pfad ruft
 * `headerSlot(HeaderState(…, search = …))` direkt (siehe `DefaultDetailScaffold` / `CollectionDetailScreen`).
 */
@Composable
fun ResolvedSlots.header(
    title: String,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit = {},
) = headerSlot(HeaderState(title, onBack, actions))
