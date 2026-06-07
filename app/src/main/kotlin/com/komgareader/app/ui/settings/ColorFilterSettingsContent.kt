package com.komgareader.app.ui.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.request.ImageRequest
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.ui.components.ChoiceRow
import com.komgareader.app.ui.components.EinkInfoDialog
import com.komgareader.app.ui.components.EinkModal
import com.komgareader.app.ui.components.FilteredAsyncImage
import com.komgareader.app.ui.components.SectionHeader
import com.komgareader.app.ui.components.StepperRow
import com.komgareader.app.ui.components.toColorFilterOrNull
import com.komgareader.app.ui.theme.EinkTokens
import com.komgareader.domain.model.ColorProfile

private const val STEP = 0.05f
private val NAV_BUTTON_PLACEHOLDER = 40.dp

/**
 * Farbfilter-Sektion im Master-Detail-Settings-Host (kein eigenes Scaffold — der Host liefert
 * Titel + Scroll). Live-Vorschau (durchblätterbar) + Profil-Auswahl + editierbarer Entwurf.
 */
@Composable
fun ColorFilterSettingsContent(
    query: String = "",
    viewModel: ColorFilterViewModel = hiltViewModel(),
) {
    val s = LocalStrings.current
    val profiles by viewModel.profiles.collectAsState()
    val active by viewModel.active.collectAsState()
    val edit by viewModel.edit.collectAsState()
    val preview by viewModel.preview.collectAsState()
    val canGoBack by viewModel.canGoBack.collectAsState()
    val ctx = LocalContext.current

    var showSaveDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var infoProfile by remember { mutableStateOf<ColorProfile?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(EinkTokens.sectionGap)) {
        // Live-Vorschau: Cover + eng anliegende Blätter-Buttons (Vorheriges nur wenn vorhanden).
        val previewProfile = edit?.let {
            ColorProfile(it.baseProfileId, it.name, it.saturation, it.contrast, it.brightness, it.builtIn)
        } ?: active
        preview?.let { p ->
            val request = remember(p.url) {
                ImageRequest.Builder(ctx).data(p.url)
                    .apply { p.headers.forEach { addHeader(it.key, it.value) } }
                    .crossfade(false).build()
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (canGoBack) {
                    PreviewNavButton(Icons.AutoMirrored.Outlined.ArrowBack, s.colorFilterPrevImage) {
                        viewModel.previousPreview()
                    }
                } else {
                    Box(Modifier.size(NAV_BUTTON_PLACEHOLDER))
                }
                FilteredAsyncImage(
                    model = request,
                    contentDescription = s.colorFilterPreview,
                    contentScale = ContentScale.Crop,
                    colorFilterOverride = previewProfile.toColorFilterOrNull(),
                    useOverride = true,
                    modifier = Modifier
                        .height(180.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(4.dp))
                        .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp)),
                )
                PreviewNavButton(Icons.AutoMirrored.Outlined.ArrowForward, s.colorFilterNextImage) {
                    viewModel.nextPreview()
                }
            }
        }

        SectionHeader(s.colorFilterProfiles)
        profiles.forEach { profile ->
            ChoiceRow(
                label = profile.name,
                selected = profile.id == active.id,
                query = query,
                // Info-Button: Werte des Profils (auch der gesperrten Presets) read-only ansehen.
                trailing = {
                    IconButton(onClick = { infoProfile = profile }) {
                        Icon(Icons.Outlined.Info, contentDescription = profile.name)
                    }
                },
            ) {
                viewModel.setActive(profile.id)
                // Built-ins sind gesperrt → kein Editor; Custom-Profile öffnen den Editor.
                if (profile.builtIn) viewModel.cancelEdit() else viewModel.beginEdit(profile)
            }
        }
        // Neues, editierbares Profil anlegen — die Regler erscheinen darunter,
        // vorbefüllt vom aktiven Profil. Speichern legt es an und wählt es aus.
        ChoiceRow(label = "＋ ${s.colorFilterNewProfile}", selected = false) {
            viewModel.beginNewProfile()
        }

        // Editor nur für editierbare Zustände (neuer Entwurf oder Custom-Profil), nie für Built-ins.
        edit?.takeIf { !it.builtIn }?.let { e ->
            val isNewDraft = e.baseProfileId == 0L
            SectionHeader(s.colorFilterAdjust)
            StepperRow(
                label = s.colorFilterSaturation,
                valueText = format(e.saturation),
                onDecrement = { viewModel.updateSaturation(-STEP) },
                onIncrement = { viewModel.updateSaturation(STEP) },
            )
            StepperRow(
                label = s.colorFilterContrast,
                valueText = format(e.contrast),
                onDecrement = { viewModel.updateContrast(-STEP) },
                onIncrement = { viewModel.updateContrast(STEP) },
            )
            StepperRow(
                label = s.colorFilterBrightness,
                valueText = format(e.brightness),
                onDecrement = { viewModel.updateBrightness(-STEP) },
                onIncrement = { viewModel.updateBrightness(STEP) },
            )

            Column(Modifier.padding(top = 8.dp)) {
                ChoiceRow(label = s.colorFilterSaveAsNew, selected = false) {
                    newName = if (isNewDraft) "" else "${e.name}${s.colorFilterCopySuffix}"
                    showSaveDialog = true
                }
                // Aktualisieren/Löschen nur für ein bereits gespeichertes Custom-Profil.
                if (!isNewDraft) {
                    ChoiceRow(label = s.colorFilterUpdate, selected = false) { viewModel.updateExisting() }
                    ChoiceRow(label = s.colorFilterDelete, selected = false) { viewModel.delete(e.baseProfileId) }
                }
            }
        }
    }

    if (showSaveDialog) {
        EinkModal(
            title = s.colorFilterSaveAsNew,
            onDismiss = { showSaveDialog = false },
            confirmLabel = s.save,
            onConfirm = { viewModel.saveAsNew(newName); showSaveDialog = false },
            dismissLabel = s.cancel,
            confirmEnabled = newName.isNotBlank(),
        ) {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text(s.colorFilterProfileName) },
                singleLine = true,
            )
        }
    }

    // Read-only-Werteansicht (auch für gesperrte Presets wie Go-7), nur X zum Schließen.
    infoProfile?.let { p ->
        EinkInfoDialog(title = p.name, onDismiss = { infoProfile = null }, closeLabel = s.close) {
            InfoValueRow(s.colorFilterSaturation, format(p.saturation))
            InfoValueRow(s.colorFilterContrast, format(p.contrast))
            InfoValueRow(s.colorFilterBrightness, format(p.brightness))
        }
    }
}

@Composable
private fun InfoValueRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Kleiner beschrifteter Pfeil-Button (Icon über Text) zum Durchblättern der Vorschau-Cover. */
@Composable
private fun PreviewNavButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(24.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

private fun format(v: Float): String =
    java.util.Locale.US.let { "%.2f".format(it, v) }.trimEnd('0').trimEnd('.')
