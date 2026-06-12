package com.komgareader.app.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.komgareader.app.ui.slots.LocalResolvedSlots
import com.komgareader.app.ui.slots.ReaderChromeSlot
import com.komgareader.app.ui.slots.UiSlotPack
import com.komgareader.app.ui.slots.UiSlots

/**
 * Swap-Beweis: ein alternatives Reader-Gerüst — der **Status-Fuß wandert nach oben** (über den
 * Inhalt, statt unten als Overlay), die Chrome-Menüleiste bleibt über die `overlay`-Region, und der
 * Inhalt füllt den Rest — dieselbe [ReaderScaffoldState]-Surface anders angeordnet, ohne das
 * [ReaderScaffold] (den Host-Wrapper) oder die Reader anzufassen. Belegt, dass ein UI-Pack das ganze
 * Lese-Chrome über die `readerChrome`-Region neu arrangieren kann, während der Scaffold-Aufruf in den
 * fünf Readern unverändert bleibt. NUR Debug/Preview, keine Nutzer-Einstellung.
 *
 * Bewusst **weggelassen** in diesem Swap-Beweis: die Tap-Zonen-Hints und der Start-Hinweis (dieses
 * minimale Gerüst zeigt sie nicht); produktive Packs sollten sie platzieren. Die Tap-Zonen-Navigation
 * ([ReaderScaffoldState.onPrev]/[onNext]/[onToggleChrome] bzw. [tapModifier]) und der E-Ink-Scrim der
 * Menüleiste bleiben über die `overlay`-Region bzw. den Host erzwungen; Refresh/Engine-Navigation
 * (Naht B) sind gar nicht Teil der Surface.
 */
@Composable
fun AlternativeReaderChrome(state: ReaderScaffoldState) {
    Box(Modifier.fillMaxSize().background(state.background)) {
        state.content()

        // Footer oben statt unten: nur bei sichtbarem Chrome (host-gegatet wie im Default).
        if (state.footer != null && state.chromeVisible) {
            val footer = state.footer
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                footer.invoke(this)
            }
        }

        // Chrome-Menüleiste über die overlay-Region (BoxScope-Extension → expliziter Receiver).
        if (state.chromeVisible) {
            val overlay = LocalResolvedSlots.current.overlay
            with(this) {
                overlay(
                    ReaderOverlayState(
                        title = state.title,
                        onBack = state.onBack,
                        onHome = state.onHome,
                        onSettings = state.onSettings,
                        actions = state.actions,
                    ),
                )
            }
        }
    }
}

/**
 * Zeigt, dass derselbe Scaffold-Pfad über ein Pack mit alternativem `readerChrome`-Slot die
 * [AlternativeReaderChrome] rendert — gleiche Surface, anderes Gerüst, Call-Site unverändert.
 */
@Preview(widthDp = 360, heightDp = 480)
@Composable
private fun AlternativeReaderChromePreview() {
    val alternative: ReaderChromeSlot = { state -> AlternativeReaderChrome(state) }
    val state = ReaderScaffoldState(
        chromeVisible = true,
        onToggleChrome = {},
        title = "Beispiel-Titel",
        onBack = {},
        onHome = {},
        onSettings = {},
        onPrev = {},
        onNext = {},
        content = {
            Box(Modifier.fillMaxSize().background(Color.DarkGray), contentAlignment = Alignment.Center) {
                Text("Seiteninhalt", color = Color.White)
            }
        },
    )
    CompositionLocalProvider(
        LocalResolvedSlots provides UiSlots.resolve(UiSlotPack(readerChrome = alternative)),
    ) {
        LocalResolvedSlots.current.readerChrome(state)
    }
}
