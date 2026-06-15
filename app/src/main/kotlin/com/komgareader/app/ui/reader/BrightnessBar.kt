package com.komgareader.app.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Maps a vertical drag fraction (0 = top, 1 = bottom) to a brightness value snapped to a discrete
 * step grid — E-Ink-friendly (each step is one partial refresh, no continuous animation).
 *
 * Top of bar (yFractionFromTop = 0) → max brightness (range.last).
 * Bottom of bar (yFractionFromTop = 1) → min brightness (range.first).
 */
fun brightnessForFraction(yFractionFromTop: Float, range: IntRange, steps: Int): Int {
    val fromBottom = (1f - yFractionFromTop).coerceIn(0f, 1f)
    val stepIndex = (fromBottom * steps).roundToInt().coerceIn(0, steps)
    val span = range.last - range.first
    return range.first + (span * stepIndex / steps)
}

/**
 * Host-rendered brightness bar anchored to a screen edge. Flat E-Ink look: 1.5dp border, no
 * shadow, no elevation, no animation — each drag step is a discrete level change (one partial
 * refresh on E-Ink). The fill rectangle grows from the bottom to indicate the current level.
 *
 * @param level    Current brightness value within [range].
 * @param range    Valid brightness range (e.g. 0..255 for screen brightness, 0..100 for frontlight).
 * @param alignment Where to anchor the bar (e.g. [Alignment.CenterStart] or [Alignment.CenterEnd]).
 * @param onLevel  Called with the snapped level while the user drags.
 * @param onDismiss Called when the user taps outside the bar.
 * @param steps    Number of discrete steps in the bar (default 16).
 */
@Composable
fun BrightnessBar(
    level: Int,
    range: IntRange,
    alignment: Alignment,
    onLevel: (Int) -> Unit,
    onDismiss: () -> Unit,
    steps: Int = 16,
) {
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) },
    ) {
        Box(
            Modifier
                .align(alignment)
                .width(56.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
                .border(1.5.dp, MaterialTheme.colorScheme.outline)
                .pointerInput(range) {
                    detectVerticalDragGestures { change, _ ->
                        val frac = change.position.y / size.height.toFloat()
                        onLevel(brightnessForFraction(frac, range, steps))
                    }
                },
        ) {
            val frac = if (range.last == range.first) 0f
                else (level - range.first).toFloat() / (range.last - range.first)
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(frac.coerceIn(0f, 1f))
                    .background(MaterialTheme.colorScheme.onSurface),
            )
        }
    }
}
