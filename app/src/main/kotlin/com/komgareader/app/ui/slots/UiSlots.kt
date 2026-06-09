package com.komgareader.app.ui.slots

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.komgareader.app.ui.components.StandardTopAppBar

/**
 * # UI-Slot-Naht (Layout-Ebene der modularen UI) — erste Region: Header
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
 * **header · overlay · tiles · nav · settings · dialog**.
 *
 * ## YAGNI: heute nur `header`
 *
 * Gebaut ist **ausschließlich** der Header-Slot — bewusst end-to-end als *eine* Region, nicht die
 * sechs auf einmal. Warum der Header zuerst: er war über die Screens **dupliziert** (jede Seite ihre
 * eigene `TopAppBar`) und ist inzwischen in genau **einer** Stelle zentralisiert
 * ([StandardTopAppBar]) — damit ist er die **ideale erste auswechselbare Region**
 * (`shared-structure-before-variants.md`: erst zentralisieren, dann hinter die Naht). Die weiteren
 * fünf Slots, die `ui-api`-Modul-Extraktion und ein APK-Pack-Lader bleiben Soll (Skins-Plan P2/P3).
 * Der Vertrag ist **in-tree und nicht eingefroren**.
 */

/**
 * Vertrag der Header-Region. Spiegelt die **echte** Signatur von [StandardTopAppBar]
 * (Titel · optionaler Zurück-Pfeil · rechte Aktions-Icons) — kein Funktionsverlust gegenüber dem
 * direkten Aufruf. Ein alternatives Pack liefert einen anderen Header mit **derselben** Aufrufform.
 */
typealias HeaderSlot = @Composable (title: String, onBack: (() -> Unit)?, actions: @Composable RowScope.() -> Unit) -> Unit

/**
 * Ein Slot-Pack: pro Region ein optionaler Slot. `null` = diese Region nicht überschreiben → Default.
 * Weitere Regionen (overlay/tiles/nav/settings/dialog) kommen später als weitere **optionale** Felder
 * hinzu, ohne bestehende Packs zu brechen (additiv).
 */
data class UiSlotPack(val header: HeaderSlot? = null)

/** Aufgelöste Slots: jede Region garantiert non-null (Default eingesetzt, wo das Pack nichts liefert). */
data class ResolvedSlots(val header: HeaderSlot)

/**
 * Auflöser der Slot-Naht: pro Region „Pack-Slot **oder** Default". **Pure Funktion** über nullbare
 * Referenzen → unit-testbar ohne Compose-Laufzeit (siehe `SlotFallbackTest`). Reicht die Referenz
 * unverändert durch (keine Wrapper), damit referenzielle Identität trägt.
 */
object UiSlots {
    fun resolve(pack: UiSlotPack): ResolvedSlots = ResolvedSlots(header = pack.header ?: DefaultSlots.header)
}

/**
 * Das mitgelieferte Default-Pack — der heutige Onyx-Look, **verhaltensgleich** zum direkten
 * [StandardTopAppBar]-Aufruf. Fehlt einem Community-Pack ein Slot, greift dieser Wert.
 */
object DefaultSlots {
    val header: HeaderSlot = { title, onBack, actions ->
        StandardTopAppBar(title = title, onBack = onBack, actions = actions)
    }
}

/**
 * App-weit bereitgestellte, **aufgelöste** Slots. Default = das mitgelieferte Pack. Der Host
 * ([com.komgareader.app.ui.theme.KomgaReaderTheme]) stellt hier das aktive Pack bereit — analog
 * [com.komgareader.app.ui.theme.LocalUiPack]. Consumer lesen `LocalResolvedSlots.current.header(...)`
 * statt [StandardTopAppBar] direkt aufzurufen.
 */
val LocalResolvedSlots = staticCompositionLocalOf { UiSlots.resolve(UiSlotPack()) }
