package com.komgareader.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.komgareader.app.ui.components.EinkOutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.hilt.navigation.compose.hiltViewModel
import coil.request.ImageRequest
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.ui.components.EinkInfoDialog
import com.komgareader.app.ui.components.EinkModal
import com.komgareader.app.ui.components.FilteredReaderAsyncImage
import com.komgareader.app.ui.components.SectionHeader
import com.komgareader.app.ui.icons.AppIcons
import com.komgareader.app.ui.theme.EinkTokens
import com.komgareader.domain.model.ColorProfile
import com.komgareader.domain.model.DitherMode

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
    var showDitherInfo by remember { mutableStateOf(false) }
    var profilesExpanded by remember { mutableStateOf(false) }
    var selectorSize by remember { mutableStateOf(IntSize.Zero) }

    // Zentrierte Vorschau: Cover mittig, Icon-Pfeile in symmetrischen festen Slots daneben.
    val previewProfile = edit?.let {
        ColorProfile(
            id = it.baseProfileId, name = it.name,
            saturation = it.saturation, contrast = it.contrast, brightness = it.brightness,
            blackPoint = it.blackPoint, whitePoint = it.whitePoint, gamma = it.gamma,
            sharpenAmount = it.sharpenAmount, sharpenRadius = it.sharpenRadius,
            ditherMode = it.ditherMode, ditherLevels = it.ditherLevels, builtIn = it.builtIn,
        )
    } ?: active

    // Vorschau-Cover — bleibt oben gepinnt (scrollt nicht mit den Reglern weg).
    val cover: @Composable () -> Unit = {
        preview?.let { p ->
            val request = remember(p) {
                ImageRequest.Builder(ctx).data(p)
                    .crossfade(false).build()
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(NAV_SLOT), contentAlignment = Alignment.Center) {
                    if (canGoBack) {
                        CompactIcon(AppIcons.Back, s.colorFilterPrevImage) {
                            viewModel.previousPreview()
                        }
                    }
                }
                FilteredReaderAsyncImage(
                    model = request,
                    contentDescription = s.colorFilterPreview,
                    contentScale = ContentScale.Crop,
                    profileOverride = previewProfile,
                    modifier = Modifier
                        .height(240.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(4.dp))
                        .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp)),
                )
                Box(Modifier.width(NAV_SLOT), contentAlignment = Alignment.Center) {
                    CompactIcon(AppIcons.Forward, s.colorFilterNextImage) {
                        viewModel.nextPreview()
                    }
                }
            }
        }
    }

    // Profil-Selektor + Editor — kann lang werden, deshalb der scrollbare Teil.
    val controls: @Composable () -> Unit = {
        // Profil-Selektor (zwischen Vorschau und Editor): aufklappbares Dropdown + Anlegen-Button rechts.
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            SectionHeader(s.colorFilterProfiles)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                // Selektor (collapsed); die aufgeklappte Liste liegt als Popup DARÜBER (kein Layout-Shift).
                Box(modifier = Modifier.weight(1f).onGloballyPositioned { selectorSize = it.size }) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(SELECTOR_HEIGHT)
                            .clip(RoundedCornerShape(8.dp))
                            .border(EinkTokens.hairline, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                            .clickable { profilesExpanded = !profilesExpanded }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(active.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        CompactIcon(AppIcons.Info, active.name) { infoProfile = active }
                        Icon(
                            if (profilesExpanded) AppIcons.ChevronUp else AppIcons.ChevronDown,
                            contentDescription = null,
                        )
                    }
                    if (profilesExpanded) {
                        Popup(
                            alignment = Alignment.TopStart,
                            // direkt unter den Selektor; überlappt den Inhalt darunter statt ihn zu schieben.
                            offset = IntOffset(0, selectorSize.height),
                            onDismissRequest = { profilesExpanded = false },
                            properties = PopupProperties(focusable = true),
                        ) {
                            Surface(
                                modifier = Modifier
                                    .width(with(LocalDensity.current) { selectorSize.width.toDp() })
                                    .border(EinkTokens.hairline, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                            ) {
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
                    Icon(AppIcons.Plus, contentDescription = s.colorFilterNewProfile, modifier = Modifier.size(24.dp))
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
                SectionHeader(s.colorFilterAdvanced)
                CompactStepperRow(s.colorFilterBlackPoint, format(e.blackPoint),
                    { viewModel.updateBlackPoint(-STEP) }, { viewModel.updateBlackPoint(STEP) })
                CompactStepperRow(s.colorFilterWhitePoint, format(e.whitePoint),
                    { viewModel.updateWhitePoint(-STEP) }, { viewModel.updateWhitePoint(STEP) })
                CompactStepperRow(s.colorFilterGamma, format(e.gamma),
                    { viewModel.updateGamma(-STEP) }, { viewModel.updateGamma(STEP) })
                CompactStepperRow(s.colorFilterSharpen, format(e.sharpenAmount),
                    { viewModel.updateSharpen(-0.1f) }, { viewModel.updateSharpen(0.1f) })
                CompactStepperRow(s.colorFilterSharpenRadius, e.sharpenRadius.toString(),
                    { viewModel.updateSharpenRadius(-1) }, { viewModel.updateSharpenRadius(1) })
                DitherSelectorRow(
                    selected = e.ditherMode,
                    labels = Triple(s.colorFilterDitherNone, s.colorFilterDitherFloyd, s.colorFilterDitherOrdered),
                    label = s.colorFilterDither,
                    onInfo = { showDitherInfo = true },
                    onSelect = { viewModel.setDitherMode(it) },
                )
                if (e.ditherMode != DitherMode.NONE) {
                    CompactStepperRow(s.colorFilterDitherLevels, e.ditherLevels.toString(),
                        { viewModel.updateDitherLevels(-2) }, { viewModel.updateDitherLevels(2) })
                }
                Text(
                    s.colorFilterReaderOnlyHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                // Aktionen mittig, als umrandete Buttons.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                ) {
                    // Konvention: sekundäre Aktion (Abbrechen/Löschen) links, primäre (Speichern/Aktualisieren) rechts.
                    if (isNewDraft) {
                        // Abbrechen: Editor verbergen, Entwurf verwerfen — das aktive Profil bleibt.
                        EinkOutlinedButton(onClick = { viewModel.cancelEdit() }) { Text(s.cancel) }
                        Button(onClick = { newName = ""; showSaveDialog = true }) { Text(s.save) }
                    } else {
                        EinkOutlinedButton(onClick = { viewModel.delete(e.baseProfileId) }) { Text(s.colorFilterDelete) }
                        Button(onClick = { viewModel.updateExisting() }) { Text(s.colorFilterUpdate) }
                    }
                }
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxHeight == Dp.Infinity) {
            // Unbegrenzte Höhe (Phone-Accordion in der Scroll-Liste): einfacher Stapel.
            Column(verticalArrangement = Arrangement.spacedBy(EinkTokens.sectionGap)) {
                cover()
                controls()
            }
        } else {
            // Begrenzte Höhe (Master-Detail): Cover oben gepinnt, nur die Regler scrollen.
            Column(Modifier.fillMaxSize()) {
                cover()
                Spacer(Modifier.height(EinkTokens.sectionGap))
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(EinkTokens.sectionGap),
                ) {
                    controls()
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
            InfoValueRow(s.colorFilterBlackPoint, format(p.blackPoint))
            InfoValueRow(s.colorFilterWhitePoint, format(p.whitePoint))
            InfoValueRow(s.colorFilterGamma, format(p.gamma))
            InfoValueRow(s.colorFilterSharpen, format(p.sharpenAmount))
            InfoValueRow(s.colorFilterSharpenRadius, p.sharpenRadius.toString())
            InfoValueRow(s.colorFilterDither, when (p.ditherMode) {
                DitherMode.NONE -> s.colorFilterDitherNone
                DitherMode.FLOYD_STEINBERG -> s.colorFilterDitherFloyd
                DitherMode.ORDERED -> s.colorFilterDitherOrdered
            })
            if (p.ditherMode != DitherMode.NONE) {
                InfoValueRow(s.colorFilterDitherLevels, p.ditherLevels.toString())
            }
        }
    }

    // Read-only-Erklärung zu Dithering + den Modi (über das Info-Icon der Dither-Zeile).
    if (showDitherInfo) {
        EinkInfoDialog(title = s.colorFilterDither, onDismiss = { showDitherInfo = false }, closeLabel = s.close) {
            Text(s.colorFilterDitherAbout, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            DitherModeInfo(s.colorFilterDitherNone, s.colorFilterDitherNoneDesc)
            DitherModeInfo(s.colorFilterDitherFloyd, s.colorFilterDitherFloydDesc)
            DitherModeInfo(s.colorFilterDitherOrdered, s.colorFilterDitherOrderedDesc)
            Spacer(Modifier.height(4.dp))
            Text(
                s.colorFilterDitherLevelsAbout,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Ein Modus-Block im Dither-Info-Modal: fetter Name + Beschreibung. */
@Composable
private fun DitherModeInfo(name: String, description: String) {
    Column(Modifier.padding(bottom = 10.dp)) {
        Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            if (selected) Icon(AppIcons.Check, contentDescription = null, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(4.dp))
        if (editable) {
            CompactIcon(AppIcons.Settings, name, onEdit)
            Spacer(Modifier.width(4.dp))
        }
        CompactIcon(AppIcons.Info, name, onInfo)
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
        CompactIcon(AppIcons.Minus, "−", onDecrement)
        Text(
            valueText,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.width(52.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        CompactIcon(AppIcons.Plus, "+", onIncrement)
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

/** Dither-Auswahl als drei umrandete Segmente (Aus / Floyd-Steinberg / Ordered) — E-Ink-flach. */
@Composable
private fun DitherSelectorRow(
    selected: DitherMode,
    labels: Triple<String, String, String>,
    label: String,
    onInfo: () -> Unit,
    onSelect: (DitherMode) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        CompactIcon(AppIcons.Info, label, onInfo)
        Spacer(Modifier.weight(1f))
        val modes = listOf(
            DitherMode.NONE to labels.first,
            DitherMode.FLOYD_STEINBERG to labels.second,
            DitherMode.ORDERED to labels.third,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            modes.forEach { (mode, text) ->
                val isActive = mode == selected
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .then(
                            if (isActive) Modifier.background(MaterialTheme.colorScheme.primary)
                            else Modifier.border(EinkTokens.hairline, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp)),
                        )
                        .clickable { onSelect(mode) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}
