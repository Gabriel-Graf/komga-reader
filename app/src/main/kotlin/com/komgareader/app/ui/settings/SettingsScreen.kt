package com.komgareader.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.komgareader.app.data.hasUpdate
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.ui.components.BadgeDot
import com.komgareader.app.ui.components.HighlightText
import com.komgareader.app.ui.components.LocalContentBottomInset
import com.komgareader.app.ui.components.LocalEinkMode
import com.komgareader.ui.theme.EinkTokens
import com.komgareader.ui.icons.AppIcons
import com.komgareader.ui.slots.LocalResolvedSlots
import com.komgareader.ui.slots.SettingsSection
import com.komgareader.ui.slots.SettingsSectionId
import com.komgareader.ui.slots.SettingsState
import com.komgareader.ui.theme.LocalDesignTokens

/** Größenklasse abhängig von der verfügbaren Breite — keine Magic-dp verstreut. */
private data class SettingsSizing(
    val sidebarWidth: Dp,
    val iconSize: Dp,
    val labelSize: TextUnit,
    val contentPadding: Dp,
)

private val SizingMedium = SettingsSizing(220.dp, 28.dp, 16.sp, EinkTokens.screenPadding)
private val SizingExpanded = SettingsSizing(280.dp, 32.dp, 20.sp, 24.dp)

// E-Ink: section titles a notch larger for legibility (faint at the default size on slow panels).
private val SizingMediumEink = SizingMedium.copy(labelSize = 18.sp)
private val SizingExpandedEink = SizingExpanded.copy(labelSize = 22.sp)

/**
 * On E-Ink the whole settings subtree reads at a larger, heavier scale — the Material/LCD sizes
 * look faint and small on the slow grey panel. Scoped to settings only (a local [MaterialTheme]),
 * so the rest of the app is untouched. Weights stay at or above the global [EinkTypography] floor.
 */
@Composable
private fun einkSettingsTypography(): androidx.compose.material3.Typography {
    val t = MaterialTheme.typography
    return t.copy(
        bodyLarge = t.bodyLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.Medium),
        bodyMedium = t.bodyMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium),
        bodySmall = t.bodySmall.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
        titleSmall = t.titleSmall.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
        labelMedium = t.labelMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    )
}

/**
 * Adaptiver Settings-Host — dünner Wrapper: baut die [SettingsState]-Surface und delegiert an die
 * `settings`-Region der Slot-Naht. [query] (live) filtert im Default-Pack auf Treffer-Sektionen,
 * springt automatisch zur ersten und markiert Treffer (siehe HighlightText). Der [modifier] ist
 * Host-Layout (Route-Padding), **nicht** Teil der Surface → als Box-Wrapper um den Slot-Call.
 */
@Composable
fun SettingsScreen(
    query: String,
    modifier: Modifier = Modifier,
    initialSection: SettingsSectionId? = null,
    viewModel: SettingsViewModel = hiltViewModel(),
    updateViewModel: AppUpdateViewModel = hiltViewModel(),
) {
    val s = LocalStrings.current
    // The "About" section gets the dirty dot when an app update is ready (guides the user there).
    val updateState by updateViewModel.state.collectAsState()
    val sections = buildSettingsSections(s, viewModel, aboutBadge = updateState.hasUpdate)
    val state = SettingsState(sections, query, initialSectionId = initialSection)
    Box(modifier) {
        LocalResolvedSlots.current.settings(state)
    }
}

/**
 * Das mitgelieferte Onyx-Settings-Skelett. < 600 dp → Accordion (Phone), sonst Master-Detail
 * (Tablet/E-Ink), ab 900 dp größer. Verhaltens-/pixelgleich zum bisherigen `SettingsScreen`-Rumpf.
 */
@Composable
fun DefaultSettings(state: SettingsState) {
    val s = LocalStrings.current
    val eink = LocalEinkMode.current
    val visible = if (state.query.isBlank()) {
        state.sections
    } else {
        state.sections.filter { sectionMatches(it.searchTerms, state.query) }
    }

    // E-Ink: render the whole settings subtree at the larger, heavier scale (scoped MaterialTheme).
    val body = @Composable {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (visible.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(s.searchNoResults, textAlign = TextAlign.Center, modifier = Modifier.padding(32.dp))
                }
                return@BoxWithConstraints
            }
            val medium = if (eink) SizingMediumEink else SizingMedium
            if (maxWidth < 600.dp) {
                SettingsAccordion(visible, state.query, medium, state.initialSectionId)
            } else {
                val sizing = if (maxWidth >= 900.dp) {
                    if (eink) SizingExpandedEink else SizingExpanded
                } else {
                    medium
                }
                SettingsMasterDetail(visible, state.query, sizing, state.initialSectionId)
            }
        }
    }

    if (eink) {
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme,
            shapes = MaterialTheme.shapes,
            typography = einkSettingsTypography(),
            content = body,
        )
    } else {
        body()
    }
}

