package com.komgareader.app.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.komgareader.app.ui.components.LocalEinkMode
import com.komgareader.ui.slots.ReaderBottomSheet
import com.komgareader.ui.theme.EinkTokens

/**
 * Host renderer of the optional [ReaderBottomSheet] capability of the `readerChrome` region. Lives
 * in ONE place (like the tap-zone layer + frontlight strips) so every reader that wants a sheet gets
 * the same mechanics (shared-structure-before-variants). Three parts:
 *
 * 1. Bottom-edge swipe (always, even immersive): full bottom-edge strip; only an upward vertical drag
 *    opens it, so left/center/right page taps still pass (mirrors the frontlight strips consuming
 *    only horizontal drag).
 * 2. Peek bar (chrome visible & collapsed): a thin grabber + label; tap or upward drag expands it —
 *    the no-swipe entry.
 * 3. Expanded sheet + scrim (expanded): a full-width, bottom-anchored, flat container.
 *
 * E-Ink host-enforced ([LocalEinkMode]): instant open/close on E-Ink, slide + fade only on phone
 * (animation-gating). Scrim opaque on E-Ink via [readerOverlayScrim].
 */
@Composable
fun BoxScope.ReaderBottomSheetLayer(sheet: ReaderBottomSheet, chromeVisible: Boolean) {
    val eink = LocalEinkMode.current

    // 1. Bottom-edge swipe-up detector (collapsed only; works even when chrome is hidden).
    if (!sheet.expanded) {
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(24.dp)
                .pointerInput(Unit) {
                    detectVerticalDragGestures { change, dragAmount ->
                        if (dragAmount < 0f) { // upward
                            sheet.onExpandedChange(true)
                            change.consume()
                        }
                    }
                },
        )
    }

    // 2. Peek bar — chrome visible & collapsed.
    if (chromeVisible && !sheet.expanded) {
        ReaderBottomSheetPeek(
            label = sheet.peekLabel,
            onExpand = { sheet.onExpandedChange(true) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    // 3. Expanded sheet + scrim.
    if (eink) {
        if (sheet.expanded) {
            Scrim(onTap = { sheet.onExpandedChange(false) })
            ReaderBottomSheetExpanded(
                sheet = sheet,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    } else {
        AnimatedVisibility(visible = sheet.expanded, enter = fadeIn(), exit = fadeOut()) {
            Scrim(onTap = { sheet.onExpandedChange(false) })
        }
        AnimatedVisibility(
            visible = sheet.expanded,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            ReaderBottomSheetExpanded(sheet = sheet, modifier = Modifier)
        }
    }
}

// Plain composable (NOT a BoxScope extension): it is also called inside AnimatedVisibility's content
// lambda (AnimatedVisibilityScope), where a BoxScope receiver would not resolve. It uses fillMaxSize
// only — no align — so no BoxScope is needed.
@Composable
private fun Scrim(onTap: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(readerOverlayScrim(Color.Black, 0.45f))
            .pointerInput(Unit) { detectTapGestures { onTap() } },
    )
}

@Composable
private fun ReaderBottomSheetPeek(label: String, onExpand: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .border(EinkTokens.hairline, MaterialTheme.colorScheme.outline)
            .clickable(onClick = onExpand)
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, dragAmount ->
                    if (dragAmount < 0f) { onExpand(); change.consume() }
                }
            }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(width = 32.dp, height = 3.dp).background(MaterialTheme.colorScheme.outline))
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ReaderBottomSheetExpanded(sheet: ReaderBottomSheet, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val cap = maxHeight * 0.55f
        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .border(EinkTokens.hairline, MaterialTheme.colorScheme.outline),
        ) {
            // Grabber handle row — drag down to dismiss (the scrollable body does not).
            Column(
                Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { change, dragAmount ->
                            if (dragAmount > 0f) { sheet.onExpandedChange(false); change.consume() }
                        }
                    }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(width = 32.dp, height = 3.dp).background(MaterialTheme.colorScheme.outline))
            }
            // Scrollable content, height-capped (mirrors EinkInfoDialog's Box(heightIn)+verticalScroll).
            Box(Modifier.heightIn(max = cap)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    sheet.content()
                }
            }
        }
    }
}
