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
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.ui.components.LocalEinkMode
import com.komgareader.app.ui.icons.AppIcons
import com.komgareader.app.ui.theme.EinkTokens
import kotlinx.coroutines.delay
import java.time.LocalTime

/**
 * Hintergrund-Farbe für die toggle­baren Reader-Overlays (Top-Menüleiste, einfacher
 * Status-Fuß, Tap-Zonen-Chips). Über [LocalEinkMode] gegatet
 * (`animation-gating`/`eink-design-language`): Auf E-Ink **deckend** (hart, kein
 * Durchscheinen — ruhiger, ghosting-arm), auf Smartphone mit [transparentAlpha]
 * durchscheinend, damit der Inhalt sichtbar bleibt.
 */
@Composable
fun readerOverlayScrim(base: Color, transparentAlpha: Float): Color =
    if (LocalEinkMode.current) base else base.copy(alpha = transparentAlpha)

/**
 * Capability-Surface der toggle­baren Reader-Chrome-Menüleiste: Titel + Navigations-/Shortcut-
 * Callbacks ([onBack] · [onHome] · [onSettings]) + die reader-spezifischen [actions]
 * (Inhaltsverzeichnis, Suche, Typografie, …). Ein [com.komgareader.app.ui.slots.OverlaySlot]-Pack
 * arrangiert daraus die Leiste.
 *
 * **Bewusst kein `visible`-Flag:** die Sichtbarkeit (chromeVisible) **und** der E-Ink-Scrim
 * ([readerOverlayScrim]) sind **host-erzwungen** (das [ReaderScaffold] rendert die Leiste nur bei
 * `chromeVisible`) — nicht Teil dieser Surface. So bleibt sie sauber und konsistent mit den anderen
 * Slot-Surfaces (kein Layout-/Zustands-Flag in der Surface).
 */
data class ReaderOverlayState(
    val title: String,
    val onBack: () -> Unit,
    val onHome: () -> Unit,
    val onSettings: () -> Unit,
    val actions: @Composable RowScope.() -> Unit,
)

/**
 * Default-Onyx-Renderer der Reader-Chrome-Menüleiste, die **über** dem Inhalt schwebt (kein Reflow).
 * Zurück-Button links; rechts zuerst die reader-spezifischen [ReaderOverlayState.actions]
 * (Inhaltsverzeichnis, Suche, Typografie, …), dann die **geteilten** Shortcuts
 * [ReaderOverlayState.onHome] (Bibliothek) und [ReaderOverlayState.onSettings] (Einstellungen) ganz
 * rechts. Die geteilten Buttons leben hier an genau **einer** Stelle
 * (`shared-structure-before-variants`) — kein Reader baut sie selbst.
 *
 * `BoxScope`-Extension, weil die Leiste sich per `Modifier.align(Alignment.TopCenter)` im
 * Reader-`Box` positioniert. Die Sichtbarkeit gatet der Host ([ReaderScaffold]). Hintergrund über
 * [readerOverlayScrim]: E-Ink deckend schwarz, Smartphone halbtransparent.
 */
