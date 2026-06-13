package com.komgareader.app.ui.plugins

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.request.ImageRequest
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.ui.components.EinkInfoDialog
import com.komgareader.app.ui.components.FilteredAsyncImage
import com.komgareader.app.ui.components.LocalEinkMode
import com.komgareader.data.plugin.repo.BrowserRow
import com.komgareader.data.plugin.repo.PluginKind
import com.komgareader.data.plugin.repo.resolveRepoUrl
import com.mikepenz.markdown.coil2.Coil2ImageTransformerImpl
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.model.markdownAnimations

/**
 * Plugin info modal for a discovered repo entry: header (name/type/version + optional license),
 * optional preview image, and the rendered README (remote images enabled) with a description
 * fallback. Onyx look via [EinkInfoDialog]; motion is host-gated (no content-size animation on
 * E-Ink, no image crossfade).
 */
@Composable
fun PluginInfoModal(
    row: BrowserRow,
    readme: ReadmeState,
    onDismiss: () -> Unit,
) {
    val s = LocalStrings.current
    val ctx = LocalContext.current
    val eink = LocalEinkMode.current
    val entry = row.item.entry
    val typeLabel = when (row.item.kind) {
        PluginKind.SOURCE -> s.pluginTabSourceLabel
        PluginKind.PRESET -> s.pluginTabPresetLabel
        PluginKind.LANGUAGE -> s.pluginTabLanguageLabel
        PluginKind.READER_PRESET -> s.pluginTabReaderPresetLabel
        PluginKind.UI_PACK -> s.pluginTabUiPackLabel
    }

    EinkInfoDialog(title = entry.name, onDismiss = onDismiss, closeLabel = s.close) {
        Text(
            "$typeLabel · v${entry.versionName}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (entry.license.isNotBlank()) {
            Text(
                "${s.pluginInfoLicense}: ${entry.license}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (entry.previewUrl.isNotBlank()) {
            val url = resolveRepoUrl(row.item.repoUrl, entry.previewUrl)
            FilteredAsyncImage(
                model = ImageRequest.Builder(ctx).data(url).crossfade(false).build(),
                contentDescription = entry.name,
                modifier = Modifier.fillMaxWidth().height(120.dp),
            )
        }
        when (readme) {
            is ReadmeState.Loading -> Text(
                s.pluginInfoLoadingReadme,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            is ReadmeState.Loaded -> Markdown(
                content = readme.markdown,
                imageTransformer = Coil2ImageTransformerImpl,
                animations = markdownAnimations(animateTextSize = { if (eink) this else animateContentSize() }),
                modifier = Modifier.fillMaxWidth(),
            )
            is ReadmeState.Empty -> Text(
                entry.description.ifBlank { s.pluginInfoNoDescription },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
