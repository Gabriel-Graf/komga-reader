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
import com.komgareader.app.ui.components.EinkSearchBar
import com.komgareader.ui.icons.AppIcons
import com.komgareader.domain.render.SearchHit
import kotlinx.coroutines.launch

/** Ziel des „Springe zu"-Feldes: eine 1-basierte interne Seite oder ein Prozentwert. */
internal sealed interface GoTarget {
    data class Page(val page: Int) : GoTarget
    data class Percent(val percent: Int) : GoTarget
}

/**
 * Parst die „Springe zu"-Eingabe: endet sie auf `%`, ist es ein Prozentwert (0–100),
 * sonst eine **interne** Seitenzahl (1..[pageCount], geclamped). Ungültig → `null`.
 * Reine Funktion (unit-testbar).
 */
internal fun parseGoTo(input: String, pageCount: Int): GoTarget? {
    val text = input.trim()
    if (text.isEmpty()) return null
    if (text.endsWith("%")) {
        val value = text.dropLast(1).trim().toIntOrNull() ?: return null
        return GoTarget.Percent(value.coerceIn(0, 100))
    }
    val page = text.toIntOrNull() ?: return null
    return GoTarget.Page(page.coerceIn(1, pageCount.coerceAtLeast(1)))
}

/**
 * Roman-Volltextsuche + „Springe zu" ([EinkInfoDialog]). **Suchleiste ganz oben**;
 * darunter ein kompaktes Feld, in das man eine **interne Seitenzahl oder einen Prozentwert**
 * tippt (springt auf die formatierten Reflow-Seiten, nicht die Komga-Rohseiten). Kein
 * „Suchbegriff eingeben"-Hinweis und kein überdimensionierter Button — beide Eingaben sind
 * gleich große Pillen mit kompaktem Bestätigen-Icon.
 *
 * **Keine Animation** (`animation-gating`); Texte über [LocalStrings] (DE+EN).
 */
@Composable
fun NovelSearchPanel(
    pageCount: Int,
    onSearch: suspend (String) -> List<SearchHit>,
    onHitSelected: (String) -> Unit,
    onGoToPage: (Int) -> Unit,
    onGoToProgress: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchHit>?>(null) }
    var goTo by remember { mutableStateOf("") }

    fun jump() {
        when (val target = parseGoTo(goTo, pageCount)) {
            is GoTarget.Page -> {
                onGoToPage(target.page - 1)
                onDismiss()
            }
            is GoTarget.Percent -> {
                onGoToProgress(target.percent / 100f)
                onDismiss()
            }
            null -> Unit
        }
    }

    EinkInfoDialog(
        title = strings.novelSearch,
        onDismiss = onDismiss,
        closeLabel = strings.close,
        contentSpacing = 8.dp,
    ) {
        EinkSearchBar(
            query = query,
            onQueryChange = { query = it },
            onSubmit = { scope.launch { results = onSearch(query) } },
            placeholder = strings.novelSearchPlaceholder,
            actionLabel = strings.novelSearch,
            modifier = Modifier.fillMaxWidth(),
            clearLabel = strings.close,
            onClear = {
                query = ""
                results = null
            },
        )
        EinkSearchBar(
            query = goTo,
            onQueryChange = { goTo = it },
            onSubmit = { jump() },
            placeholder = strings.novelGoToPlaceholder,
            actionLabel = strings.novelGoToAction,
            actionIcon = AppIcons.Forward,
            modifier = Modifier.fillMaxWidth(),
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
        // Vor der ersten Suche: nichts anzeigen (kein „Suchbegriff eingeben"-Fülltext).
        results == null -> Unit
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
