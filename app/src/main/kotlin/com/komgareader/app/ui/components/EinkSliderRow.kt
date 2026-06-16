package com.komgareader.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.komgareader.ui.icons.AppIcons
import com.komgareader.ui.theme.EinkTokens
import com.komgareader.ui.theme.LocalDesignTokens
import kotlin.math.roundToInt

/** A named tick on an [EinkSliderRow] track — drawn taller with a small label beneath. */
data class SliderLandmark(val index: Int, val label: String)

/**
 * Discrete notched slider for an integer position over `0..stepCount` — the E-Ink-safe
 * replacement for a continuous Material `Slider` (which drags/flings and ghosts on E-Ink).
 *
 * Layout: [label] left, then a row of [−][notched track][+], the current [valueText] on the
 * right of the control row, and (optionally) [landmarks] as taller ticks with `bodySmall`
 * labels centred under the track. The position marker (filled thumb) and the filled portion of
 * the track use `LocalDesignTokens.accent` (mono = black, Kaleido/LCD = colour) — the same
 * single selection signal as the rest of the app, behind `allowsAccentColor`. Never coupled to
 * the motion axis, never hardcoded.
 *
 * Interaction is **discrete only** (no drag/fling): −/+ step the position by one (clamped to
 * `0..stepCount`); tapping the track snaps to the nearest notch. No animation — every change is
 * an instant state swap (`animation-gating`/`eink-design-language`).
 */
@Composable
fun EinkSliderRow(
    label: String,
    valueText: String,
    position: Int,
    stepCount: Int,
    onPosition: (Int) -> Unit,
    modifier: Modifier = Modifier,
    landmarks: List<SliderLandmark> = emptyList(),
) {
    val accent = LocalDesignTokens.current.accent
    val trackColor = MaterialTheme.colorScheme.outlineVariant
    val clamped = position.coerceIn(0, stepCount)

    Column(modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f).padding(end = 12.dp),
            )
            CompactIconButton(AppIcons.Minus, "−") {
                if (clamped > 0) onPosition(clamped - 1)
            }
            NotchedTrack(
                position = clamped,
                stepCount = stepCount,
                landmarks = landmarks,
                accent = accent,
                trackColor = trackColor,
                onSnap = { onPosition(it) },
                modifier = Modifier
                    .weight(2f)
                    .height(36.dp)
                    .padding(horizontal = 6.dp),
            )
            CompactIconButton(AppIcons.Plus, "+") {
                if (clamped < stepCount) onPosition(clamped + 1)
            }
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                // Fixed width so rows with different digit counts stay vertically aligned.
                modifier = Modifier.padding(start = 8.dp).width(56.dp),
            )
        }
        // No text label row under the track: it read as a big empty gap before the next control
        // (request 2026-06-16). The landmark TICKS on the track still mark the named stops, and the
        // current step's name shows in [valueText] on the right as you move — so the labels were
        // redundant. The slider is now the same height as the unlabelled ones above/below it.
    }
}

/**
 * The discrete track: `stepCount + 1` notches, a filled lead-in to [position], a tick at every
 * notch (taller at a landmark) and a filled thumb at [position]. Tapping snaps to the nearest
 * notch via [onSnap]. Pure draw + a single tap gesture — no drag, no animation.
 */
@Composable
private fun NotchedTrack(
    position: Int,
    stepCount: Int,
    landmarks: List<SliderLandmark>,
    accent: Color,
    trackColor: Color,
    onSnap: (Int) -> Unit,
    modifier: Modifier,
) {
    val landmarkIndices = landmarks.map { it.index }.toSet()
    Box(
        modifier
            .pointerInput(stepCount) {
                detectTapGestures { tap ->
                    if (stepCount <= 0) return@detectTapGestures
                    val fraction = (tap.x / size.width).coerceIn(0f, 1f)
                    onSnap((fraction * stepCount).roundToInt().coerceIn(0, stepCount))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxWidth().height(36.dp)) {
            if (stepCount <= 0) return@Canvas
            val midY = size.height / 2f
            val lineWidth = EinkTokens.hairline.toPx()
            val step = size.width / stepCount
            val thumbX = step * position

            // Base track (full width) then accent-filled lead-in up to the thumb.
            drawLine(trackColor, Offset(0f, midY), Offset(size.width, midY), lineWidth)
            drawLine(accent, Offset(0f, midY), Offset(thumbX, midY), lineWidth * 2f)

            // Notch ticks: taller + accent at landmarks, short + neutral otherwise.
            for (i in 0..stepCount) {
                val x = step * i
                val isLandmark = i in landmarkIndices
                val half = if (isLandmark) size.height * 0.34f else size.height * 0.20f
                drawLine(
                    color = if (isLandmark) accent else trackColor,
                    start = Offset(x, midY - half),
                    end = Offset(x, midY + half),
                    strokeWidth = if (isLandmark) lineWidth * 1.5f else lineWidth,
                )
            }

            // Filled thumb (mono accent) at the current position.
            drawCircle(accent, radius = size.height * 0.26f, center = Offset(thumbX, midY))
        }
    }
}

