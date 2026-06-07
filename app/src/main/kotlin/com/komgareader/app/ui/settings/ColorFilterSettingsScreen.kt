package com.komgareader.app.ui.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.request.ImageRequest
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.ui.components.ChoiceRow
import com.komgareader.app.ui.components.EinkModal
import com.komgareader.app.ui.components.FilteredAsyncImage
import com.komgareader.app.ui.components.SectionHeader
import com.komgareader.app.ui.components.StepperRow
import com.komgareader.app.ui.components.SubPageScaffold
import com.komgareader.app.ui.components.toColorFilterOrNull
import com.komgareader.domain.model.ColorProfile

private const val STEP = 0.05f

@Composable
fun ColorFilterSettingsScreen(
    onBack: () -> Unit,
    viewModel: ColorFilterViewModel = hiltViewModel(),
) {
    val s = LocalStrings.current
    val profiles by viewModel.profiles.collectAsState()
    val active by viewModel.active.collectAsState()
    val edit by viewModel.edit.collectAsState()
    val preview by viewModel.preview.collectAsState()
    val ctx = LocalContext.current

    var showSaveDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    SubPageScaffold(title = s.settingsColorFilter, onBack = onBack) {
        val previewProfile = edit?.let {
            ColorProfile(it.baseProfileId, it.name, it.saturation, it.contrast, it.brightness, it.builtIn)
        } ?: active
        preview?.let { p ->
            val request = remember(p.url) {
                ImageRequest.Builder(ctx).data(p.url)
                    .apply { p.headers.forEach { addHeader(it.key, it.value) } }
                    .crossfade(false).build()
            }
            Box(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                FilteredAsyncImage(
                    model = request,
                    contentDescription = s.colorFilterPreview,
                    contentScale = ContentScale.Fit,
                    colorFilterOverride = previewProfile.toColorFilterOrNull(),
                    useOverride = true,
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .aspectRatio(2f / 3f)
                        .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp)),
                )
            }
        }

        SectionHeader(s.colorFilterProfiles)
        profiles.forEach { profile ->
            ChoiceRow(label = profile.name, selected = profile.id == active.id) {
                viewModel.setActive(profile.id)
                viewModel.beginEdit(profile)
            }
        }

        edit?.let { e ->
            SectionHeader(s.colorFilterAdjust)
            StepperRow(
                label = s.colorFilterSaturation,
                valueText = format(e.saturation),
                onDecrement = { viewModel.updateSaturation(-STEP) },
                onIncrement = { viewModel.updateSaturation(STEP) },
                enabled = !e.builtIn,
            )
            StepperRow(
                label = s.colorFilterContrast,
                valueText = format(e.contrast),
                onDecrement = { viewModel.updateContrast(-STEP) },
                onIncrement = { viewModel.updateContrast(STEP) },
                enabled = !e.builtIn,
            )
            StepperRow(
                label = s.colorFilterBrightness,
                valueText = format(e.brightness),
                onDecrement = { viewModel.updateBrightness(-STEP) },
                onIncrement = { viewModel.updateBrightness(STEP) },
                enabled = !e.builtIn,
            )

            Column(Modifier.padding(top = 8.dp)) {
                ChoiceRow(label = s.colorFilterSaveAsNew, selected = false) {
                    newName = if (e.builtIn) "${e.name}${s.colorFilterCopySuffix}" else e.name
                    showSaveDialog = true
                }
                if (!e.builtIn) {
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
}

private fun format(v: Float): String =
    java.util.Locale.US.let { "%.2f".format(it, v) }.trimEnd('0').trimEnd('.')
