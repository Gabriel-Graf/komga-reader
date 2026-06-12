package com.komgareader.app.ui.slots

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.komgareader.app.ui.components.DefaultDialog
import com.komgareader.app.ui.components.DialogState
import com.komgareader.app.ui.components.StandardTopAppBar
import com.komgareader.app.ui.home.DefaultHomeHeader
import com.komgareader.app.ui.home.HomeHeaderState

/**
 * # UI-Slot-Naht (Layout-Ebene der modularen UI) — gebaute Regionen: Header, HomeHeader, Dialog
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
 * Die Chrome-Regionen, die langfristig je einzeln auswechselbar werden sollen:
 * **header · homeHeader · overlay · tiles · nav · settings · dialog**.
 *
 * ## Stand: header + homeHeader + dialog gebaut
 *
 * Gebaut sind drei Regionen — **header** (zentralisierte [StandardTopAppBar]), **homeHeader**
 * ([HomeHeaderState]-Surface) und **dialog** ([DialogState]-Surface, der eine Onyx-Dialog-Rahmen
 * hinter [DialogSlot]). Bewusst je *eine* Region end-to-end statt aller auf einmal: jede war zuvor
 * an genau **einer** Stelle zentralisiert (`shared-structure-before-variants.md`: erst zentralisieren,
 * dann hinter die Naht). Die weiteren Slots (overlay/tiles/nav/settings), die `ui-api`-Modul-Extraktion
 * und ein APK-Pack-Lader bleiben Soll (Skins-Plan P2/P3). Der Vertrag ist **in-tree und nicht eingefroren**.
 */

/**
 * Vertrag der Header-Region. Spiegelt die **echte** Signatur von [StandardTopAppBar]
 * (Titel · optionaler Zurück-Pfeil · rechte Aktions-Icons) — kein Funktionsverlust gegenüber dem
 * direkten Aufruf. Ein alternatives Pack liefert einen anderen Header mit **derselben** Aufrufform.
 */
typealias HeaderSlot = @Composable (title: String, onBack: (() -> Unit)?, actions: @Composable RowScope.() -> Unit) -> Unit

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
 * Ein Slot-Pack: pro Region ein optionaler Slot. `null` = diese Region nicht überschreiben → Default.
 * Weitere Regionen (overlay/tiles/nav/settings) kommen später als weitere **optionale** Felder
 * hinzu, ohne bestehende Packs zu brechen (additiv).
 */
data class UiSlotPack(
    val header: HeaderSlot? = null,
    val homeHeader: HomeHeaderSlot? = null,
    val dialog: DialogSlot? = null,
)

/** Aufgelöste Slots: jede Region garantiert non-null (Default eingesetzt, wo das Pack nichts liefert). */
data class ResolvedSlots(val header: HeaderSlot, val homeHeader: HomeHeaderSlot, val dialog: DialogSlot)

/**
 * Auflöser der Slot-Naht: pro Region „Pack-Slot **oder** Default". **Pure Funktion** über nullbare
 * Referenzen → unit-testbar ohne Compose-Laufzeit (siehe `SlotFallbackTest`). Reicht die Referenz
 * unverändert durch (keine Wrapper), damit referenzielle Identität trägt.
 */
object UiSlots {
    fun resolve(pack: UiSlotPack): ResolvedSlots = ResolvedSlots(
        header = pack.header ?: DefaultSlots.header,
        homeHeader = pack.homeHeader ?: DefaultSlots.homeHeader,
        dialog = pack.dialog ?: DefaultSlots.dialog,
    )
}

/**
 * Das mitgelieferte Default-Pack — der heutige Onyx-Look, verhaltensgleich zu den bisherigen
 * direkten Aufrufen. Fehlt einem Community-Pack ein Slot, greift dieser Wert.
 */
object DefaultSlots {
    val header: HeaderSlot = { title, onBack, actions ->
        StandardTopAppBar(title = title, onBack = onBack, actions = actions)
    }
    val homeHeader: HomeHeaderSlot = { state ->
        DefaultHomeHeader(state)
    }
    val dialog: DialogSlot = { state ->
        DefaultDialog(state)
    }
}

/**
 * App-weit bereitgestellte, **aufgelöste** Slots. Default = das mitgelieferte Pack. Der Host
 * ([com.komgareader.app.ui.theme.KomgaReaderTheme]) stellt hier das aktive Pack bereit — analog
 * [com.komgareader.app.ui.theme.LocalUiPack]. Consumer lesen `LocalResolvedSlots.current.header(...)`
 * statt [StandardTopAppBar] direkt aufzurufen.
 */
val LocalResolvedSlots = staticCompositionLocalOf { UiSlots.resolve(UiSlotPack()) }
