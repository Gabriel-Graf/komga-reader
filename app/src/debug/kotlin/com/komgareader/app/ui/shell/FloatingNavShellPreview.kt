package com.komgareader.app.ui.shell

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.komgareader.app.ui.components.BottomNavItem
import com.komgareader.ui.icons.AppIcons
import com.komgareader.ui.theme.AuroraPack
import com.komgareader.ui.theme.LocalDesignTokens

/**
 * Swap-Beweis: [FloatingNavBar] rendert mit dem Aurora-Look (Cobalt-Akzent, Schatten-Tiefe,
 * gerundete Pille). NUR Debug/Preview, keine Nutzer-Einstellung.
 */
@Preview(name = "Aurora FloatingNavBar")
@Composable
private fun FloatingNavBarPreview() {
    MaterialTheme(colorScheme = AuroraPack.colorScheme(dark = true)) {
        CompositionLocalProvider(LocalDesignTokens provides AuroraPack.designTokens(dark = true)) {
            Surface(color = MaterialTheme.colorScheme.background) {
                FloatingNavBar(
                    items = listOf(
                        BottomNavItem(AppIcons.Library, "Bibliothek"),
                        BottomNavItem(AppIcons.Groups, "Gruppen"),
                        BottomNavItem(AppIcons.Settings, "Einstellungen"),
                    ),
                    selectedIndex = 0,
                    onSelect = {},
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
