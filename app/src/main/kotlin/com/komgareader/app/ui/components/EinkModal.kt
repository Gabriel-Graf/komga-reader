package com.komgareader.app.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.komgareader.app.ui.icons.AppIcons
import com.komgareader.app.ui.theme.EinkTokens

/**
 * Modal im Onyx-Look: **immer schwarzer Rand** (strongBorder/outline), weiße Surface,
 * großer Radius. Ersetzt das nackte Material `AlertDialog`. Titel oben, Inhalt mittig,
 * Aktionen unten, voll-breit geteilt (Abbrechen links, Bestätigen rechts). Genau ein Modal gleichzeitig.
 */
@Composable
fun EinkModal(
    title: String,
    onDismiss: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String,
    modifier: Modifier = Modifier,
    confirmEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .border(
                    EinkTokens.strongBorder,
                    MaterialTheme.colorScheme.outline,
                    MaterialTheme.shapes.large,
                ),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                content()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EinkOutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text(dismissLabel) }
                    Button(onClick = onConfirm, enabled = confirmEnabled, modifier = Modifier.weight(1f)) { Text(confirmLabel) }
                }
            }
        }
    }
}

/**
 * Read-only-Modal im Onyx-Look: Titel links, **nur ein X oben rechts** zum Schließen,
 * darunter der Inhalt. Für reine Infos (z. B. Preset-Werte anzeigen) ohne Aktion.
 */
@Composable
fun EinkInfoDialog(
    title: String,
    onDismiss: () -> Unit,
    closeLabel: String,
    modifier: Modifier = Modifier,
    contentSpacing: Dp = 12.dp,
    widthFraction: Float = 0.6f,
    content: @Composable ColumnScope.() -> Unit,
) {
    // usePlatformDefaultWidth=false + schmale Surface → kompaktes, inhaltsgerechtes Modal
    // (der Default-Dialog wäre für die wenigen Werte unnötig breit).
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = modifier
                .fillMaxWidth(widthFraction)
                .border(
                    EinkTokens.strongBorder,
                    MaterialTheme.colorScheme.outline,
                    MaterialTheme.shapes.large,
                ),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.padding(20.dp)) {
                // Sticky Header: Titel links, X rechts.
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(AppIcons.Close, contentDescription = closeLabel, modifier = Modifier.size(22.dp))
                    }
                }
                // Scrollender Body mit Scroll-Affordance: lange Inhalte (Typo/TOC/Suche)
                // bleiben erreichbar; rechts signalisiert ein Indikator die Scrollrichtung.
                val scrollState = rememberScrollState()
                Box(Modifier.heightIn(max = 560.dp).padding(top = 12.dp)) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(scrollState)
                            // Platz rechts für den Scroll-Indikator reservieren.
                            .padding(end = 22.dp),
                        verticalArrangement = Arrangement.spacedBy(contentSpacing),
                        content = content,
                    )
                    ScrollAffordance(scrollState)
                }
            }
        }
    }
}

/**
 * Scroll-Richtungs-Anzeige am rechten Rand des Dialog-Bodys, über [LocalEinkMode] gegatet:
 * **E-Ink** zeigt statische Chevrons (oben ▲ wenn nach oben scrollbar, unten ▼ wenn nach
 * unten scrollbar — am Anfang nur ▼, am Ende nur ▲, mittig beide). **Smartphone** zeigt
 * eine schlanke Custom-Scrollbar. Nichts, wenn der Inhalt komplett passt.
 */
@Composable
private fun BoxScope.ScrollAffordance(scrollState: ScrollState) {
    if (scrollState.maxValue == 0) return
    // Indikatoren ragen in das Dialog-Padding hinein → näher am rechten Rand.
    val toEdge = Modifier.offset(x = 12.dp)
    if (LocalEinkMode.current) {
        if (scrollState.value > 0) {
            Icon(
                AppIcons.ChevronUp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.TopEnd).then(toEdge).size(22.dp),
            )
        }
        if (scrollState.value < scrollState.maxValue) {
            Icon(
                AppIcons.ChevronDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.BottomEnd).then(toEdge).size(22.dp),
            )
        }
    } else {
        CustomScrollbar(scrollState, Modifier.align(Alignment.CenterEnd).then(toEdge).fillMaxHeight())
    }
}

/** Schlanke Custom-Scrollbar (Smartphone): Thumb proportional zur Sichtbarkeit, kein Stock-Control. */
@Composable
private fun CustomScrollbar(scrollState: ScrollState, modifier: Modifier) {
    BoxWithConstraints(modifier) {
        val viewportPx = constraints.maxHeight
        val contentPx = viewportPx + scrollState.maxValue
        if (contentPx <= viewportPx) return@BoxWithConstraints
        val thumbHeight = maxHeight * (viewportPx.toFloat() / contentPx.toFloat())
        val posFraction = scrollState.value.toFloat() / scrollState.maxValue
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(y = (maxHeight - thumbHeight) * posFraction)
                .width(4.dp)
                .height(thumbHeight)
                .background(MaterialTheme.colorScheme.onSurface, RoundedCornerShape(2.dp)),
        )
    }
}
