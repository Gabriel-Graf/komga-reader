package com.komgareader.app.ui.reader

import com.komgareader.domain.repository.SettingsRepository
import com.komgareader.plugin.PluginCategory
import com.komgareader.plugin.host.PluginHost
import com.panela.comiccutter.GeometricPanelSource
import com.panela.comiccutter.MlFilter
import com.panela.comiccutter.MlPanelSource
import com.panela.comiccutter.PanelSource
import com.panela.comiccutter.onnx.OnnxModelRunner
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides the [PanelSource] the comic reader uses to detect panels: the ML detector
 * (a PANEL_MODEL plugin's ONNX model) when [SettingsRepository.useMlDetection] is on and such a
 * plugin is installed, otherwise the geometric fallback. The reader is agnostic to the choice; only
 * the raw panel detection differs. The chosen source is cached (model loading is expensive); any
 * failure (no plugin, ONNX init error) degrades gracefully to [GeometricPanelSource].
 */
@Singleton
class PanelSourceProvider @Inject constructor(
    private val pluginHost: PluginHost,
    private val settings: SettingsRepository,
) {
    @Volatile private var cached: PanelSource? = null

    suspend fun current(): PanelSource {
        cached?.let { return it }
        val source = build()
        cached = source
        return source
    }

    private suspend fun build(): PanelSource {
        if (!settings.useMlDetection.first()) return GeometricPanelSource()
        val bytes = pluginHost.binaryDataPluginBytes(PluginCategory.PANEL_MODEL) ?: return GeometricPanelSource()
        return runCatching {
            MlPanelSource(OnnxModelRunner(bytes), MlFilter(0.25f, 0.7f, 0.0f, null))
        }.getOrDefault(GeometricPanelSource())
    }
}
