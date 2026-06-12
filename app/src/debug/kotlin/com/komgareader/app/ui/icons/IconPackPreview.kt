package com.komgareader.app.ui.icons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.komgareader.app.ui.theme.KomgaReaderTheme

/**
 * **Beweis der Icon-Pack-Naht — kein Kern-Umbau nötig.** Ein [AlternativeIconPack] überschreibt nur
 * [IconKey.Home] (→ `Library`-Glyph) und gibt für alle anderen Keys `null` zurück; der Halter
 * [ActiveIconPack] fällt damit pro Key sauber auf das [DefaultIconPack] zurück. Die Call-Sites
 * (`Icon(AppIcons.Home, …)`) ändern sich nicht — sie zeigen plötzlich den Alt-Glyph für `Home`,
 * den Default für den Rest.
 *
 * Bewusst nur ein **Debug/Preview-Pfad**, **keine** Nutzer-Einstellung (der Pack-Lader/-Wähler ist
 * Soll, L1/L2). Anders als die UI-Slot-Previews wird das Pack **prozess-global** gesetzt (Spec §2:
 * Icons sind app-weiter Look, kein CompositionLocal). Da [ActiveIconPack] globaler Zustand ist, setzt
 * [IconRaster] das Pack in einem [DisposableEffect] und **stellt im `onDispose` den vorherigen Wert
 * wieder her** — sonst leckte ein gerendertes Alternativ-Preview den Glyph in andere Previews
 * desselben Prozesses.
 */
private val AlternativeIconPack = IconPack { key ->
    if (key == IconKey.Home) LucideIcons.Library else null
}

@Composable
private fun IconRaster(pack: IconPack) {
    // Im Body setzen, damit die unten gelesenen `AppIcons.*` im selben Composition-Pass den Pack-Glyph
    // zeigen (ein globaler `var` triggert keine Recomposition — Setzen in einem Effect käme zu spät).
    ActiveIconPack.current = pack
    // …und beim Verlassen der Preview zurücksetzen, damit der globale Zustand nicht in andere Previews
    // desselben Prozesses leckt.
    DisposableEffect(pack) {
        onDispose { ActiveIconPack.current = DefaultIconPack }
    }
    KomgaReaderTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(AppIcons.Home, contentDescription = "Home")
                Icon(AppIcons.Search, contentDescription = "Search")
                Icon(AppIcons.Settings, contentDescription = "Settings")
                Icon(AppIcons.Library, contentDescription = "Library")
            }
            Text("Home links — restliche Glyphen vom Default-Pack.")
        }
    }
}

/** Default-Pack: alle Glyphen wie ausgeliefert. */
@Preview(name = "Icon-Pack · Default", widthDp = 320, heightDp = 160)
@Composable
private fun IconPackDefaultPreview() {
    IconRaster(DefaultIconPack)
}

/** Alternatives Pack: nur `Home` ersetzt, Rest fällt auf den Default zurück — gleiche Call-Sites. */
@Preview(name = "Icon-Pack · Alternativ (Home ersetzt)", widthDp = 320, heightDp = 160)
@Composable
private fun IconPackAlternativePreview() {
    IconRaster(AlternativeIconPack)
}
