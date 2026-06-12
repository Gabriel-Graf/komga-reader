package com.komgareader.app.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.komgareader.app.ui.slots.resolveSlots
import com.komgareader.ui.icons.AppIcons
import com.komgareader.ui.slots.LocalResolvedSlots
import com.komgareader.ui.slots.OverlaySlot
import com.komgareader.ui.slots.ReaderOverlayState
import com.komgareader.ui.slots.UiSlotPack

/**
 * Swap-Beweis: eine alternative Reader-Chrome-Menüleiste — **Shortcuts (Home/Einstellungen) links**,
 * **Titel zentriert**, Zurück rechts — dieselbe [ReaderOverlayState]-Surface anders angeordnet, ohne
 * das [ReaderScaffold] anzufassen. Belegt, dass ein UI-Pack die Leiste über die `overlay`-Region
 * ersetzen kann, während der Scaffold-Aufruf unverändert bleibt. NUR Debug/Preview, keine
 * Nutzer-Einstellung.
 *
 * Die reader-spezifischen [ReaderOverlayState.actions] werden in diesem Swap-Beweis bewusst
 * weggelassen (das zentrierte Titel-Layout hat keine Aktions-Spalte); produktive Packs sollten sie
 * platzieren. Der E-Ink-Scrim ([readerOverlayScrim]) + die Sichtbarkeit (chromeVisible) bleiben
 * host-erzwungen.
 */
@Composable
fun BoxScope.AlternativeReaderOverlay(state: ReaderOverlayState) {
    Row(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .background(readerOverlayScrim(Color.Black, 0.45f))
            .displayCutoutPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = state.onHome) {
            Icon(AppIcons.Home, contentDescription = null, tint = Color.White)
        }
        IconButton(onClick = state.onSettings) {
            Icon(AppIcons.Settings, contentDescription = null, tint = Color.White)
        }
        Text(
            text = state.title,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        IconButton(onClick = state.onBack) {
            Icon(AppIcons.Back, contentDescription = null, tint = Color.White)
        }
    }
}

/**
 * Zeigt, dass derselbe Scaffold-Aufruf über ein Pack mit alternativem `overlay`-Slot die
 * [AlternativeReaderOverlay] rendert — gleiche Surface, andere Leiste, Call-Site unverändert.
 */
@Preview(widthDp = 360, heightDp = 120)
@Composable
private fun AlternativeOverlayPreview() {
    val alternative: OverlaySlot = { state -> AlternativeReaderOverlay(state) }
    val state = ReaderOverlayState(
        title = "Beispiel-Titel",
        onBack = {},
        onHome = {},
        onSettings = {},
        actions = {},
    )
    CompositionLocalProvider(
        LocalResolvedSlots provides resolveSlots(UiSlotPack(overlay = alternative)),
    ) {
        Box(Modifier.fillMaxSize().background(Color.DarkGray)) {
            // overlay ist eine BoxScope-Extension; der BoxScope-Receiver (diese Box) wird über
            // `with(this)` explizit gemacht (der implizite Receiver greift bei Funktionswerten nicht).
            val overlay = LocalResolvedSlots.current.overlay
            with(this) { overlay(state) }
        }
    }
}
