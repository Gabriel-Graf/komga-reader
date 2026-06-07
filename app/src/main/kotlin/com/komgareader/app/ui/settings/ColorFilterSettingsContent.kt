package com.komgareader.app.ui.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.request.ImageRequest
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.ui.components.EinkInfoDialog
import com.komgareader.app.ui.components.EinkModal
import com.komgareader.app.ui.components.FilteredAsyncImage
import com.komgareader.app.ui.components.SectionHeader
import com.komgareader.app.ui.components.toColorFilterOrNull
import com.komgareader.app.ui.theme.EinkTokens
import com.komgareader.domain.model.ColorProfile

private const val STEP = 0.05f

/** Feste Breite der Pfeil-Slots links/rechts — symmetrisch, damit das Cover exakt zentriert bleibt. */
private val NAV_SLOT = 56.dp

/** Höhe der Dropdown-Selektor-Zeile = Höhe des „＋"-Buttons daneben (gleiche Zeile → gleiche Höhe). */
private val SELECTOR_HEIGHT = 44.dp

/**
 * Farbfilter-Sektion im Master-Detail-Settings-Host (kein eigenes Scaffold — der Host liefert
 * Titel + Scroll). Reihenfolge: zentrierte Vorschau → Profil-Selektor (Dropdown + Anlegen-Button)
 * → Editor. Der Editor öffnet sich nur über das Zahnrad bzw. „Neues Profil", nicht beim Auswählen.
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
    var profilesExpanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(EinkTokens.sectionGap)) {
        // Zentrierte Vorschau: Cover mittig, Icon-Pfeile in symmetrischen festen Slots daneben.
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
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(NAV_SLOT), contentAlignment = Alignment.Center) {
                    if (canGoBack) {
                        CompactIcon(Icons.AutoMirrored.Outlined.ArrowBack, s.colorFilterPrevImage) {
                            viewModel.previousPreview()
                        }
                    }
                }
                FilteredAsyncImage(
                    model = request,
                    contentDescription = s.colorFilterPreview,
                    contentScale = ContentScale.Crop,
                    colorFilterOverride = previewProfile.toColorFilterOrNull(),
                    useOverride = true,
                    modifier = Modifier
                        .height(240.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(4.dp))
                        .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp)),
                )
                Box(Modifier.width(NAV_SLOT), contentAlignment = Alignment.Center) {
                    CompactIcon(Icons.AutoMirrored.Outlined.ArrowForward, s.colorFilterNextImage) {
                        viewModel.nextPreview()
                    }
                }
            }
        }

        // Profil-Selektor (zwischen Vorschau und Editor): aufklappbares Dropdown + Anlegen-Button rechts.
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            SectionHeader(s.colorFilterProfiles)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                // Dropdown als EIN umrahmter Block: Selektor + aufgeklappte Liste, Breite = weight(1f).
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .border(EinkTokens.hairline, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(SELECTOR_HEIGHT)
                            .clickable { profilesExpanded = !profilesExpanded }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(active.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        CompactIcon(Icons.Outlined.Info, active.name) { infoProfile = active }
                        Icon(
                            if (profilesExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = null,
                        )
                    }
                    if (profilesExpanded) {
                        HorizontalDivider(
                            thickness = EinkTokens.hairline,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                            profiles.forEach { profile ->
                                ProfileRow(
                                    name = profile.name,
                                    selected = profile.id == active.id,
                                    editable = !profile.builtIn,
                                    onInfo = { infoProfile = profile },
                                    onEdit = {
                                        viewModel.setActive(profile.id)
                                        viewModel.beginEdit(profile)
                                        profilesExpanded = false
                                    },
                                    onSelect = {
                                        // Auswählen aktiviert nur — der Editor öffnet erst übers Zahnrad.
                                        viewModel.setActive(profile.id)
                                        viewModel.cancelEdit()
                                        profilesExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
                // Eigenständiger „Neues Profil"-Button rechts, oben ausgerichtet.
                Box(
                    modifier = Modifier
                        .height(SELECTOR_HEIGHT)
                        .width(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(EinkTokens.hairline, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                        .clickable { viewModel.beginNewProfile() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = s.colorFilterNewProfile, modifier = Modifier.size(24.dp))
                }
            }
        }

        // Editor (Adjust) — erscheint nur beim Anlegen oder über das Zahnrad. Eng gestellt.
        edit?.takeIf { !it.builtIn }?.let { e ->
            val isNewDraft = e.baseProfileId == 0L
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SectionHeader(s.colorFilterAdjust)
                CompactStepperRow(s.colorFilterSaturation, format(e.saturation),
                    { viewModel.updateSaturation(-STEP) }, { viewModel.updateSaturation(STEP) })
                CompactStepperRow(s.colorFilterContrast, format(e.contrast),
                    { viewModel.updateContrast(-STEP) }, { viewModel.updateContrast(STEP) })
                CompactStepperRow(s.colorFilterBrightness, format(e.brightness),
                    { viewModel.updateBrightness(-STEP) }, { viewModel.updateBrightness(STEP) })
                // Aktionen mittig, als umrandete Buttons.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                ) {
                    // Konvention: sekundäre Aktion (Abbrechen/Löschen) links, primäre (Speichern/Aktualisieren) rechts.
                    if (isNewDraft) {
                        // Abbrechen: Editor verbergen, Entwurf verwerfen — das aktive Profil bleibt.
                        OutlinedButton(onClick = { viewModel.cancelEdit() }) { Text(s.cancel) }
                        Button(onClick = { newName = ""; showSaveDialog = true }) { Text(s.save) }
                    } else {
                        OutlinedButton(onClick = { viewModel.delete(e.baseProfileId) }) { Text(s.colorFilterDelete) }
                        Button(onClick = { viewModel.updateExisting() }) { Text(s.colorFilterUpdate) }
                    }
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

/** Kompakte Profil-Zeile (≈40 dp). Zahnrad öffnet den Editor (nur editierbare Profile), Tippen wählt nur aus. */
@Composable
private fun ProfileRow(
    name: String,
    selected: Boolean,
    editable: Boolean,
    onInfo: () -> Unit,
    onEdit: () -> Unit,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            if (selected) Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(4.dp))
        if (editable) {
            CompactIcon(Icons.Outlined.Settings, name, onEdit)
            Spacer(Modifier.width(4.dp))
        }
        CompactIcon(Icons.Outlined.Info, name, onInfo)
    }
}

/** Kompakte ±-Regel-Zeile (≈40 dp) — enger als das geteilte StepperRow (das 48-dp-IconButtons nutzt). */
@Composable
private fun CompactStepperRow(
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
        CompactIcon(Icons.Outlined.Remove, "−", onDecrement)
        Text(
            valueText,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.width(52.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        CompactIcon(Icons.Outlined.Add, "+", onIncrement)
    }
}

/** Antippbares Icon mit kleinem Touch-Bereich (36 dp) statt des 48-dp-IconButton — für enge Zeilen. */
@Composable
private fun CompactIcon(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
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

@Composable
private fun InfoValueRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun format(v: Float): String =
    java.util.Locale.US.let { "%.2f".format(it, v) }.trimEnd('0').trimEnd('.')
