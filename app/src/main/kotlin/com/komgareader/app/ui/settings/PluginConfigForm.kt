package com.komgareader.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.toMutableStateMap
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.ui.components.EinkToggle
import com.komgareader.plugin.ConfigField
import com.komgareader.plugin.ConfigSchema
import com.komgareader.plugin.FieldType

/**
 * Generisches Formular für ein Plugin-[ConfigSchema]: rendert je [ConfigField] genau ein
 * E-Ink-konformes Control, sammelt die Werte und ruft [onSubmit] mit dem fertigen
 * `Map<key, value>`-Ergebnis auf.
 *
 * Verantwortung: Schema → Werte-Map. Kein Netzwerk, kein Dialog-Rahmen — der Aufrufer
 * entscheidet, ob das Formular in einem [EinkModal] oder direkt auf der Seite erscheint.
 *
 * E-Ink-Invarianten:
 * - Flach, 1.5px-Rand (OutlinedTextField + EinkToggle), keine Schatten.
 * - Keinerlei Animationen — alle State-Wechsel sind sofortig.
 * - Labels kommen vom Plugin (bereits lokalisiert); nur der Submit-Button nutzt das App-i18n
 *   ([Strings.save]).
 *
 * BOOL-Felder speichern intern "true"/"false" als String — konsistent mit [ServerConfig.extras].
 */
@Composable
fun PluginConfigForm(
    schema: ConfigSchema,
    onSubmit: (Map<String, String>) -> Unit,
) {
    val s = LocalStrings.current

    // Vorbelegen: gesetzter Default oder leerer String (BOOL-Felder → "false" wenn leer).
    val values: SnapshotStateMap<String, String> = remember(schema) {
        schema.fields.map { field ->
            val initial = field.default.ifEmpty {
                if (field.type == FieldType.BOOL) "false" else ""
            }
            field.key to initial
        }.toMutableStateMap()
    }

    // Submit nur aktiv, wenn alle Pflichtfelder (nicht-BOOL) ausgefüllt sind.
    val submitEnabled = schema.fields
        .filter { it.required && it.type != FieldType.BOOL }
        .all { field -> values[field.key].orEmpty().isNotBlank() }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        schema.fields.forEach { field ->
            PluginConfigField(
                field = field,
                value = values[field.key].orEmpty(),
                onValueChange = { values[field.key] = it },
            )
        }

        Button(
            onClick = { onSubmit(values.toMap()) },
            enabled = submitEnabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(s.save)
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
        FieldType.TEXT -> OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(field.label) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        FieldType.URL -> OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(field.label) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        FieldType.SECRET -> OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(field.label) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
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
    }
}
