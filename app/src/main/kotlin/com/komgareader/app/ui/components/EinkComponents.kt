package com.komgareader.app.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.komgareader.app.ui.icons.AppIcons
import com.komgareader.app.ui.slots.LocalResolvedSlots
import com.komgareader.app.ui.slots.header
import com.komgareader.app.ui.theme.EinkTokens
import com.komgareader.app.ui.theme.LocalDesignTokens

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

/**
 * Sektions-Kopf über einer Gruppe von Settings-Zeilen. **Dominant** (titleMedium, 18sp, Bold,
 * onSurface) — bewusst **größer und stärker** als die Zeilen-Labels darunter (bodyLarge, 16sp),
 * damit die Hierarchie stimmt. Anti-Pattern (vorher real): Kopf kleiner/blasser als der Inhalt —
 * dann wirkt der Screen wie lose Buttons statt geordnete Information.
 */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
        ),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(bottom = 8.dp),
    )
}

/** Einrückung des Gruppen-Inhalts unter den [SectionHeader] — macht den Kopf zum Anker. */
val SettingsGroupIndent = 4.dp

/**
 * Wiederkehrendes Gerüst einer Settings-Gruppe: dominanter [SectionHeader] → optionaler
 * erklärender Helper → leicht eingerückter Inhalt. **Das** gemeinsame Konzept aller Settings-Tabs
 * (Verbindung, Reader, Farbfilter …) — gleiche Hierarchie, gleicher Abstand, damit die Seite wie
 * ein Konzept und nicht wie lose Zeilen wirkt. [query] markiert Suchtreffer im Helper.
 *
 * [trailing] hängt eine Aktion rechts in dieselbe Kopfzeile (z. B. ein „+"-Button) — ein
 * adressierbarer Slot statt eines kopierten Sonder-Headers (Richtung modulare UI). Default null =
 * unveränderter Kopf für alle bestehenden Aufrufer.
 */
@Composable
fun SettingsGroup(
    title: String,
    query: String,
    modifier: Modifier = Modifier,
    helper: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        if (trailing == null) {
            SectionHeader(title)
        } else {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionHeader(title, Modifier.weight(1f))
                trailing()
            }
        }
        if (helper != null) {
            HighlightText(
                helper, query, MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Column(Modifier.padding(start = SettingsGroupIndent)) { content() }
    }
}

/**
 * Leise Unter-Beschriftung über einem Feld-Cluster (z. B. „Server", „Anmeldung") — eine Stufe
 * **unter** [SectionHeader]: klein, gedämpft (labelMedium, onSurfaceVariant). Bündelt
 * zusammengehörige Eingabefelder optisch, ohne mit dem Sektions-Kopf zu konkurrieren.
 */
@Composable
fun FieldCaption(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(bottom = 2.dp),
    )
}

/**
 * Einheitliche Settings-Zeile: Label (+ optionaler Helper darunter) links, ein [trailing]-Control
 * rechts. **Das** kohärente Grundraster aller Einstellungen — Switch/Stepper/Wert hängen alle hier
 * dran, damit jede Zeile gleich ausgerichtet und gleich kompakt ist. [query] markiert Suchtreffer.
 */
@Composable
fun SettingsRow(
    label: String,
    modifier: Modifier = Modifier,
    helper: String? = null,
    query: String = "",
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            HighlightText(label, query, MaterialTheme.typography.bodyLarge)
            if (helper != null) {
                HighlightText(
                    helper, query, MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        trailing()
    }
}

/** Settings-Zeile mit An/Aus-Schalter rechts — kapselt die zuvor inline gebauten Switch-Zeilen. */
@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    helper: String? = null,
    query: String = "",
) {
    SettingsRow(label, modifier, helper, query) {
        EinkToggle(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * Flacher E-Ink-Toggle: 1.5px-Rahmen statt Schatten, maximaler Kontrast, **keine Bewegung**
 * (der Knopf springt sofort — auf E-Ink animiert nichts). Ersetzt den Material-`Switch`:
 * AN = gefüllte Pille im Geräteklassen-**Akzent** (mono = schwarz, Kaleido/LCD = Farbe),
 * Knopf rechts; AUS = leere Pille, neutraler Knopf links. Der gefüllte Zustand ist ein
 * **aktiver** Zustand → Akzent über [LocalDesignTokens], nie hartes `onSurface` (Stream C).
 */
@Composable
fun EinkToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val tokens = LocalDesignTokens.current
    val border = if (enabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant
    val track = if (checked) tokens.accent else MaterialTheme.colorScheme.surface
    val knob = if (checked) tokens.onAccent else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = modifier
            .size(width = 48.dp, height = 28.dp)
            .border(EinkTokens.hairline, border, RoundedCornerShape(14.dp))
            .background(track, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(20.dp).background(if (enabled) knob else border, CircleShape))
    }
}

/**
 * Prominenter Sektions-Kopf für **Reader-Panels** (Typo/TOC/Suche): `titleMedium` in
 * `onSurface` — bewusst **größer und stärker** als die Setting-Zeilen darunter
 * (`bodyLarge`), damit die Hierarchie stimmt (Anti-Pattern: Titel kleiner als Inhalt).
 * Kein gedämpftes Grau wie [SectionHeader] — der Panel-Kopf trägt die Gliederung.
 */
@Composable
fun PanelSectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
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
    dense: Boolean = false,
    onSelect: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect)
            .padding(vertical = if (dense) 8.dp else 14.dp),
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
                    // Auswahl-Signal in der Geräteklassen-Akzentfarbe — wie Sidebar-Balken & Segmente.
                    // mono = Schwarz (accent = onSurface), Kaleido/LCD = gedämpfte/volle Farbe.
                    tint = LocalDesignTokens.current.accent,
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
        topBar = { LocalResolvedSlots.current.header(title, onBack) {} },
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

/**
 * Kompakte ±-Regelzeile (≈40 dp) für Settings-Gruppen — enger als [StepperRow]
 * (das 48-dp-IconButtons nutzt). Label links, Wert mittig (als formatierter String),
 * − / + als kleines antippbares Icon (36 dp Touch-Bereich). Wird in Farbfilter-Settings
 * **und** in der Roman-Typografie-Sektion der Reader-Settings genutzt.
 */
@Composable
fun CompactStepperRow(
    label: String,
    valueText: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        CompactIconButton(AppIcons.Minus, "−", onDecrement)
        Text(
            valueText,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.width(52.dp),
            textAlign = TextAlign.Center,
        )
        CompactIconButton(AppIcons.Plus, "+", onIncrement)
    }
}

/**
 * Antippbares Icon mit kleinem Touch-Bereich (36 dp) statt des 48-dp-IconButton — für
 * enge Zeilen wie [CompactStepperRow]. Geteilter Baustein: genutzt von [CompactStepperRow]
 * sowie den Settings-Inhalten (Farbfilter, Roman-Typografie).
 */
@Composable
fun CompactIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    Icon(
        icon,
        contentDescription = contentDescription,
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(6.dp),
        tint = MaterialTheme.colorScheme.onSurface,
    )
}
