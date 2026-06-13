package com.komgareader.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.ui.icons.AppIcons

/**
 * Geteiltes E-Ink-Eingabefeld — ein [OutlinedTextField] mit den projektweit gewünschten
 * Bequemlichkeiten als Trailing-Icons (statt sie in jedem Formular einzeln zu verdrahten,
 * `shared-structure-before-variants`):
 *
 * - **X-Löschen:** bei nicht-leerem Inhalt ein Schließen-Icon, das den ganzen Feldinhalt leert.
 * - **Auge:** bei [isPassword] ein Sichtbar/Unsichtbar-Umschalter (anzeigen/bearbeiten des Werts).
 * - **Pfeil-Bestätigen:** wenn [onConfirm] gesetzt ist, ein Pfeil-rechts als Inline-Bestätigung
 *   (ersetzt einen separaten Button) — zusätzlich auf der Tastatur als IME-„Los".
 *
 * Bei [isPassword] erscheinen Auge **und** X nebeneinander; bei [onConfirm] verdrängt der Pfeil
 * das X (ein einzelnes Bestätigungs-Feld braucht kein Leeren). Das Feld ist immer volle Breite.
 *
 * E-Ink-Invarianten: flach, 1.5px-Rand (OutlinedTextField), keine Animation — alle Zustandswechsel
 * sind sofortig. Beschriftungen kommen aus [LocalStrings] (X/Auge) bzw. vom Aufrufer ([confirmLabel]).
 *
 * Der [modifier] wird auf das Feld gelegt (vor `fillMaxWidth`) — so können Aufrufer z. B. die
 * Autofill-Verdrahtung (`onGloballyPositioned`/`onFocusChanged`) durchreichen.
 */
@Composable
fun EinkTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    supportingText: String? = null,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    onConfirm: (() -> Unit)? = null,
    confirmLabel: String? = null,
) {
    val s = LocalStrings.current
    var revealed by rememberSaveable { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        placeholder = placeholder?.let { { Text(it) } },
        supportingText = supportingText?.let { { Text(it) } },
        visualTransformation = if (isPassword && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isPassword) KeyboardType.Password else keyboardType,
            imeAction = if (onConfirm != null) ImeAction.Go else ImeAction.Default,
        ),
        keyboardActions = KeyboardActions(onGo = { if (value.isNotBlank()) onConfirm?.invoke() }),
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isPassword) {
                    IconButton(onClick = { revealed = !revealed }) {
                        Icon(
                            if (revealed) AppIcons.PasswordHide else AppIcons.PasswordShow,
                            contentDescription = if (revealed) s.hidePassword else s.showPassword,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                when {
                    onConfirm != null && value.isNotBlank() -> IconButton(onClick = onConfirm) {
                        Icon(AppIcons.Forward, contentDescription = confirmLabel, modifier = Modifier.size(20.dp))
                    }
                    onConfirm == null && value.isNotEmpty() -> IconButton(onClick = { onValueChange("") }) {
                        Icon(AppIcons.Close, contentDescription = s.clearInput, modifier = Modifier.size(20.dp))
                    }
                }
            }
        },
    )
}
