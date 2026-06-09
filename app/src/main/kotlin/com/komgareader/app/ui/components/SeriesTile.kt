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
import androidx.compose.ui.unit.sp
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

        Text(
            series.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(2.dp),
            color = Color.White,
            fontSize = 10.sp,
        )
    }
}
