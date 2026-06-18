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
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

private const val DEFAULT_NUMBER_STEP = 0.05

/**
 * Decimal places needed to store a value without losing the [step] granularity. A coarser format than
 * the step would round a moved value back onto the previous notch, pinning the slider (e.g. step 0.001
 * formatted as "%.2f" always reads back the same position). At least 2 decimals (confidence-style).
 */
private fun decimalsFor(step: Double): Int {
    var decimals = 0
    var s = step
    while (decimals < 6 && kotlin.math.abs(s - s.roundToInt()) > 1e-9) {
        s *= 10
        decimals++
    }
    return max(2, decimals)
}

/**
 * Formats [value] with exactly enough decimals for [step] (see [decimalsFor]) using a **dot** decimal
 * separator ([Locale.ROOT]). This is the value that gets STORED and later parsed with
 * [String.toDoubleOrNull], which only accepts a dot — formatting with the default locale would emit a
 * comma on e.g. German devices, making the round-trip return null and pinning the slider at its
 * minimum. Internal so the locale round-trip is unit-testable.
 */
internal fun formatForStep(value: Double, step: Double): String =
    String.format(Locale.ROOT, "%.${decimalsFor(step)}f", value)

/**
 * Retained state of a plugin configuration form — lets the caller (e.g. an
 * [com.komgareader.app.ui.components.EinkModal]) control the confirm button and read back
 * the values on confirmation, without placing a submit button inside the form.
 *
 * Typical usage:
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
    /** true when all required fields (non-BOOL) are filled in. */
    val isValid: Boolean
        get() = schema.fields
            .filter { it.required && it.type != FieldType.BOOL }
            .all { field -> values[field.key].orEmpty().isNotBlank() }

    /** Returns an immutable snapshot of the current values. */
    fun snapshot(): Map<String, String> = values.toMap()
}

/**
 * Creates and remembers a [PluginFormState] for the given [schema].
 * Pre-filled with the configured default or a type-specific fallback:
 * - BOOL   → "false"
 * - NUMBER → [formatForStep] of (min ?: 0.0) — dot decimal separator, matches the slider storage format
 * - else   → "" (required fields → isValid checks for empty string)
 */
@Composable
fun rememberPluginFormState(schema: ConfigSchema): PluginFormState {
    val values: SnapshotStateMap<String, String> = remember(schema) {
        schema.fields.map { field ->
            val initial = field.default.ifEmpty {
                when (field.type) {
                    FieldType.BOOL -> "false"
                    FieldType.NUMBER -> formatForStep(field.min ?: 0.0, field.step ?: DEFAULT_NUMBER_STEP)
                    else -> ""
                }
            }
            field.key to initial
        }.toMutableStateMap()
    }
    return remember(schema, values) { PluginFormState(schema, values) }
}

/**
 * Generic form for a plugin [ConfigSchema] for use inside an
 * [com.komgareader.app.ui.components.EinkModal]: renders exactly one E-Ink-conformant control
 * per [ConfigField] from [state] and collects the values. Deliberately places NO submit button
 * in the form — the modal's own confirm button is the submit trigger
 * (`onConfirm = { onSubmit(state.snapshot()) }`, `confirmEnabled = state.isValid`).
 * [state] must be created by [rememberPluginFormState] in the caller.
 *
 * Responsibility: schema → values map.
 *
 * E-Ink invariants:
 * - Flat, 1.5 px border (OutlinedTextField + EinkToggle), no shadows.
 * - No animations — all state transitions are immediate.
 * - Labels come from the plugin (already localised) — no app i18n key needed.
 *
 * BOOL fields store "true"/"false" internally as strings — consistent with [ServerConfig.extras].
 */
@Composable
fun PluginConfigForm(state: PluginFormState) {
    PluginConfigFields(state)
}

/** Renders all schema fields of a [PluginFormState] — shared core of both form variants. */
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
 * A single configuration control, depending on [ConfigField.type]:
 * - TEXT/URL → labelled [EinkTextField] (URL: URI keyboard)
 * - SECRET   → labelled [EinkTextField] with password masking
 * - NUMBER   → discrete [EinkSliderRow] (E-Ink-safe: no drag/fling, no ghosting)
 * - BOOL     → label row with [EinkToggle] on the right
 *
 * The label comes directly from the plugin ([ConfigField.label], already localised) —
 * no app i18n key needed.
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
            val step = field.step?.takeIf { it > 0.0 } ?: DEFAULT_NUMBER_STEP
            val stepCount = ((max - min) / step).roundToInt().coerceAtLeast(1)
            val current = value.toDoubleOrNull()?.coerceIn(min, max) ?: min
            val position = ((current - min) / step).roundToInt().coerceIn(0, stepCount)
            EinkSliderRow(
                label = field.label,
                valueText = formatForStep(current, step),
                position = position,
                stepCount = stepCount,
                // Format with enough decimals for the step so the stored value round-trips to the
                // same notch — otherwise a coarse format pins the slider at its minimum.
                onPosition = { p -> onValueChange(formatForStep(min + p * step, step)) },
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
