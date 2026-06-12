package com.komgareader.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.request.ImageRequest
import com.komgareader.app.data.coil.SourceCover
import com.komgareader.app.ui.slots.resolveSlots
import com.komgareader.domain.model.Series
import com.komgareader.ui.slots.LocalResolvedSlots
import com.komgareader.ui.slots.TileState
import com.komgareader.ui.slots.UiSlotPack

/**
 * Swap-Beweis: ein alternatives Kachel-Layout — **Titel über dem Cover** (eigene Zeile) statt als
 * Scrim-Band unten, dieselbe [TileState]-Surface anders angeordnet, ohne eine Aufrufstelle
 * anzufassen. Belegt, dass ein UI-Pack den Kachel-Look über die `tiles`-Region ersetzen kann,
 * während [SeriesTile] unverändert aufgerufen wird. NUR Debug/Preview, keine Nutzer-Einstellung.
 *
 * Das Lokal/Cloud-Badge wird in diesem Swap-Beweis bewusst weggelassen (kein Bestandteil des
 * Titel-oben-Layouts); produktive Packs sollten [TileState.isLocal] sichtbar machen. Das
 * Cover-Laden + der E-Ink-Filter ([FilteredAsyncImage], `crossfade(false)`) bleiben host-erzwungen.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlternativeTile(state: TileState, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val request = remember(state.series.sourceId, state.series.remoteId) {
        ImageRequest.Builder(ctx)
            .data(SourceCover(state.series.sourceId, state.series.remoteId, isSeries = true))
            .crossfade(false).build()
    }
    Column(
        modifier.combinedClickable(onClick = state.onClick, onLongClick = state.onLongClick),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            state.series.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
        )
        FilteredAsyncImage(
            model = request,
            contentDescription = state.series.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(4.dp))
                .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp)),
        )
    }
}

/**
 * Zeigt, dass derselbe [SeriesTile]-Aufruf über ein Pack mit alternativem `tiles`-Slot den
 * [AlternativeTile] rendert — gleiche Surface, andere Kachel, Call-Site unverändert.
 */
@Preview(widthDp = 200, heightDp = 400)
@Composable
private fun AlternativeTilePreview() {
    val fakeSeries = Series(
        id = 1L,
        sourceId = 0L,
        remoteId = "preview-1",
        title = "Beispiel-Serie",
    )
    CompositionLocalProvider(
        LocalResolvedSlots provides
            resolveSlots(UiSlotPack(tiles = { state, modifier -> AlternativeTile(state, modifier) })),
    ) {
        SeriesTile(
            series = fakeSeries,
            isLocal = true,
            onClick = {},
            onLongClick = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}
