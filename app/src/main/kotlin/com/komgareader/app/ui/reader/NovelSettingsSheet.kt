package com.komgareader.app.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign as UiTextAlign
import androidx.compose.ui.unit.dp
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.domain.model.NovelBookmark
import com.komgareader.domain.render.Hyphenation
import com.komgareader.domain.render.NovelFont
import com.komgareader.domain.render.NovelFonts
import com.komgareader.domain.render.NovelSettings
import com.komgareader.domain.render.ReflowConfig
import com.komgareader.domain.render.TextAlign
import com.komgareader.ui.theme.EinkTokens
import com.komgareader.ui.theme.LocalDesignTokens
import java.io.File

/** Which tab of the novel settings bottom sheet is active. Novel-local; the host does not see tabs. */
enum class NovelSheetTab { TYPOGRAPHY, BOOKMARKS }

/**
 * Content of the novel settings bottom sheet: two tabs [Typography | Bookmarks]. Stateless — values
 * in, callbacks out; the caller (screen) owns the `expanded` state + the close action. Both tabs reuse
 * the already-shared building blocks ([NovelTypographyControls] / [NovelBookmarkList]) — no dialog
 * frame (shared-structure-before-variants). The TOC moved out to a chrome button ([NovelTocPanel]).
 * No animation (animation-gating).
 */
@Composable
fun NovelSettingsSheet(
    selectedTab: NovelSheetTab,
    onTabChange: (NovelSheetTab) -> Unit,
    config: ReflowConfig,
    onFontSizeEm: (Float) -> Unit,
    onLineHeight: (Float) -> Unit,
    onFontWeight: (Int) -> Unit,
    onMargin: (String) -> Unit,
    onTextAlign: (String) -> Unit,
    onHyphenation: (String) -> Unit,
    onFontFamily: (String) -> Unit,
    bookmarks: List<NovelBookmark>,
    onBookmarkJump: (String) -> Unit,
    onBookmarkRename: (Long) -> Unit,
    onBookmarkDelete: (Long) -> Unit,
    onBookmarkJumped: () -> Unit,
    availableFonts: List<NovelFont> = NovelFonts.ALL,
    fontFiles: Map<String, File> = emptyMap(),
) {
    val strings = LocalStrings.current

    // Pinned tab row + independently scrolling body so the [Typography | Bookmarks] header stays
    // fixed at the top of the (fixed-height) sheet while the tab content scrolls (request 2026-06-16).
    Column(Modifier.fillMaxSize()) {
        SheetTabRow(
            tabs = listOf(
                NovelSheetTab.TYPOGRAPHY to strings.novelTypography,
                NovelSheetTab.BOOKMARKS to strings.novelBookmarks,
            ),
            selected = selectedTab,
            onSelect = onTabChange,
        )
        Box(Modifier.fillMaxWidth().weight(1f)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                when (selectedTab) {
                    NovelSheetTab.TYPOGRAPHY -> NovelTypographyControls(
                        fontSizeEm = config.fontSizeEm,
                        onFontSize = onFontSizeEm,
                        lineHeight = config.lineHeight,
                        onLineHeight = onLineHeight,
                        fontWeight = config.fontWeight,
                        onFontWeight = onFontWeight,
                        marginPreset = config.marginPreset(),
                        onMargin = onMargin,
                        textAlign = if (config.textAlign == TextAlign.LEFT) "LEFT" else "JUSTIFY",
                        onTextAlign = onTextAlign,
                        hyphenationLang = config.hyphenationLang(),
                        onHyphenation = onHyphenation,
                        fontFamily = config.fontFamily,
                        onFontFamily = onFontFamily,
                        availableFonts = availableFonts,
                        fontFiles = fontFiles,
                    )
                    NovelSheetTab.BOOKMARKS -> NovelBookmarkList(
                        bookmarks = bookmarks,
                        onJump = onBookmarkJump,
                        onRename = onBookmarkRename,
                        onDelete = onBookmarkDelete,
                        onJumped = onBookmarkJumped,
                    )
                }
            }
        }
    }
}

/**
 * Flat, monochrome 2-segment tab row in the Onyx look: one outer border, the active tab fills with
 * the mono accent ([LocalDesignTokens]); the others are transparent. No animation — instant switch.
 */
@Composable
private fun SheetTabRow(
    tabs: List<Pair<NovelSheetTab, String>>,
    selected: NovelSheetTab,
    onSelect: (NovelSheetTab) -> Unit,
) {
    val tokens = LocalDesignTokens.current
    Row(
        Modifier
            .fillMaxWidth()
            .border(EinkTokens.hairline, MaterialTheme.colorScheme.outline),
    ) {
        tabs.forEach { (tab, label) ->
            val isSelected = tab == selected
            Box(
                Modifier
                    .weight(1f)
                    .selectable(selected = isSelected, onClick = { onSelect(tab) })
                    .background(if (isSelected) tokens.accent else MaterialTheme.colorScheme.surface)
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) tokens.onAccent else MaterialTheme.colorScheme.onSurface,
                    textAlign = UiTextAlign.Center,
                )
            }
        }
    }
}

/** Reverse-map: concrete [ReflowConfig] margins -> the preset string the controls expect. */
private fun ReflowConfig.marginPreset(): String = when (margin) {
    NovelSettings.marginFor(NovelSettings.MARGIN_NARROW) -> NovelSettings.MARGIN_NARROW
    NovelSettings.marginFor(NovelSettings.MARGIN_WIDE) -> NovelSettings.MARGIN_WIDE
    else -> NovelSettings.MARGIN_NORMAL
}

/** Reverse-map: [Hyphenation] -> language code string ("" = off) the controls expect. */
private fun ReflowConfig.hyphenationLang(): String = when (val h = hyphenation) {
    is Hyphenation.Language -> h.lang
    Hyphenation.Off -> ""
}
