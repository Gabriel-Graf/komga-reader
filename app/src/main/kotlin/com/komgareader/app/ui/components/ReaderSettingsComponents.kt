package com.komgareader.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.komgareader.ui.icons.AppIcons
import com.komgareader.ui.theme.EinkTokens
import com.komgareader.ui.theme.LocalDesignTokens

/**
 * Eine wählbare Option eines [SegmentedChoiceRow]: stabiler [key] (persistierter Wert) +
 * anzeigbares [label]. Quellen-/geräte-neutral — kein i18n-Zwang im Modell.
 */
data class SegmentOption(val key: String, val label: String)

/**
 * **Dominanter Scope-Kopf** für die scope-gruppierte Hierarchie der Reader-Settings
 * („Allgemein", „Roman-Reader", „Webtoon", „Comic"). Bewusst stärker als der
 * [SectionHeader] einer Untergruppe: fetter Titel + dünne Trennlinie (Hairline,
 * `outlineVariant`) darunter, die den Scope optisch vom vorherigen abgrenzt. Flach,
 * monochrom, E-Ink-konform — kein Schatten, keine Akzentfarbe (neutrale Chrome).
 *
 * Eigenständiger, parametrisierter Baustein (modulare-UI-Richtung): ein UI-Pack könnte
 * den Scope-Kopf gegen eine Alternative tauschen, ohne den Screen anzufassen.
 */
@Composable
fun ScopeHeader(title: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Box(
            Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .height(EinkTokens.hairline)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
    }
}

/**
 * **Label + horizontaler Segment-Selektor**: die Optionen liegen gleich breit
 * (`weight(1f)`) nebeneinander, das gewählte Segment ist **aktiv gefüllt**. Verallgemeinert
 * das `DitherSelectorRow`-Muster aus den Farbfilter-Settings — selbe Akzent-/Auswahl-Optik,
 * damit die App konsistent bleibt.
 *
 * **Geräteklassen-Disziplin:** der aktive Zustand nutzt `LocalDesignTokens.accent` /
 * `onAccent` (mono = schwarz/weiß, Kaleido/LCD = Farbe) — nie hartkodiert, nie an Bewegung
 * gekoppelt. Unausgewählte Segmente tragen einen Hairline-Rahmen, neutrales `onSurface`.
 * [query] hebt Treffer im Label hervor.
 */
@Composable
fun SegmentedChoiceRow(
    label: String,
    options: List<SegmentOption>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    query: String = "",
) {
    val tokens = LocalDesignTokens.current
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HighlightText(
            text = label,
            query = query,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f).padding(end = 12.dp),
        )
        Row(
            modifier = Modifier.weight(1.4f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            options.forEach { option ->
                val isActive = option.key == selectedKey
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .then(
                            if (isActive) Modifier.background(tokens.accent)
                            else Modifier.border(
                                EinkTokens.hairline,
                                MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(6.dp),
                            ),
                        )
                        .clickable { onSelect(option.key) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = if (isActive) tokens.onAccent else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
    }
}

/**
 * **Antippbare Wert-Zeile:** Label links, aktueller [value] + Chevron rechts. Öffnet einen
 * Auswahl-Dialog ([PickerModal]). Neutrale Chrome (`onSurface`/`onSurfaceVariant`) — kein
 * Akzent, kein aktiver Zustand (die Auswahl selbst passiert im Modal). [query] hebt Treffer
 * im Label hervor. Für lange/seltene Auswahllisten (Schriftart, Anzeige-Modus), die als
 * Segmente zu breit würden.
 */
@Composable
fun PickerRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    query: String = "",
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HighlightText(
            text = label,
            query = query,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f).padding(end = 12.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            AppIcons.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * **Auswahl-Dialog** für eine [PickerRow]: Titel + vertikale Liste einfach-wählbarer Optionen
 * ([ChoiceRow] mit Häkchen). Tippen wählt und schließt sofort (genau **ein** Modal gleichzeitig,
 * E-Ink-Invariante). Erscheint ohne Animation. Baut auf [EinkInfoDialog] (read-only-Rahmen mit
 * X) — die Auswahl ist die einzige Aktion, ein Bestätigen-Button wäre überflüssig.
 *
 * Generisch über [T]: [options] beliebige Werte, [keyOf]/[labelOf] mappen auf
 * Persistenz-Schlüssel + Anzeigename. [descriptionOf] ist optional — wenn gesetzt und nicht
 * null, wird eine kleinere sekundäre Zeile unter dem Option-Label gerendert (nur im offenen
 * Picker sichtbar, nicht in der kollabierten [PickerRow]).
 *
 * [labelFontFamilyOf] is optional — when provided, each option's label is rendered in the
 * returned [FontFamily] (live font sample). Falls back to the theme default when the resolver
 * returns null. When non-null, [ChoiceRow] is bypassed so the label [Text] can carry a
 * custom [fontFamily] (ChoiceRow delegates to HighlightText which has no fontFamily slot).
 * All existing callers pass null implicitly and are unaffected.
 */
@Composable
fun <T> PickerModal(
    title: String,
    options: List<T>,
    selectedKey: String,
    keyOf: (T) -> String,
    labelOf: (T) -> String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    closeLabel: String,
    descriptionOf: ((T) -> String?)? = null,
    labelFontFamilyOf: ((T) -> FontFamily?)? = null,
) {
    val accentTint = LocalDesignTokens.current.accent
    EinkInfoDialog(title = title, onDismiss = onDismiss, closeLabel = closeLabel) {
        options.forEach { option ->
            val key = keyOf(option)
            val description = descriptionOf?.invoke(option)
            val customFont = labelFontFamilyOf?.invoke(option)
            if (customFont != null) {
                // Render label in the option's own font face — mirrors FontPickerRow in
                // NovelTypographyControls. ChoiceRow/HighlightText has no fontFamily slot,
                // so we use a flat Row that mirrors ChoiceRow's dense Onyx-look.
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = key == selectedKey, onClick = {
                                onSelect(key)
                                onDismiss()
                            })
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = labelOf(option),
                            style = MaterialTheme.typography.bodyLarge,
                            fontFamily = customFont,
                            modifier = Modifier.weight(1f),
                        )
                        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                            if (key == selectedKey) {
                                Icon(
                                    AppIcons.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp),
                                    tint = accentTint,
                                )
                            }
                        }
                    }
                    if (description != null) {
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                }
            } else if (description != null) {
                Column {
                    ChoiceRow(
                        label = labelOf(option),
                        selected = key == selectedKey,
                        dense = true,
                        onSelect = {
                            onSelect(key)
                            onDismiss()
                        },
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            } else {
                ChoiceRow(
                    label = labelOf(option),
                    selected = key == selectedKey,
                    dense = true,
                    onSelect = {
                        onSelect(key)
                        onDismiss()
                    },
                )
            }
        }
    }
}
