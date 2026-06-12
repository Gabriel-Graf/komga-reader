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
import com.komgareader.app.ui.slots.resolveSlots
import com.komgareader.ui.slots.LocalResolvedSlots
import com.komgareader.ui.slots.ReaderChromeSlot
import com.komgareader.ui.slots.ReaderOverlayState
import com.komgareader.ui.slots.ReaderScaffoldState
import com.komgareader.ui.slots.UiSlotPack

/**
 * Swap-Beweis: ein **minimales** alternatives Reader-Gerüst — nur Inhalt + die Chrome-Menüleiste über
 * die `overlay`-Region, ohne Status-Fuß, Tap-Zonen-Hints und Start-Hinweis — dieselbe
 * [ReaderScaffoldState]-Surface anders (knapper) angeordnet, ohne das [ReaderScaffold] (den
 * Host-Wrapper) oder die Reader anzufassen. Belegt, dass ein UI-Pack das ganze Lese-Chrome über die
 * `readerChrome`-Region neu arrangieren (und Stücke weglassen) kann, während der Scaffold-Aufruf in den
 * fünf Readern unverändert bleibt. NUR Debug/Preview, keine Nutzer-Einstellung.
 *
 * Bewusst **weggelassen** in diesem Swap-Beweis: [ReaderScaffoldState.footer] (der `ReaderStatusBar`
 * richtet sich selbst `BottomCenter` aus — ein Pack, das ihn anders platzieren will, liefert seinen
 * eigenen Footer), die Tap-Zonen-Hints und der Start-Hinweis; produktive Packs sollten diese platzieren.
 * Die Tap-Zonen-Navigation ([ReaderScaffoldState.onPrev]/[onNext]/[onToggleChrome] bzw. [tapModifier])
 * und der E-Ink-Scrim der Menüleiste bleiben über die `overlay`-Region bzw. den Host erzwungen;
 * Refresh/Engine-Navigation (Naht B) sind gar nicht Teil der Surface.
 */
@Composable
fun AlternativeReaderChrome(state: ReaderScaffoldState) {
    Box(Modifier.fillMaxSize().background(state.background)) {
        state.content()

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
        LocalResolvedSlots provides resolveSlots(UiSlotPack(readerChrome = alternative)),
    ) {
        LocalResolvedSlots.current.readerChrome(state)
    }
}
