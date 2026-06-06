package com.komgareader.app.ui.reader

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Versteckt System-Status- und Navigationsleiste, solange der Reader sichtbar ist
 * (echtes Vollbild). Stellt beide beim Verlassen wieder her. Die Bars lassen sich
 * weiterhin per Wisch transient einblenden.
 */
@Composable
fun ImmersiveFullscreenEffect() {
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
            ?: return@DisposableEffect onDispose {}
        val controller = WindowCompat.getInsetsController(window, view)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            WindowCompat.setDecorFitsSystemWindows(window, true)
        }
    }
}

/**
 * Durchscheinende Reader-Leiste, die **über** dem Inhalt schwebt (kein Reflow, die
 * Scrollposition bleibt unverändert). Nur sichtbar, wenn [visible]. Enthält immer
 * einen Zurück-Button; rechts optionale [actions].
 *
 * Der halbtransparente schwarze Hintergrund lässt den Inhalt durchscheinen, sodass
 * kein deckendes Weiß den Inhalt stört.
 */
@Composable
fun BoxScope.ReaderChromeOverlay(
    visible: Boolean,
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    if (!visible) return
    Row(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.45f))
            .displayCutoutPadding()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Zurück",
                tint = Color.White,
            )
        }
        Text(
            text = title,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
        )
        actions()
    }
}
