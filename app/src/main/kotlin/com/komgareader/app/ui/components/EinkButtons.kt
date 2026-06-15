package com.komgareader.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.komgareader.ui.theme.EinkTokens
import com.komgareader.ui.theme.LocalDesignTokens

/**
 * Outlined-Button im E-Ink-Look: Rand = [EinkTokens.hairline] (1.5dp, schwarz/outline) statt
 * Material-Default (≈1dp, auf E-Ink kaum sichtbar). Sorgt für **einheitliche Linienstärke**
 * über die ganze App — jeder Outlined-Button nutzt diesen Wrapper, kein nacktes [OutlinedButton].
 *
 * **Press-Feedback (E-Ink):** der Material-Ripple ist auf E-Ink praktisch unsichtbar, also kehrt
 * der Button **solange er gedrückt ist** Grund/Schrift um (Grund = Mono-Akzent, Schrift/Icon =
 * onAccent — schwarz/weiß). So sieht der Nutzer den Tap sofort als sauberen, sofortigen
 * Zustandswechsel (kein Bewegungs-Schnickschnack, `animation-gating`/`eink-design-language`).
 */
@Composable
fun EinkOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    val tokens = LocalDesignTokens.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        contentPadding = contentPadding,
        interactionSource = interaction,
        border = BorderStroke(EinkTokens.hairline, MaterialTheme.colorScheme.outline),
        colors = if (pressed) {
            ButtonDefaults.outlinedButtonColors(
                containerColor = tokens.accent,
                contentColor = tokens.onAccent,
            )
        } else {
            ButtonDefaults.outlinedButtonColors()
        },
        content = content,
    )
}
