package com.komgareader.app.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.ui.components.EinkInfoDialog
import com.komgareader.app.ui.components.EinkOutlinedButton
import com.komgareader.app.ui.components.EinkSearchBar
import com.komgareader.app.ui.components.SectionHeader
import com.komgareader.app.ui.components.StepperRow
import com.komgareader.domain.render.SearchHit
import kotlinx.coroutines.launch

/**
 * Roman-Volltextsuche + „Gehe zu %" im Onyx-Look ([EinkInfoDialog] — ein Modal
 * über dem Reader, Hardware-Back/X schließt). Gepufferte Eingabe über die
 * [EinkSearchBar] (kein nacktes Material-Feld); Bestätigen führt [onSearch] in
 * einem Coroutine-Scope aus (off-main-thread im VM). Ein Tap auf einen Treffer
 * springt zu dessen Anker und schließt das Panel.
 *
 * Der diskrete „Gehe zu %"-Stepper (kein Slider — ruckelt auf E-Ink) springt an
 * die relative Position. **Keine Animation** (`animation-gating`); alle Texte über
 * [LocalStrings] (DE+EN).
 */
@Composable
fun NovelSearchPanel(
    onSearch: suspend (String) -> List<SearchHit>,
    onHitSelected: (String) -> Unit,
    onGoToProgress: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchHit>?>(null) }
    var percent by remember { mutableStateOf(0) }

    EinkInfoDialog(
        title = strings.novelSearch,
        onDismiss = onDismiss,
        closeLabel = strings.close,
    ) {
        // Gehe-zu-%: diskreter Stepper in 5-%-Schritten.
        SectionHeader(strings.novelGoToPercent)
        StepperRow(
            label = strings.novelGoToPercent,
            valueText = "$percent %",
            onDecrement = { percent = (percent - PERCENT_STEP).coerceAtLeast(0) },
            onIncrement = { percent = (percent + PERCENT_STEP).coerceAtMost(100) },
        )
        EinkOutlinedButton(
            onClick = {
                onGoToProgress(percent / 100f)
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(strings.novelGoToPercent) }

        SectionHeader(strings.novelSearch)
        EinkSearchBar(
            query = query,
            onQueryChange = { query = it },
            onSubmit = {
                scope.launch { results = onSearch(query) }
            },
            placeholder = strings.novelSearchPlaceholder,
            actionLabel = strings.novelSearch,
            modifier = Modifier.fillMaxWidth(),
            clearLabel = strings.close,
            onClear = {
                query = ""
                results = null
            },
        )

        SearchResults(
            results = results,
            onHitSelected = { anchor ->
                onHitSelected(anchor)
                onDismiss()
            },
        )
    }
}

@Composable
private fun SearchResults(
    results: List<SearchHit>?,
    onHitSelected: (String) -> Unit,
) {
    val strings = LocalStrings.current
    when {
        results == null -> Text(
            strings.novelSearchEmpty,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        results.isEmpty() -> Text(
            strings.novelSearchNoResults,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        else -> results.forEach { hit ->
            Text(
                text = hit.snippet,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = hit.anchor.isNotEmpty()) { onHitSelected(hit.anchor) }
                    .padding(vertical = 10.dp),
            )
        }
    }
}

private const val PERCENT_STEP = 5
