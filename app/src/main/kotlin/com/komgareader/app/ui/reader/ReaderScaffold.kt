package com.komgareader.app.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import com.komgareader.app.ui.icons.AppIcons

/**
 * Geteiltes Reader-Gerüst: kapselt die über alle Reader identische Chrome-Mechanik —
 * den schwarzen Vollbild-Hintergrund, die Drittel-Tap-Zonen (links → [onPrev],
 * rechts → [onNext], Mitte → `chrome.toggleChrome()`), das durchscheinende
 * [ReaderChromeOverlay] und einen optionalen Status-Fuß ([footer]).
 *
 * Das gehört nach `shared-structure-before-variants` an genau **eine** Stelle, bevor der
 * vierte Reader (NOVEL) dazukommt: Paged/Epub bauen vollständig darauf, der NOVEL-Reader
 * baut darauf auf statt als Parallel-Linie. Reader mit bespoke Gesten (Comic: Panel-
 * Hit-Test/Zoom; Webtoon: E-Ink-gegatete Frame-Sprünge) liefern ihren eigenen Tap-Handler
 * über [tapModifier] und nutzen das Scaffold nur für Overlay/Footer.
 *
 * **Keine Animation:** Das Overlay erscheint/verschwindet sofort (kein Fade/Slide), exakt
 * wie der bisherige `if (visible) …`-Pfad — konform zu `animation-gating` (E-Ink).
 */
@Composable
fun ReaderScaffold(
    chrome: ReaderChromeState,
    title: String,
    onBack: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = Color.Black,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    tapModifier: Modifier? = null,
    footer: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val chromeVisible by chrome.chromeVisible.collectAsState()

    Box(modifier.fillMaxSize().background(background)) {
        content()

        // Tap-Zonen: standardmäßig Drittel-Navigation; bespoke Reader liefern eigene.
        val taps = tapModifier ?: Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val width = size.width.toFloat()
                    when {
                        offset.x < width / 3f -> onPrev()
                        offset.x > width * 2f / 3f -> onNext()
                        else -> chrome.toggleChrome()
                    }
                }
            }
        Box(taps)

        ReaderChromeOverlay(
            visible = chromeVisible,
            title = title,
            onBack = onBack,
            actions = actions,
        )

        if (chromeVisible && footer != null) {
            footer()
        }
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
