package com.komgareader.app.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
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
 * in, callbacks out; the caller (screen) owns the `expanded` state + the close action. The TYPOGRAPHY
 * tab reuses the already-shared [NovelTypographyControls] inside a scrolling column; the BOOKMARKS
 * tab hosts the full-height [NovelBookmarkPanel] (it owns its own pinned header + scroll, so it is NOT
 * wrapped in the shared scroll). No dialog frame (shared-structure-before-variants). The TOC moved out
 * to a chrome button ([NovelTocPanel]). No animation (animation-gating).
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
    defaultMarkerStyle: String,
    onDefaultMarkerStyle: (String) -> Unit,
    onBookmarkJump: (String) -> Unit,
    onBookmarkRename: (Long) -> Unit,
    onBookmarkDelete: (Long) -> Unit,
    onBookmarkPickColor: (List<Long>) -> Unit,
    onBookmarkApplyMode: (List<Long>, String) -> Unit,
    onBookmarkDeleteMany: (List<Long>) -> Unit,
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
            when (selectedTab) {
                // Typography: the shared scroll wraps the controls.
                NovelSheetTab.TYPOGRAPHY -> Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    NovelTypographyControls(
                        fontSizeEm = config.fontSizeEm,
                        onFontSize = onFontSizeEm,
                        lineHeight = config.lineHeight,
                        onLineHeight = onLineHeight,
                        fontWeight = config.fontWeight,
                        onFontWeight = onFontWeight,
                        marginPreset = NovelSettings.presetForMargin(config.margin),
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
                }
                // Bookmarks: the panel owns its pinned header + its own scroll — fill the sheet.
                NovelSheetTab.BOOKMARKS -> NovelBookmarkPanel(
                    bookmarks = bookmarks,
                    defaultMarkerStyle = defaultMarkerStyle,
                    onDefaultMarkerStyle = onDefaultMarkerStyle,
                    onJump = onBookmarkJump,
                    onJumped = onBookmarkJumped,
                    onRename = onBookmarkRename,
                    onDelete = onBookmarkDelete,
                    onPickColor = onBookmarkPickColor,
                    onApplyMode = onBookmarkApplyMode,
                    onDeleteMany = onBookmarkDeleteMany,
                    modifier = Modifier.fillMaxSize(),
                )
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
    // Inset from the screen edges so the tab strip does NOT touch the sides ("shorter, not thinner" —
    // request 2026-06-16): the horizontal padding sits OUTSIDE the border, so the bordered strip floats
    // with side gaps; the row keeps its original height (full label + 10dp vertical padding).
    // Slightly rounded corners (request 2026-06-16). Clip BEFORE the border so the active tab's
    // accent fill is clipped to the rounded shape at the strip's ends, and the border follows it.
    val tabShape = RoundedCornerShape(8.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp)
            .clip(tabShape)
            .border(EinkTokens.hairline, MaterialTheme.colorScheme.outline, tabShape),
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

/** Reverse-map: [Hyphenation] -> language code string ("" = off) the controls expect. */
private fun ReflowConfig.hyphenationLang(): String = when (val h = hyphenation) {
    is Hyphenation.Language -> h.lang
    Hyphenation.Off -> ""
}
