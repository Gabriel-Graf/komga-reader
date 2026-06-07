package com.komgareader.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/**
 * Schlanke Suchzeile im E-Ink-Stil: niedrige (40 dp) gerundete Pille mit
 * Hairline-Rahmen (wie die Tiles), Platzhalter und kompakter Lupe rechts zum
 * Bestätigen; Enter/IME-Search löst ebenfalls aus. Eigener BasicTextField statt
 * Material-OutlinedTextField, damit Höhe und Look zum Rest der UI passen.
 */
@Composable
fun EinkSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    placeholder: String,
    actionLabel: String,
    modifier: Modifier = Modifier,
    clearLabel: String? = null,
    onClear: (() -> Unit)? = null,
    leading: @Composable (RowScope.() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(20.dp)
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        modifier = modifier.height(40.dp),
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(MaterialTheme.colorScheme.surface, shape)
                    .border(1.5.dp, MaterialTheme.colorScheme.outline, shape)
                    .padding(start = if (leading != null) 10.dp else 14.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leading != null) {
                    leading()
                    Spacer(Modifier.width(6.dp))
                }
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                }
                if (onClear != null && query.isNotEmpty()) {
                    IconButton(onClick = onClear, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = clearLabel,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = onSubmit, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = actionLabel,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}
