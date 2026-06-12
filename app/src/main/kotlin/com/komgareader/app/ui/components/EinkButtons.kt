package com.komgareader.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.komgareader.ui.theme.EinkTokens

/**
 * Outlined-Button im E-Ink-Look: Rand = [EinkTokens.hairline] (1.5dp, schwarz/outline) statt
 * Material-Default (≈1dp, auf E-Ink kaum sichtbar). Sorgt für **einheitliche Linienstärke**
 * über die ganze App — jeder Outlined-Button nutzt diesen Wrapper, kein nacktes [OutlinedButton].
 */
@Composable
fun EinkOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        contentPadding = contentPadding,
        border = BorderStroke(EinkTokens.hairline, MaterialTheme.colorScheme.outline),
        content = content,
    )
}