@Composable
fun BoxScope.DefaultReaderOverlay(state: ReaderOverlayState) {
    val strings = LocalStrings.current
    Row(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .background(readerOverlayScrim(Color.Black, 0.45f))
            .displayCutoutPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = state.onBack) {
            Icon(AppIcons.Back, contentDescription = "Zurück", tint = Color.White)
        }
        Text(
            text = state.title,
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
        // Erst die reader-spezifischen Aktionen (Inhaltsverzeichnis, Suche, Typografie, …),
        // dann die geteilten Shortcuts Home + Einstellungen ganz rechts — über alle Reader gleich.
        state.actions(this)
        IconButton(onClick = state.onHome) {
            Icon(
                AppIcons.Home,
                contentDescription = strings.readerHome,
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
        IconButton(onClick = state.onSettings) {
            Icon(
                AppIcons.Settings,
                contentDescription = strings.readerSettings,
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/**
 * Einfacher toggle­barer Status-Fuß (Seite X / N) für Paged/Webtoon/Epub. Schwebt am
 * unteren Rand, Hintergrund über [readerOverlayScrim] (E-Ink deckend).
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
 * Dauerhafte, dünne **Info-Leiste** für den Roman-Reader — die geteilte Basis von
 * Page-Header und Page-Footer (DRY). Links [start], rechts [end], dazwischen Dehnung.
 * Eine Hairline-Trennlinie zur Inhaltsseite ([dividerOnTop] = Footer oben, Header unten).
 *
 * Ersetzt den engine-eigenen crengine-Streifen durch eine E-Ink-konforme, flache Leiste
 * (weiße Fläche, schwarze Schrift, Hairline statt Schatten). Auf E-Ink deckend, auf
 * Smartphone leicht durchscheinend.
 */
@Composable
fun BoxScope.ReaderInfoBar(
    align: Alignment,
    dividerOnTop: Boolean,
    start: @Composable () -> Unit,
    end: @Composable () -> Unit,
) {
    val divider = MaterialTheme.colorScheme.outlineVariant
    Column(Modifier.align(align).fillMaxWidth()) {
        if (dividerOnTop) Hairline(divider)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(readerOverlayScrim(Color.White, 0.85f))
                .displayCutoutPadding()
                .padding(horizontal = BAR_INSET, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // [start] füllt die Breite (linksbündig, kürzt mit …); [end] sitzt damit
            // bündig am rechten Rand — mit demselben Abstand wie [start] links (BAR_INSET).
            Box(Modifier.weight(1f)) { start() }
            end()
        }
        if (!dividerOnTop) Hairline(divider)
    }
}

/** Gemeinsamer horizontaler Rand-Abstand der Info-Leiste — links wie rechts identisch. */
private val BAR_INSET = 14.dp

@Composable
private fun Hairline(color: Color) {
    Box(Modifier.fillMaxWidth().height(EinkTokens.hairline).background(color))
}

/**
 * Schwarze Schrift auf der weißen Info-Leiste — gemeinsamer Stil für Header-/Footer-Slots.
 * `bodyMedium` + SemiBold: einen Tick größer/dicker als der Material-Default, damit der
 * dünne Page-Header/-Footer auf E-Ink gut lesbar bleibt.
 */
@Composable
fun ReaderInfoText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Color.Black,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/**
 * Aktuelle Uhrzeit als `HH:mm`, die sich minütlich aktualisiert (für den Page-Header
 * rechts, wie der frühere crengine-Streifen). Aktualisiert genau zum Minutenwechsel,
 * damit auf E-Ink höchstens ein Refresh pro Minute anfällt.
 */
@Composable
fun rememberClockText(): String {
    var now by remember { mutableStateOf(currentHourMinute()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = currentHourMinute()
            val secondsToNextMinute = (60 - LocalTime.now().second).coerceIn(1, 60)
            delay(secondsToNextMinute * 1000L)
        }
    }
    return now
}

private fun currentHourMinute(): String =
    LocalTime.now().let { "%02d:%02d".format(it.hour, it.minute) }

/**
 * Kompakte Tap-Zonen-Hints im eink-ui-Look (`komga-eink-ui`): je ein **kleiner Chip**
 * (weiße Fläche, schwarzer Rahmen, Pfeil über Label) mittig am linken/rechten Rand —
 * **nicht** das ganze Drittel überdeckend. Zeigt die Blätter-Richtung + Letzte/Nächste
 * Seite. Wird nur bei sichtbarem Chrome eingeblendet.
 *
 * **Nicht interaktiv** (kein `pointerInput`): die darunterliegenden Tap-Zonen des
 * [ReaderScaffold] bleiben aktiv — die Chips sind reine Orientierung.
 */
@Composable
fun BoxScope.ReaderTapZoneHints() {
    val strings = LocalStrings.current
    TapZoneChip(
        modifier = Modifier.align(Alignment.CenterStart).padding(start = 24.dp),
        icon = AppIcons.Back,
        label = strings.readerPrevPage,
    )
    TapZoneChip(
        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 24.dp),
        icon = AppIcons.Forward,
        label = strings.readerNextPage,
    )
}

@Composable
private fun TapZoneChip(modifier: Modifier, icon: ImageVector, label: String) {
    // Schwarz mit weißer Schrift wie das Top-Overlay, über [readerOverlayScrim]
    // geräteklassen-gegatet (E-Ink deckend, Smartphone durchscheinend).
    Column(
        modifier = modifier
            .background(readerOverlayScrim(Color.Black, 0.6f), RoundedCornerShape(EinkTokens.tileRadius))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(24.dp),
        )
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
        AnimatedVisibility(visible = visible, modifier = align, enter = fadeIn(), exit = fadeOut()) { pill() }
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
