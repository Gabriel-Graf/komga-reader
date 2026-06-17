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
import com.komgareader.app.ui.components.EinkTextField
import com.komgareader.app.ui.components.EinkToggle
import com.komgareader.plugin.ConfigField
import com.komgareader.plugin.ConfigSchema
import com.komgareader.plugin.FieldType

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
 * Vorbelegt: gesetzter Default oder leerer String (BOOL-Felder → "false" wenn leer).
 */
@Composable
fun rememberPluginFormState(schema: ConfigSchema): PluginFormState {
    val values: SnapshotStateMap<String, String> = remember(schema) {
        schema.fields.map { field ->
            val initial = field.default.ifEmpty {
                if (field.type == FieldType.BOOL) "false" else ""
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
 * - TEXT/URL → beschriftetes [OutlinedTextField] (URL: URI-Tastatur)
 * - SECRET   → beschriftetes [OutlinedTextField] mit Passwort-Maske
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
        FieldType.NUMBER -> {
            // Rendering placeholder: NUMBER field type declared but rendering deferred to later task.
            EinkTextField(
                value = value,
                onValueChange = onValueChange,
                label = field.label,
                keyboardType = KeyboardType.Decimal,
            )
        }
    }
}
