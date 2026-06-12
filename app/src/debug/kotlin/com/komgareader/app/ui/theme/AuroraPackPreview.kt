package com.komgareader.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.komgareader.ui.theme.AuroraPack

/**
 * Swap-Beweis: [AuroraPack] rendert mit dem richtigen ColorScheme, Shapes und Typography.
 * NUR Debug/Preview, keine Nutzer-Einstellung.
 */
@Preview(name = "Aurora dark")
@Preview(name = "Aurora light")
@Composable
private fun AuroraPackPreview() {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = AuroraPack.colorScheme(dark),
        shapes = AuroraPack.shapes,
        typography = AuroraPack.typography,
    ) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "Bibliothek",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
}
