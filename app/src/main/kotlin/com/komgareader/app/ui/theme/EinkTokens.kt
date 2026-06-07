package com.komgareader.app.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Zentrale Maß-Tokens für den Onyx-E-Ink-Look. Farben/Radien kommen aus dem
 * MaterialTheme (siehe [KomgaReaderTheme]); hier nur Abstände und Rahmenstärken,
 * damit kein Magic-dp im UI-Code landet.
 */
object EinkTokens {
    /** Hairline-Rahmen für ruhige Flächen (Tiles, Listenzeilen, Divider). 1.5dp — dünner ist auf E-Ink unsichtbar. */
    val hairline = 1.5.dp

    /** Betonter Rahmen — immer schwarz; Pflicht für Modals. */
    val strongBorder = 2.dp

    /** Eckenradius für Tiles/Quick-Action-Kacheln. */
    val tileRadius = 10.dp

    /** Rand zum Bildschirm. */
    val screenPadding = 16.dp

    /** Vertikaler Abstand zwischen Sektionen. */
    val sectionGap = 16.dp

    /** Abstand im Tile-Grid. */
    val tileGap = 8.dp

    /** Icon-Größe in der Bottom-Menubar. */
    val navIcon = 34.dp
}
