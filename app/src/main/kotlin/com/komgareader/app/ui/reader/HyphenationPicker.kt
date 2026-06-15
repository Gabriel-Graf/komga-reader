package com.komgareader.app.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.ui.components.ChoiceRow
import com.komgareader.app.ui.components.EinkInfoDialog
import com.komgareader.app.ui.components.SegmentOption
import com.komgareader.app.ui.components.SegmentedChoiceRow
import com.komgareader.domain.render.HyphenationLanguages
import com.komgareader.domain.render.HyphenationMode
import com.komgareader.domain.render.hyphenationModeOf
import java.util.Locale

private const val LANGUAGE_KEY = "__language__"

/** Localized, capitalized display name for a hyphenation language code (e.g. "it" -> "Italienisch"). */
internal fun hyphenationLanguageName(code: String): String =
    Locale(code).getDisplayLanguage(Locale.getDefault())
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

/**
 * Shared hyphenation control: Automatic / Off chips plus a Language chip that opens a modal of all
 * supported languages. Used in both the settings screen and the in-reader typography panel
 * (shared-structure-before-variants). [value] is the stored setting ("auto" | "" | a language code).
 */
@Composable
fun HyphenationPicker(value: String, onValue: (String) -> Unit, query: String = "") {
    val strings = LocalStrings.current
    var modalOpen by remember { mutableStateOf(false) }
    val mode = hyphenationModeOf(value)
    val languageLabel =
        if (mode == HyphenationMode.LANGUAGE) hyphenationLanguageName(value) else strings.novelHyphenationLanguage

    SegmentedChoiceRow(
        label = strings.novelHyphenation,
        options = listOf(
            SegmentOption("auto", strings.novelHyphenationAuto),
            SegmentOption("", strings.novelHyphenationOff),
            SegmentOption(LANGUAGE_KEY, languageLabel),
        ),
        selectedKey = if (mode == HyphenationMode.LANGUAGE) LANGUAGE_KEY else value,
        onSelect = { key -> if (key == LANGUAGE_KEY) modalOpen = true else onValue(key) },
        query = query,
    )

    if (modalOpen) {
        HyphenationLanguageModal(
            current = value.takeIf { mode == HyphenationMode.LANGUAGE },
            onPick = { onValue(it); modalOpen = false },
            onDismiss = { modalOpen = false },
        )
    }
}

@Composable
private fun HyphenationLanguageModal(current: String?, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val strings = LocalStrings.current
    val items = remember {
        HyphenationLanguages.SUPPORTED
            .map { it to hyphenationLanguageName(it) }
            .sortedBy { it.second }
    }
    EinkInfoDialog(
        title = strings.hyphenationLanguageTitle,
        onDismiss = onDismiss,
        closeLabel = strings.close,
    ) {
        items.forEach { (code, name) ->
            ChoiceRow(label = name, selected = code == current, dense = true, onSelect = { onPick(code) })
        }
    }
}