@Composable
private fun SettingsMasterDetail(
    visible: List<SettingsSection>,
    query: String,
    sizing: SettingsSizing,
    initialSectionId: SettingsSectionId? = null,
) {
    var selectedId by rememberSaveable { mutableStateOf(initialSectionId ?: visible.first().id) }
    // Auto-Sprung: wenn die Auswahl ausgefiltert ist, auf die erste sichtbare wechseln.
    // Key auf query (stabil), nicht auf die bei jeder Recomposition neue visible-Liste.
    LaunchedEffect(query) {
        if (visible.none { it.id == selectedId }) selectedId = visible.first().id
    }
    // Deep-link hint (e.g. update banner → "About"): jump to it when the host requests a section.
    LaunchedEffect(initialSectionId) {
        if (initialSectionId != null && visible.any { it.id == initialSectionId }) selectedId = initialSectionId
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
        // Trennung Sidebar ⇄ Content: nur eine (etwas dickere) vertikale Linie, keine Box um die Tabs.
        // Äußerer Slot: Höhe inklusive Freiraum. Innerer Box: zeichnet die Linie nur bis zur Bar.
        // (background() zeichnet auf die gemessene Größe — padding() muss außen stehen, damit die
        // sichtbare Farbe im verbleibenden Innen-Slot landet, nicht auf der vollen fillMaxHeight-Höhe.)
        Box(
            Modifier
                .width(2.dp)
                .fillMaxHeight()
                // Etwas kürzer als die volle Höhe: Luft oben (zur Suche) und unten — gibt dem Layout Ruhe.
                .padding(vertical = EinkTokens.sectionGap)
                .padding(bottom = LocalContentBottomInset.current),
        ) {
            Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.outline))
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                // Sektionen, die ihr eigenes Scrollen verwalten (z. B. Farbfilter mit gepinntem
                // Cover), bekommen eine gebundene Höhe statt des Host-Scrolls.
                .then(if (selected.scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(sizing.contentPadding)
                // Freiraum für die überlagernde Bottom-Menubar, damit die letzte Zeile frei steht.
                .padding(bottom = LocalContentBottomInset.current),
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
        modifier.verticalScroll(rememberScrollState()),
    ) {
        val accent = LocalDesignTokens.current.accent
        visible.forEach { section ->
            val active = section.id == selectedId
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(section.id) }
                    .padding(horizontal = 12.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Aktiv: Akzent-Balken links (E-Ink: Schwarz, Kaleido/LCD: Akzentfarbe).
                Box(
                    Modifier
                        .size(width = 3.dp, height = sizing.iconSize)
                        .background(if (active) accent else Color.Transparent),
                )
                Spacer(Modifier.width(10.dp))
                SectionIcon(section, sizing.iconSize)
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
private fun SettingsAccordion(
    visible: List<SettingsSection>,
    query: String,
    sizing: SettingsSizing,
    initialSectionId: SettingsSectionId? = null,
) {
    // Bei aktiver Suche alle Treffer-Sektionen aufgeklappt; sonst nur die manuell geöffnete (null = alle zu).
    var openId by rememberSaveable { mutableStateOf<SettingsSectionId?>(initialSectionId ?: visible.first().id) }
    // Deep-link hint (e.g. update banner → "About"): expand it when the host requests a section.
    LaunchedEffect(initialSectionId) {
        if (initialSectionId != null && visible.any { it.id == initialSectionId }) openId = initialSectionId
    }
    val searching = query.isNotBlank()

    LazyColumn(
        Modifier.fillMaxSize().padding(EinkTokens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(EinkTokens.tileGap),
        contentPadding = PaddingValues(bottom = LocalContentBottomInset.current),
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
                    SectionIcon(section, sizing.iconSize)
                    Spacer(Modifier.width(12.dp))
                    HighlightText(
                        text = section.title,
                        query = query,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = sizing.labelSize),
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        if (expanded) AppIcons.ChevronDown else AppIcons.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val eink = LocalEinkMode.current
                AnimatedVisibility(
                    visible = expanded,
                    enter = if (eink) EnterTransition.None else expandVertically(expandFrom = Alignment.Top),
                    exit = if (eink) ExitTransition.None else shrinkVertically(shrinkTowards = Alignment.Top),
                ) {
                    Box(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                        section.content(query)
                    }
                }
            }
        }
    }
}

/** Section icon with an optional "dirty" dot (e.g. "About" when an app update is available). */
@Composable
private fun SectionIcon(section: SettingsSection, size: Dp) {
    Box {
        Icon(
            section.icon,
            contentDescription = null,
            modifier = Modifier.size(size),
            tint = MaterialTheme.colorScheme.onSurface,
        )
        if (section.badge) {
            BadgeDot(Modifier.align(Alignment.TopEnd).offset(x = 5.dp, y = (-3).dp))
        }
    }
}
