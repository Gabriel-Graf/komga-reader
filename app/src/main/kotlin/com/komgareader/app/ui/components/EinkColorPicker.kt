package com.komgareader.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.ui.icons.AppIcons
import com.komgareader.ui.theme.EinkTokens

/**
 * Kaleido-legible palette for bookmark marker colours. Black is first (the default) so an
 * unconfigured bookmark reads as plain ink; the rest are distinct enough to tell apart even on a
 * desaturated colour-E-Ink panel. ARGB, fully opaque.
 */
private val BookmarkPalette = listOf(
    0xFF000000.toInt(), // black (default)
    0xFFD32F2F.toInt(), // red
    0xFF1565C0.toInt(), // blue
    0xFF2E7D32.toInt(), // green
    0xFFEF6C00.toInt(), // orange
    0xFF6A1B9A.toInt(), // purple
    0xFF00838F.toInt(), // teal
    0xFF5D4037.toInt(), // brown
)

/**
 * Colour picker for a bookmark marker — a fixed swatch [BookmarkPalette] plus a `#RRGGBB` custom
 * field with a live preview swatch. Built on [EinkModal] (black border, exactly one modal at a
 * time). The selected swatch carries a thick `outline` border + an [AppIcons.Check] tinted for
 * contrast against the swatch. Confirm applies the chosen ARGB Int via [onPick]; an invalid hex
 * disables confirm so a half-typed value can never be committed.
 *
 * The chosen colour is *content* colour (drawn over the page), independent of the UI accent — so
 * it is intentionally not gated behind `allowsAccentColor`. No animation: selection is an instant
 * state swap.
 */
@Composable
fun EinkColorPicker(
    title: String,
    initial: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    var selected by remember { mutableIntStateOf(initial or 0xFF000000.toInt()) }
    var hexText by remember { mutableStateOf(toHex(initial)) }

    val parsed = parseHex(hexText)
    val effective = parsed ?: selected

    EinkModal(
        title = title,
        onDismiss = onDismiss,
        confirmLabel = strings.save,
        onConfirm = { onPick(effective) },
        dismissLabel = strings.cancel,
        confirmEnabled = parsed != null || hexText.isBlank(),
    ) {
        // Swatch grid: a wrapping set of fixed-size cells. Two rows of four keeps the modal a
        // stable size (no reflow on open).
        BookmarkPalette.chunked(4).forEach { rowColours ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowColours.forEach { argb ->
                    Swatch(
                        color = Color(argb),
                        selected = effective == argb,
                        onClick = {
                            selected = argb
                            hexText = toHex(argb)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.size(10.dp))
        }

        // Custom hex field + live preview of the parsed colour.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            EinkTextField(
                value = hexText,
                onValueChange = { hexText = normalizeHexInput(it) },
                label = strings.novelBookmarkColorCustom,
                placeholder = "#RRGGBB",
                keyboardType = KeyboardType.Ascii,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier
                    .size(40.dp)
                    .border(EinkTokens.hairline, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                    .background(Color(effective), RoundedCornerShape(6.dp)),
            )
        }
    }
}

/** One palette cell: filled with [color], hairline by default, thick [outline] + check when selected. */
@Composable
private fun Swatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            // Square cell that fills its weighted column slot — uniform, gap-aligned swatches that
            // open in a stable size (no reflow on E-Ink).
            .aspectRatio(1f)
            .border(
                width = if (selected) EinkTokens.strongBorder else EinkTokens.hairline,
                color = if (selected) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(6.dp),
            )
            .background(color, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                AppIcons.Check,
                contentDescription = toHex(color.toArgb()),
                modifier = Modifier.size(22.dp),
                // Contrast against the swatch: white check on dark fills, black on light.
                tint = if (color.luminance() < 0.5f) Color.White else Color.Black,
            )
        }
    }
}

/** Format an ARGB Int as an opaque `#RRGGBB` string (alpha dropped — bookmark colours are opaque). */
private fun toHex(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

/** Keep only `#` + hex digits, upper-cased, capped at `#RRGGBB` length, leading `#` enforced. */
private fun normalizeHexInput(raw: String): String {
    val digits = raw.removePrefix("#").filter { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
    return "#" + digits.uppercase().take(6)
}

/** Parse a complete `#RRGGBB` string to an opaque ARGB Int, or null if not yet complete/valid. */
private fun parseHex(text: String): Int? {
    val digits = text.removePrefix("#")
    if (digits.length != 6) return null
    val rgb = digits.toIntOrNull(16) ?: return null
    return rgb or 0xFF000000.toInt()
}

/** Perceptual-ish luminance for picking a contrasting check tint. */
private fun Color.luminance(): Float = 0.299f * red + 0.587f * green + 0.114f * blue
