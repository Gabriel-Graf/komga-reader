package com.komgareader.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.ui.components.SettingsGroup
import com.komgareader.ui.icons.AppIcons

/**
 * Read-only overview of the device's hardware long-press shortcuts (Onyx volume keys). Mirrors the
 * live wiring in [com.komgareader.app.ui.reader.ReaderShortcutsViewModel] — long VOLUME-UP returns to
 * the home screen, long VOLUME-DOWN forces a full anti-ghosting refresh. Display only (no remapping in
 * v1); gated to devices with hardware buttons in [buildSettingsSections].
 */
@Composable
fun ButtonsSettingsContent() {
    val s = LocalStrings.current
    SettingsGroup(s.settingsButtons, query = "", helper = s.settingsButtonsDesc) {
        ButtonRow(
            icon = AppIcons.Home,
            action = s.buttonActionHome,
            key = s.buttonKeyVolumeUpLong,
            desc = s.buttonActionHomeDesc,
        )
        ButtonRow(
            icon = AppIcons.Refresh,
            action = s.buttonActionRefresh,
            key = s.buttonKeyVolumeDownLong,
            desc = s.buttonActionRefreshDesc,
        )
    }
}

/** One mapping row: icon · (action title + one-line description) · the key hint, right-aligned. */
@Composable
private fun ButtonRow(icon: ImageVector, action: String, key: String, desc: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
        Column(
            Modifier.weight(1f).padding(start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                action,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            key,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}
