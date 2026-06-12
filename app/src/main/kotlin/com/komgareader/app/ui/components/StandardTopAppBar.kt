package com.komgareader.app.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import com.komgareader.ui.icons.AppIcons

/**
 * Einheitliche Titelleiste: Titel links (optional Zurück-Pfeil), Aktions-Icons rechts.
 * **Adressierbarer Chrome-Baustein** — eine Stelle für den Header-Look statt pro Screen eine eigene
 * `TopAppBar` (SeriesDetail, Settings-Unterseiten, …). Ein späteres UI-Pack ersetzt genau diese
 * Region (Ziel modulare UI, `big-picture-and-goals.md`), ohne jeden Screen anzufassen.
 *
 * @param onBack `null` = kein Zurück-Pfeil (Top-Level-Screen). Sonst Hardware-Back-äquivalente Aktion.
 * @param actions rechte Aktions-Icons (nur **funktionierende** Aktionen, keine Dead-Buttons).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StandardTopAppBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(AppIcons.Back, contentDescription = null)
                }
            }
        },
        actions = actions,
    )
}
