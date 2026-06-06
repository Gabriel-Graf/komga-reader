package com.komgareader.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.komgareader.app.BuildConfig
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.ui.components.SubPageScaffold

/** Über: App-Name, Version, Geräte-Hinweis. */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val s = LocalStrings.current
    SubPageScaffold(title = s.settingsAbout, onBack = onBack) {
        Column {
            Text(s.appName, style = MaterialTheme.typography.titleLarge)
            Text(
                s.aboutDevice,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(Modifier.fillMaxWidth()) {
            Text("${s.versionLabel}: ", style = MaterialTheme.typography.bodyMedium)
            Text(BuildConfig.VERSION_NAME, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
