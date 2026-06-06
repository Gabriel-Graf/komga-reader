package com.komgareader.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.AutofillNode
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalAutofillTree
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.ui.components.SubPageScaffold

/** Verbindung: Komga-Server konfigurieren (API-Key ODER Benutzer/Passwort) + Status. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ConnectionSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val s = LocalStrings.current
    val server by viewModel.server.collectAsState()

    var nameInput by remember { mutableStateOf("") }
    var urlInput by remember { mutableStateOf("") }
    var apiKeyInput by remember { mutableStateOf("") }
    var usernameInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }

    SubPageScaffold(title = s.settingsConnection, onBack = onBack) {
        Column {
            val statusText = if (server != null) "${s.connected}: ${server!!.name}" else s.notConnected
            Text(statusText, modifier = Modifier.padding(bottom = 12.dp))

            OutlinedTextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                label = { Text(s.serverDisplayName) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                label = { Text(s.serverUrl) },
                placeholder = { Text(s.serverUrlHint) },
                supportingText = { Text(s.serverUrlHelper) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = { apiKeyInput = it },
                label = { Text(s.serverApiKeyOptional) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Text(
                text = s.orSeparator,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 8.dp),
            )
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            val autofill = LocalAutofill.current
            val autofillTree = LocalAutofillTree.current

            val usernameNode = remember {
                AutofillNode(autofillTypes = listOf(AutofillType.Username), onFill = { usernameInput = it })
            }
            autofillTree += usernameNode
            OutlinedTextField(
                value = usernameInput,
                onValueChange = { usernameInput = it },
                label = { Text(s.serverUsername) },
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { usernameNode.boundingBox = it.boundsInWindow() }
                    .onFocusChanged { focus ->
                        autofill?.run {
                            if (focus.isFocused) requestAutofillForNode(usernameNode)
                            else cancelAutofillForNode(usernameNode)
                        }
                    },
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))

            val passwordNode = remember {
                AutofillNode(autofillTypes = listOf(AutofillType.Password), onFill = { passwordInput = it })
            }
            autofillTree += passwordNode
            OutlinedTextField(
                value = passwordInput,
                onValueChange = { passwordInput = it },
                label = { Text(s.serverPassword) },
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { passwordNode.boundingBox = it.boundsInWindow() }
                    .onFocusChanged { focus ->
                        autofill?.run {
                            if (focus.isFocused) requestAutofillForNode(passwordNode)
                            else cancelAutofillForNode(passwordNode)
                        }
                    },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            Spacer(Modifier.height(12.dp))
            Row {
                Button(onClick = {
                    viewModel.saveServer(nameInput, urlInput, apiKeyInput, usernameInput, passwordInput)
                    nameInput = ""; urlInput = ""; apiKeyInput = ""; usernameInput = ""; passwordInput = ""
                }) { Text(s.connect) }
                if (server != null) {
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { viewModel.disconnect() }) { Text(s.disconnect) }
                }
            }
        }
    }
}
