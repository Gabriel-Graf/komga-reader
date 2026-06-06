package com.komgareader.app.ui.components

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import com.komgareader.app.ui.settings.matchRanges

/**
 * Text, der die Treffer von [query] markiert: fett + `outlineVariant`-Hintergrund —
 * monochrom, E-Ink-konform (keine Akzentfarbe). Ohne Treffer = normaler Text.
 */
@Composable
fun HighlightText(
    text: String,
    query: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
) {
    val ranges = matchRanges(text, query)
    if (ranges.isEmpty()) {
        Text(text, modifier = modifier, style = style, color = color)
        return
    }
    val markBg = MaterialTheme.colorScheme.outlineVariant
    val annotated: AnnotatedString = buildAnnotatedString {
        append(text)
        ranges.forEach { r ->
            addStyle(SpanStyle(fontWeight = FontWeight.Bold, background = markBg), r.first, r.last + 1)
        }
    }
    Text(annotated, modifier = modifier, style = style, color = color)
}
