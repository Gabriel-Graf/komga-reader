package com.komgareader.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.toMutableStateMap
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.komgareader.app.ui.components.EinkSliderRow
import com.komgareader.app.ui.components.EinkTextField
import com.komgareader.app.ui.components.EinkToggle
import com.komgareader.plugin.ConfigField
import com.komgareader.plugin.ConfigSchema
import com.komgareader.plugin.FieldType
import kotlin.math.roundToInt

/**
 * Gehaltener Zustand eines Plugin-Konfigurations-Formulars — ermöglicht es dem Aufrufer
 * (z. B. einem [com.komgareader.app.ui.components.EinkModal]), den Bestätigen-Button zu
 * steuern und die Werte beim Bestätigen abzugreifen, ohne den Submit-Button ins Formular
 * zu legen.
 *
 * Typischer Verwendungsfall:
 * ```kotlin
 * val formState = rememberPluginFormState(schema)
 * EinkModal(
 *     confirmEnabled = formState.isValid,
 *     onConfirm = { onSubmit(formState.snapshot()) },
 * ) {
 *     PluginConfigForm(formState)
 * }
 * ```
 */
class PluginFormState(
    internal val schema: ConfigSchema,
    val values: SnapshotStateMap<String, String>,
) {
    /** true, wenn alle Pflichtfelder (nicht-BOOL) ausgefüllt sind. */
    val isValid: Boolean
        get() = schema.fields
            .filter { it.required && it.type != FieldType.BOOL }
            .all { field -> values[field.key].orEmpty().isNotBlank() }

    /** Liefert eine unveränderliche Kopie der aktuellen Werte. */
    fun snapshot(): Map<String, String> = values.toMap()
}

/**
 * Erzeugt und merkt sich einen [PluginFormState] für das gegebene [schema].
 * Vorbelegt: gesetzter Default oder typ-spezifischer Fallback:
 * - BOOL   → "false"
 * - NUMBER → "%.2f".format(min ?: 0.0) — passend zum Slider-Speicherformat, kein Rundungs-Drift
 * - sonst  → "" (Pflichtfelder → isValid prüft auf Leer-String)
 */
@Composable
fun rememberPluginFormState(schema: ConfigSchema): PluginFormState {
    val values: SnapshotStateMap<String, String> = remember(schema) {
        schema.fields.map { field ->
            val initial = field.default.ifEmpty {
                when (field.type) {
                    FieldType.BOOL -> "false"
                    FieldType.NUMBER -> "%.2f".format(field.min ?: 0.0)
                    else -> ""
                }
            }
            field.key to initial
        }.toMutableStateMap()
    }
    return remember(schema, values) { PluginFormState(schema, values) }
}

/**
 * Generisches Formular für ein Plugin-[ConfigSchema] zur Integration in ein
 * [com.komgareader.app.ui.components.EinkModal]: rendert je [ConfigField] genau ein
 * E-Ink-konformes Control aus [state] und sammelt die Werte. Es legt bewusst KEINEN
 * Submit-Button ins Formular — der Modal-eigene Bestätigen-Button ist der Submit-Auslöser
 * (`onConfirm = { onSubmit(state.snapshot()) }`, `confirmEnabled = state.isValid`).
 * [state] muss per [rememberPluginFormState] im Aufrufer erzeugt worden sein.
 *
 * Verantwortung: Schema → Werte-Map.
 *
 * E-Ink-Invarianten:
 * - Flach, 1.5px-Rand (OutlinedTextField + EinkToggle), keine Schatten.
 * - Keinerlei Animationen — alle State-Wechsel sind sofortig.
 * - Labels kommen vom Plugin (bereits lokalisiert) — kein App-i18n-Key nötig.
 *
 * BOOL-Felder speichern intern "true"/"false" als String — konsistent mit [ServerConfig.extras].
 */
@Composable
fun PluginConfigForm(state: PluginFormState) {
    PluginConfigFields(state)
}

/** Rendert alle Schema-Felder eines [PluginFormState] — gemeinsamer Kern beider Formular-Varianten. */
@Composable
private fun PluginConfigFields(state: PluginFormState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        state.schema.fields.forEach { field ->
            PluginConfigField(
                field = field,
                value = state.values[field.key].orEmpty(),
                onValueChange = { state.values[field.key] = it },
            )
        }
    }
}

/**
 * Ein einzelnes Konfigurations-Control, je nach [ConfigField.type]:
 * - TEXT/URL → beschriftetes [EinkTextField] (URL: URI-Tastatur)
 * - SECRET   → beschriftetes [EinkTextField] mit Passwort-Maske
 * - NUMBER   → diskreter [EinkSliderRow] (E-Ink-sicher: kein Drag/Fling, kein Ghosting)
 * - BOOL     → Label-Zeile mit [EinkToggle] rechts
 *
 * Das Label kommt direkt aus dem Plugin ([ConfigField.label], bereits lokalisiert) —
 * kein App-i18n-Key nötig.
 */
@Composable
private fun PluginConfigField(
    field: ConfigField,
    value: String,
    onValueChange: (String) -> Unit,
) {
    when (field.type) {
        FieldType.TEXT -> EinkTextField(
            value = value,
            onValueChange = onValueChange,
            label = field.label,
        )
        FieldType.URL -> EinkTextField(
            value = value,
            onValueChange = onValueChange,
            label = field.label,
            keyboardType = KeyboardType.Uri,
        )
        FieldType.SECRET -> EinkTextField(
            value = value,
            onValueChange = onValueChange,
            label = field.label,
            isPassword = true,
        )
        FieldType.NUMBER -> {
            // Map the floating-point range (min/max/step) onto EinkSliderRow's integer position
            // space so the control is discrete-only — no continuous drag, no E-Ink ghosting.
            val min = field.min ?: 0.0
            val max = field.max ?: 1.0
            val step = field.step?.takeIf { it > 0.0 } ?: 0.05
            val stepCount = ((max - min) / step).roundToInt().coerceAtLeast(1)
            val current = value.toDoubleOrNull()?.coerceIn(min, max) ?: min
            val position = ((current - min) / step).roundToInt().coerceIn(0, stepCount)
            EinkSliderRow(
                label = field.label,
                valueText = "%.2f".format(current),
                position = position,
                stepCount = stepCount,
                onPosition = { p -> onValueChange("%.2f".format(min + p * step)) },
            )
        }
        FieldType.BOOL -> {
            val checked = value == "true"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = field.label,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp),
                )
                EinkToggle(
                    checked = checked,
                    onCheckedChange = { onValueChange(if (it) "true" else "false") },
                )
            }
        }
    }
}
