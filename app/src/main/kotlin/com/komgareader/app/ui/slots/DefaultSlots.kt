package com.komgareader.app.ui.slots

import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.ui.components.DefaultDialog
import com.komgareader.app.ui.components.DefaultSeriesTile
import com.komgareader.app.ui.components.EinkSearchBar
import com.komgareader.app.ui.components.StandardTopAppBar
import com.komgareader.app.ui.detail.DefaultDetailScaffold
import com.komgareader.app.ui.home.DefaultHomeHeader
import com.komgareader.app.ui.reader.DefaultReaderOverlay
import com.komgareader.app.ui.reader.DefaultReaderScaffold
import com.komgareader.app.ui.settings.DefaultSettings
import com.komgareader.ui.icons.AppIcons
import com.komgareader.ui.slots.DialogSlot
import com.komgareader.ui.slots.DetailSlot
import com.komgareader.ui.slots.HeaderSlot
import com.komgareader.ui.slots.HeaderState
import com.komgareader.ui.slots.HomeHeaderSlot
import com.komgareader.ui.slots.OverlaySlot
import com.komgareader.ui.slots.ReaderChromeSlot
import com.komgareader.ui.slots.ResolvedSlots
import com.komgareader.ui.slots.SettingsSlot
import com.komgareader.ui.slots.TilesSlot
import com.komgareader.ui.slots.UiSlotPack
import com.komgareader.ui.slots.UiSlots

/**
 * Das mitgelieferte Default-Pack — der heutige Onyx-Look, verhaltensgleich zu den bisherigen
 * direkten Aufrufen. Fehlt einem Community-Pack ein Slot, greift dieser Wert. Diese Renderer koppeln
 * an app-i18n/-Komponenten (das `KomgaSource`-Äquivalent der UI) und bleiben deshalb in `:app`; der
 * **Vertrag** liegt im Modul `:ui-api` (A1).
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
    val readerChrome: ReaderChromeSlot = { state ->
        DefaultReaderScaffold(state)
    }

    /** Die mitgelieferten Slots als aufgelöstes Pack — Default-Argument für [UiSlots.resolve]. */
    val resolved: ResolvedSlots = ResolvedSlots(
        headerSlot = header,
        homeHeader = homeHeader,
        dialog = dialog,
        settings = settings,
        tiles = tiles,
        overlay = overlay,
        detail = detail,
        readerChrome = readerChrome,
    )
}

/** App-Komfort: resolve gegen das mitgelieferte Default-Pack. */
fun resolveSlots(pack: UiSlotPack): ResolvedSlots = UiSlots.resolve(pack, DefaultSlots.resolved)

/**
 * Der mitgelieferte such-fähige Onyx-Renderer der Header-Region. Ohne aktive Suche eine
 * [StandardTopAppBar] (bei vorhandener Such-Capability mit vorgelagerter Lupe); bei `search.active`
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
        // Such-Focus-State auf dieser Ebene halten (nicht im title-Lambda) — so hängt seine
        // Lebensdauer am DefaultHeader-Knoten, nicht am title-Slot, der bei Recomposition neu
        // entstehen könnte (State-Verlust / erneutes onClose).
        val focus = remember { FocusRequester() }
        // Erst schließen, wenn das Feld den Fokus WIEDER verliert (nicht beim initialen
        // Nicht-Fokus, der sonst sofort schließt) und leer ist.
        var wasFocused by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { focus.requestFocus() }
        TopAppBar(
            title = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    // Lokale val: [HeaderState] liegt im Modul :ui-api, ein cross-module-Property
                    // ist nicht smart-castbar.
                    val onBack = state.onBack
                    if (onBack != null) {
                        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                            Icon(AppIcons.Back, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
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
                            contentDescription = search.actionLabel ?: LocalStrings.current.searchAction,
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                state.actions(this)
            },
        )
    }
}
