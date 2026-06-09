package com.komgareader.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.request.ImageRequest
import com.komgareader.app.data.coil.SourceCover
import com.komgareader.app.ui.icons.AppIcons
import com.komgareader.domain.model.Series

/**
 * Bibliotheks-Kachel für eine Serie: Cover (quellen-agnostisch über [SourceCover]/Coil-Fetcher),
 * Lokal/Cloud-Badge oben rechts, Titel-Band unten. **Adressierbarer Chrome-Baustein** — eine Stelle
 * für den Tile-Look über alle Screens (Bibliothek, Gruppen), damit ein späteres UI-Pack genau diese
 * Region ersetzen kann statt jeden Grid-Aufrufer (Ziel modulare UI, `big-picture-and-goals.md`).
 *
 * Cover-dominiert → der Rahmen bleibt geräteübergreifend ein Border (frasst das Bild ein); Farbe
 * trägt der Akzent im restlichen Chrome, nicht die Kachel.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SeriesTile(
    series: Series,
    isLocal: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val request = remember(series.sourceId, series.remoteId) {
        ImageRequest.Builder(ctx)
            .data(SourceCover(series.sourceId, series.remoteId, isSeries = true))
            .crossfade(false).build()
    }
    Box(
        modifier
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(4.dp))
            .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        FilteredAsyncImage(
            model = request,
            contentDescription = series.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        // Lokal/Cloud-Badge mit opaquem Hintergrund (auf jedem Cover sichtbar).
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(24.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), CircleShape)
                .padding(3.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (isLocal) AppIcons.Local else AppIcons.Cloud,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }

        TileTitleBand(series.title, Modifier.align(Alignment.BottomStart))
    }
}

/** Scrim hinter dem Titelband — dunkel genug, dass weißer Text auf jedem Cover lesbar bleibt. */
private val TileScrim = Color.Black.copy(alpha = 0.7f)

/**
 * Geteiltes Titelband am unteren Rand einer Cover-Kachel (Serie **und** Gruppe): dunkler Scrim,
 * weißer Text, eine Stelle für den Look — kein 2× hartes `Color.Black`/`10.sp` über die Grids
 * verstreut (Konsistenz + ein künftiges UI-Pack tauscht genau hier).
 */
@Composable
fun TileTitleBand(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.labelSmall,
        color = Color.White,
        modifier = modifier
            .fillMaxWidth()
            .background(TileScrim)
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}
