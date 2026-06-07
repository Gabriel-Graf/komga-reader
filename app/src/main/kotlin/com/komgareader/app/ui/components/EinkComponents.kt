package com.komgareader.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.komgareader.app.ui.icons.AppIcons
import com.komgareader.app.ui.theme.EinkTokens

/**
 * Wiederverwendbare E-Ink-Bausteine im Onyx-Look. Siehe Skill `komga-eink-ui`.
 * Alle Komponenten: flach, Hairline- bzw. starker Rahmen statt Schatten, keine Animation.
 */

/**
 * Kachel mit Hairline-Rahmen: Icon links, Titel + Summary, optional Chevron für Drill-in.
 * Summary zeigt typischerweise den aktuellen Wert ("E-Ink · Hell", "Deutsch").
 */
@Composable
fun SettingsTile(
    icon: ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showChevron: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(
                EinkTokens.hairline,
                MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(EinkTokens.tileRadius),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (showChevron) {
            Icon(
                AppIcons.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Kleiner Sektions-Kopf über einer Gruppe von Zeilen/Tiles. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(bottom = 4.dp),
    )
}

/**
 * Auswahlzeile mit Häkchen rechts statt RadioButton-Kreis (ruhiger auf E-Ink).
 * Für Theme-/Sprach-/Modus-Auswahl. [query] markiert Suchtreffer im Label.
 */
@Composable
fun ChoiceRow(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
    query: String = "",
    onSelect: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HighlightText(
            text = label,
            query = query,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        // Fester Häkchen-Slot: reserviert die Breite auch ohne Auswahl, damit ein optionales
        // [trailing]-Element (z. B. Info-Button) zeilenübergreifend bündig ganz rechts sitzt.
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            if (selected) {
                Icon(
                    AppIcons.Check,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        trailing?.invoke()
    }
}

/**
 * Zeile mit ±-Steppern für einen numerischen Wert — E-Ink-tauglich (kein Slider,
 * der auf E-Ink schlecht zeichnet). Label links, [−] Wert [+] rechts. [display]
 * formatiert den Wert (z. B. "25 %").
 */
@Composable
fun StepperRow(
    label: String,
    value: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
    canDecrement: Boolean = true,
    canIncrement: Boolean = true,
    display: (Int) -> String = { it.toString() },
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        StepperButton(AppIcons.Minus, enabled = canDecrement, contentDescription = "−", onClick = onDecrement)
        Text(
            text = display(value),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(64.dp).padding(horizontal = 8.dp),
        )
        StepperButton(AppIcons.Plus, enabled = canIncrement, contentDescription = "+", onClick = onIncrement)
    }
}

@Composable
private fun StepperButton(
    icon: ImageVector,
    enabled: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .size(40.dp)
            .border(EinkTokens.hairline, tint, RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/**
 * Gerüst für eine Settings-Unterseite: TopBar mit Titel + Zurück-Pfeil,
 * scrollender Body mit Screen-Padding.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubPageScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    content: @Composable () -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AppIcons.Back, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        val base = Modifier
            .padding(padding)
            .fillMaxSize()
        val body = if (scrollable) base.verticalScroll(rememberScrollState()) else base
        Column(
            modifier = body.padding(EinkTokens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(EinkTokens.sectionGap),
        ) {
            content()
        }
    }
}

/** Inhalts-Padding-Helfer, falls eine Unterseite eigenes Scroll-/Listen-Layout braucht. */
val SubPageContentPadding = PaddingValues(EinkTokens.screenPadding)

/**
 * Diskrete ±-Regelzeile (kein kontinuierlicher Slider — ruckelt auf E-Ink). Label links,
 * aktueller Wert mittig, − / + Buttons rechts. [enabled] sperrt z. B. bei Built-in-Profilen.
 */
@Composable
fun StepperRow(
    label: String,
    valueText: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        IconButton(onClick = onDecrement, enabled = enabled) {
            Icon(AppIcons.Minus, contentDescription = "−", modifier = Modifier.size(22.dp))
        }
        Text(
            valueText,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.width(56.dp),
            textAlign = TextAlign.Center,
        )
        IconButton(onClick = onIncrement, enabled = enabled) {
            Icon(AppIcons.Plus, contentDescription = "+", modifier = Modifier.size(22.dp))
        }
    }
}
