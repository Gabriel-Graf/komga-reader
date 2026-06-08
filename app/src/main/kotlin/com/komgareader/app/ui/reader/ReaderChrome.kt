package com.komgareader.app.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.ui.components.LocalEinkMode
import com.komgareader.app.ui.icons.AppIcons

/**
 * Hintergrund-Farbe für alle Reader-Overlays (Top-Leiste, Status-Fuß, Tap-Zonen).
 * Über [LocalEinkMode] gegatet (`animation-gating`/`eink-design-language`): Auf E-Ink
 * **deckend** (hart, kein Durchscheinen — ruhiger, ghosting-arm), auf Smartphone mit
 * [transparentAlpha] durchscheinend, damit der Inhalt sichtbar bleibt.
 */
@Composable
fun readerOverlayScrim(base: Color, transparentAlpha: Float): Color =
    if (LocalEinkMode.current) base else base.copy(alpha = transparentAlpha)

/**
 * Reader-Leiste, die **über** dem Inhalt schwebt (kein Reflow, die Scrollposition
 * bleibt unverändert). Nur sichtbar, wenn [visible]. Enthält immer einen Zurück-Button;
 * rechts optionale [actions].
 *
 * Hintergrund über [readerOverlayScrim]: E-Ink deckend schwarz, Smartphone halbtransparent.
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
            .background(readerOverlayScrim(Color.Black, 0.45f))
            .displayCutoutPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                AppIcons.Back,
                contentDescription = "Zurück",
                tint = Color.White,
            )
        }
        Text(
            text = title,
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
        actions()
    }
}

/**
 * Geteilter Status-Fuß (Seite X / N) für die einfachen Reader (Paged/Webtoon/Epub).
 * Schwebt am unteren Rand, Hintergrund über [readerOverlayScrim] (E-Ink deckend).
 *
 * [dark] = `true` für Reader mit schwarzem Hintergrund (schwarze Leiste, weiße Schrift),
 * `false` für helle Reader (graue Leiste, schwarze Schrift).
 */
@Composable
fun BoxScope.ReaderStatusBar(text: String, dark: Boolean) {
    val base = if (dark) Color.Black else Color.LightGray
    Text(
        text = text,
        color = if (dark) Color.White else Color.Black,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 16.dp)
            .background(readerOverlayScrim(base, 0.6f))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

/**
 * Visualisierung der Drittel-Tap-Zonen: das linke und rechte Drittel bekommen einen
 * Hintergrund wie das Overlay plus einen Pfeil in Blätter-Richtung und ein Label
 * (Letzte/Nächste Seite). Wird nur eingeblendet, wenn das Chrome sichtbar ist.
 *
 * **Nicht interaktiv** (kein `pointerInput`): die darunterliegenden Tap-Zonen des
 * [ReaderScaffold] bleiben aktiv — die Hints sind reine Orientierung. Die Mitte bleibt
 * frei (Tap = Chrome umschalten).
 */
@Composable
fun BoxScope.ReaderTapZoneHints() {
    val strings = LocalStrings.current
    val scrim = readerOverlayScrim(Color.Black, 0.45f)
    Row(Modifier.matchParentSize()) {
        TapZoneHint(Modifier.weight(1f), scrim, AppIcons.Back, strings.readerPrevPage)
        Spacer(Modifier.weight(1f))
        TapZoneHint(Modifier.weight(1f), scrim, AppIcons.Forward, strings.readerNextPage)
    }
}

@Composable
private fun TapZoneHint(modifier: Modifier, scrim: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Column(
        modifier = modifier.fillMaxHeight().background(scrim),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = Color.White)
        Spacer(Modifier.height(8.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * Kurzer Start-Hinweis oben mittig, der beim Öffnen jedes Readers daran erinnert, dass
 * die Menüleiste per Tipp in die Mitte ein-/ausgeblendet wird. Verschwindet nach ~1,5 s.
 *
 * Animation über [LocalEinkMode] gegatet (`animation-gating`): Smartphone blendet per
 * Fade ein/aus, E-Ink schaltet **sofort** (kein Ghosting durch animiertes Verblassen).
 */
@Composable
fun BoxScope.ReaderStartHint(text: String, visible: Boolean) {
    val align = Modifier
        .align(Alignment.TopCenter)
        .displayCutoutPadding()
        .padding(top = 24.dp)
    val pill: @Composable () -> Unit = {
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .background(Color.Black, RoundedCornerShape(8.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
    if (LocalEinkMode.current) {
        if (visible) Box(align) { pill() }
    } else {
        AnimatedVisibility(
            visible = visible,
            modifier = align,
            enter = fadeIn(),
            exit = fadeOut(),
        ) { pill() }
    }
}

/**
 * Standard-Aktions-Icon, das zwischen den Lesemodi umschaltet — von allen Readern
 * im Overlay rechts genutzt. Hält das identische [AppIcons.ReaderMode]-Glyph an
 * einer Stelle.
 */
@Composable
internal fun ReaderModeAction(onToggleMode: () -> Unit, contentDescription: String) {
    IconButton(onClick = onToggleMode) {
        Icon(AppIcons.ReaderMode, contentDescription = contentDescription, tint = Color.White)
    }
}
