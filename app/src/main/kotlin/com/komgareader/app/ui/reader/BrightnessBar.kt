package com.komgareader.app.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.komgareader.ui.theme.EinkTokens
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

/** Width of the floating bar. */
private val BAR_WIDTH = 44.dp

/** Fraction of the screen height the bar spans — short, floating, not full-height. */
private const val BAR_HEIGHT_FRACTION = 0.5f

/** Inset from the screen edge so the bar floats rather than sticking to the border. */
private val BAR_EDGE_INSET = 20.dp

/**
 * Host-rendered frontlight bar: a short, rounded, **floating** pill inset from the screen edge.
 * Flat E-Ink look — [EinkTokens.hairline] border, no shadow, no elevation, no animation; each drag
 * step is one discrete level change (one partial refresh on E-Ink). The fill grows from the bottom
 * to show the current level, clipped to the rounded shape.
 *
 * @param level     Current brightness value within [range].
 * @param range     Valid brightness range (device index space; see [com.komgareader.domain.eink.EinkCapabilities.brightnessRange]).
 * @param alignment Which edge to float against ([Alignment.CenterStart] or [Alignment.CenterEnd]).
 * @param onLevel   Called with the snapped level while the user drags.
 * @param onDismiss Called when the user taps outside the bar.
 * @param steps     Number of discrete steps the drag snaps to (default 16).
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
    val shape = RoundedCornerShape(BAR_WIDTH / 2) // pill: fully rounded ends
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) },
    ) {
        Box(
            Modifier
                .align(alignment)
                .padding(horizontal = BAR_EDGE_INSET)
                .width(BAR_WIDTH)
                .fillMaxHeight(BAR_HEIGHT_FRACTION)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .border(EinkTokens.hairline, MaterialTheme.colorScheme.outline, shape)
                .pointerInput(range) {
                    detectVerticalDragGestures { change, _ ->
                        val frac = change.position.y / size.height.toFloat()
                        onLevel(brightnessForFraction(frac, range, steps))
                    }
                },
        ) {
            val frac = if (range.last == range.first) 0f
                else (level - range.first).toFloat() / (range.last - range.first)
            // Fill is clipped to the parent pill shape, so it reads as a filled portion of the pill.
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
