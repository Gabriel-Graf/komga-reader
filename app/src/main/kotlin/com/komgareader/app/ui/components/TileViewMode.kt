package com.komgareader.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.request.ImageRequest
import com.komgareader.app.data.coil.SourceCover
import com.komgareader.app.ui.icons.AppIcons
import com.komgareader.app.ui.theme.EinkTokens

/**
 * Drei Anzeigemodi für die Sammlungen-/Bibliotheken-Listen — geteilt (DRY), damit beide Tabs
 * dieselbe Mechanik nutzen. [columns] = `null` für die Zeilenliste, sonst die Spaltenzahl des
 * Cover-Gitters (Kachel = dichteres 4er-Raster, große Kachel = 3er-Raster).
 */
enum class TileViewMode(val columns: Int?) {
    LIST(null),
    TILE(4),
    LARGE_TILE(3),
}

/** Sicheres Parsen des persistierten Strings; unbekannt fällt auf [fallback]. */
fun tileViewModeOf(value: String?, fallback: TileViewMode): TileViewMode =
    runCatching { value?.let { TileViewMode.valueOf(it) } }.getOrNull() ?: fallback

/** Nächster Modus im Rotations-Zyklus Liste → Kachel → große Kachel → Liste. */
fun TileViewMode.next(): TileViewMode = when (this) {
    TileViewMode.LIST -> TileViewMode.TILE
    TileViewMode.TILE -> TileViewMode.LARGE_TILE
    TileViewMode.LARGE_TILE -> TileViewMode.LIST
}

/**
 * **Ein** Umschalt-Button (wie der Listen/Gitter-Toggle in den Buch-Details), der die drei Ansichten
 * durchrotiert: zeigt das Icon des **aktuellen** Modus, ein Tipp schaltet auf den nächsten. Für die
 * Top-Bar (kompakt, neutrale Chrome — kein Dauer-Akzent).
 */
@Composable
fun RotatingViewModeButton(
    current: TileViewMode,
    onSelect: (TileViewMode) -> Unit,
    listLabel: String,
    tileLabel: String,
    largeTileLabel: String,
) {
    val (icon, label) = when (current) {
        TileViewMode.LIST -> AppIcons.ListView to listLabel
        TileViewMode.TILE -> AppIcons.GridView to tileLabel
        TileViewMode.LARGE_TILE -> AppIcons.LargeGridView to largeTileLabel
    }
    IconButton(onClick = { onSelect(current.next()) }) {
        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * Cover-dominierte Kachel mit 2×2-Collage der ersten vier Mitglieder-Cover, Hairline-Rahmen und
 * Titelband unten ([TileTitleBand]) — **die geteilte Tile-Mechanik** für Bibliotheken **und**
 * Sammlungen (DRY, eine Stelle). Optionale Overlays [topStart] (z. B. Typ-Chip) und [topEnd]
 * (z. B. Aktionen) liegen in den oberen Ecken.
 */
@Composable
fun CollageTile(
    covers: List<SourceCover>,
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    topStart: (@Composable () -> Unit)? = null,
    topEnd: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(EinkTokens.hairline, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
    ) {
        CompositeCover(covers)
        topStart?.let { Box(Modifier.align(Alignment.TopStart).padding(4.dp)) { it() } }
        topEnd?.let { Box(Modifier.align(Alignment.TopEnd)) { it() } }
        TileTitleBand(name, Modifier.align(Alignment.BottomStart))
    }
}

/**
 * 2×2-Collage der ersten vier Cover. Fehlende Slots bleiben ruhige Flächen — bei 1–3 Mitgliedern
 * wird stets das Vierer-Raster gezeigt. Geteilt zwischen Bibliotheks- und Sammlungs-Kacheln.
 */
@Composable
fun CompositeCover(covers: List<SourceCover>) {
    Column(Modifier.fillMaxSize()) {
        repeat(2) { row ->
            Row(Modifier.fillMaxWidth().weight(1f)) {
                repeat(2) { col ->
                    CoverSlot(
                        cover = covers.getOrNull(row * 2 + col),
                        modifier = Modifier.fillMaxSize().weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * Geteilte **Listen-Zeile** (DRY aus den Sammlungen): Hairline-Rahmen, Titel + optionaler
 * Untertitel links, optionaler [trailing]-Slot rechts (Badge/Aktionen). Für die Listen-Ansicht
 * von Bibliotheken **und** Sammlungen.
 */
@Composable
fun EntityListRow(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(EinkTokens.hairline, MaterialTheme.colorScheme.outline, RoundedCornerShape(EinkTokens.tileRadius))
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke(this)
    }
}

@Composable
private fun CoverSlot(cover: SourceCover?, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (cover != null) {
            val request = remember(cover) {
                ImageRequest.Builder(ctx).data(cover).crossfade(false).build()
            }
            FilteredAsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
