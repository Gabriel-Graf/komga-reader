package com.komgareader.app.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.komgareader.ui.slots.ReaderBottomSheet

/**
 * Swap/layout proof of the `readerChrome` bottom-sheet capability: the collapsed peek bar (chrome
 * visible) and the expanded full-width sheet. Debug/preview only — not a user setting. (The real
 * swipe gesture is device-bound; this only renders the two states.)
 */
@Preview(name = "Bottom sheet - collapsed peek", widthDp = 360, heightDp = 640)
@Composable
private fun BottomSheetCollapsedPreview() {
    Box(Modifier.fillMaxSize()) {
        val sheet = ReaderBottomSheet(
            expanded = false,
            onExpandedChange = {},
            peekLabel = "Einstellungen",
            content = { Text("Inhalt") },
        )
        ReaderBottomSheetLayer(sheet = sheet, chromeVisible = true)
    }
}

@Preview(name = "Bottom sheet - expanded", widthDp = 360, heightDp = 640)
@Composable
private fun BottomSheetExpandedPreview() {
    Box(Modifier.fillMaxSize()) {
        var expanded by remember { mutableStateOf(true) }
        val sheet = ReaderBottomSheet(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            peekLabel = "Einstellungen",
            content = { Text("Typografie / TOC Inhalt …") },
        )
        ReaderBottomSheetLayer(sheet = sheet, chromeVisible = true)
    }
}
