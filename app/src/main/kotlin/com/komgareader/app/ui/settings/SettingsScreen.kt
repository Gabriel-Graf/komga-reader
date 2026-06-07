package com.komgareader.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.ui.components.HighlightText
import com.komgareader.app.ui.theme.EinkTokens

/** Größenklasse abhängig von der verfügbaren Breite — keine Magic-dp verstreut. */
private data class SettingsSizing(
    val sidebarWidth: Dp,
    val iconSize: Dp,
    val labelSize: TextUnit,
    val contentPadding: Dp,
)

private val SizingMedium = SettingsSizing(220.dp, 28.dp, 16.sp, EinkTokens.screenPadding)
private val SizingExpanded = SettingsSizing(280.dp, 32.dp, 20.sp, 24.dp)

/**
 * Adaptiver Settings-Host. < 600 dp → Accordion (Phone), sonst Master-Detail
 * (Tablet/E-Ink), ab 900 dp größer. [query] (live) filtert auf Treffer-Sektionen,
 * springt automatisch zur ersten und markiert Treffer (siehe HighlightText).
 */
@Composable
fun SettingsScreen(
    query: String,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val s = LocalStrings.current
    val sections = buildSettingsSections(s, viewModel)
    val visible = if (query.isBlank()) sections else sections.filter { sectionMatches(it.searchTerms, query) }

    BoxWithConstraints(modifier.fillMaxSize()) {
        if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(s.searchNoResults, textAlign = TextAlign.Center, modifier = Modifier.padding(32.dp))
            }
            return@BoxWithConstraints
        }
        if (maxWidth < 600.dp) {
            SettingsAccordion(visible, query, SizingMedium)
        } else {
            val sizing = if (maxWidth >= 900.dp) SizingExpanded else SizingMedium
            SettingsMasterDetail(visible, query, sizing)
        }
    }
}

@Composable
private fun SettingsMasterDetail(visible: List<SettingsSection>, query: String, sizing: SettingsSizing) {
    var selectedId by rememberSaveable { mutableStateOf(visible.first().id) }
    // Auto-Sprung: wenn die Auswahl ausgefiltert ist, auf die erste sichtbare wechseln.
    // Key auf query (stabil), nicht auf die bei jeder Recomposition neue visible-Liste.
    LaunchedEffect(query) {
        if (visible.none { it.id == selectedId }) selectedId = visible.first().id
    }
    val selected = visible.firstOrNull { it.id == selectedId } ?: visible.first()

    Row(Modifier.fillMaxSize()) {
        SettingsSidebar(
            visible = visible,
            selectedId = selectedId,
            query = query,
            sizing = sizing,
            onSelect = { selectedId = it },
            modifier = Modifier.width(sizing.sidebarWidth).fillMaxHeight(),
        )
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                // Sektionen, die ihr eigenes Scrollen verwalten (z. B. Farbfilter mit gepinntem
                // Cover), bekommen eine gebundene Höhe statt des Host-Scrolls.
                .then(if (selected.scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(sizing.contentPadding),
        ) {
            selected.content(query)
        }
    }
}

@Composable
private fun SettingsSidebar(
    visible: List<SettingsSection>,
    selectedId: SettingsSectionId,
    query: String,
    sizing: SettingsSizing,
    onSelect: (SettingsSectionId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .border(EinkTokens.hairline, MaterialTheme.colorScheme.outlineVariant)
            .verticalScroll(rememberScrollState()),
    ) {
        visible.forEach { section ->
            val active = section.id == selectedId
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(section.id) }
                    .padding(horizontal = 12.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Aktiv: schwarzer Akzent-Balken links (E-Ink-Bottom-Bar-Sprache, vertikal).
                Box(
                    Modifier
                        .size(width = 3.dp, height = sizing.iconSize)
                        .background(if (active) MaterialTheme.colorScheme.onSurface else Color.Transparent),
                )
                Spacer(Modifier.width(10.dp))
                Icon(
                    section.icon,
                    contentDescription = null,
                    modifier = Modifier.size(sizing.iconSize),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(12.dp))
                HighlightText(
                    text = section.title,
                    query = query,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = sizing.labelSize,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    ),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SettingsAccordion(visible: List<SettingsSection>, query: String, sizing: SettingsSizing) {
    // Bei aktiver Suche alle Treffer-Sektionen aufgeklappt; sonst nur die manuell geöffnete (null = alle zu).
    var openId by rememberSaveable { mutableStateOf<SettingsSectionId?>(visible.first().id) }
    val searching = query.isNotBlank()

    LazyColumn(
        Modifier.fillMaxSize().padding(EinkTokens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(EinkTokens.tileGap),
    ) {
        items(visible, key = { it.id }) { section ->
            val expanded = searching || section.id == openId
            Column(
                Modifier
                    .fillMaxWidth()
                    .border(
                        EinkTokens.hairline,
                        MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(EinkTokens.tileRadius),
                    ),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !searching) { openId = if (openId == section.id) null else section.id }
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        section.icon,
                        contentDescription = null,
                        modifier = Modifier.size(sizing.iconSize),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.width(12.dp))
                    HighlightText(
                        text = section.title,
                        query = query,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = sizing.labelSize),
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        if (expanded) Icons.Outlined.KeyboardArrowDown else Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AnimatedVisibility(visible = expanded) {
                    Box(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                        section.content(query)
                    }
                }
            }
        }
    }
}
