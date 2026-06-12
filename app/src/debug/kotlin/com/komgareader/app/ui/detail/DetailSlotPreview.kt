package com.komgareader.app.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.komgareader.app.ui.slots.resolveSlots
import com.komgareader.ui.slots.DetailScaffoldState
import com.komgareader.ui.slots.DetailSlot
import com.komgareader.ui.slots.LocalResolvedSlots
import com.komgareader.ui.slots.UiSlotPack

/**
 * Swap-Beweis: ein alternatives Detail-Gerüst — **eigener schlanker Titelbalken** (eine simple
 * [Column] mit Titel-[Text]) statt des `header`-Slots und **ohne** Material-Scaffold — dieselbe
 * [DetailScaffoldState]-Surface anders gerahmt, ohne die Call-Sites (SeriesDetail/GroupBrowse)
 * anzufassen. Belegt, dass ein UI-Pack das ganze Detail-Gerüst über die `detail`-Region ersetzen
 * kann. NUR Debug/Preview, keine Nutzer-Einstellung.
 *
 * Bewusst weggelassene Fähigkeiten der Surface (R1–R4-Lehre): der
 * [DetailScaffoldState.snackbarHost] wird nicht gerendert (eigene Rahmung ohne Snackbar-Anker) und
 * die [DetailScaffoldState.actions] entfallen (der schlanke Titelbalken hat keine Aktions-Spalte).
 * Produktive Packs sollten beide platzieren. Der Body wird mit Null-Padding aufgerufen, da diese
 * Rahmung kein Material-Insets-Padding erzeugt.
 */
@Composable
fun AlternativeDetailScaffold(state: DetailScaffoldState) {
    Column(Modifier.fillMaxSize().background(Color.White)) {
        Text(
            text = state.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
        state.content(PaddingValues(0.dp))
    }
}

/**
 * Zeigt, dass derselbe `detail`-Aufruf über ein Pack mit alternativem `detail`-Slot die
 * [AlternativeDetailScaffold] rendert — gleiche [DetailScaffoldState]-Surface, andere Rahmung,
 * Call-Site unverändert.
 */
@Preview(widthDp = 360, heightDp = 240)
@Composable
private fun AlternativeDetailPreview() {
    val alternative: DetailSlot = { state -> AlternativeDetailScaffold(state) }
    val state = DetailScaffoldState(
        title = "Beispiel-Serie",
        onBack = {},
        actions = {},
        content = { padding ->
            Box(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                Text("Body-Inhalt (host-gebaut)")
            }
        },
    )
    CompositionLocalProvider(
        LocalResolvedSlots provides resolveSlots(UiSlotPack(detail = alternative)),
    ) {
        LocalResolvedSlots.current.detail(state)
    }
}
