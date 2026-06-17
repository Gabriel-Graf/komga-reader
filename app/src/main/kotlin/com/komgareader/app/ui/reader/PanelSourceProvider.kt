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

private const val PANEL_YOLO_PKG = "com.komgareader.model.panel.yolo"

internal const val DEFAULT_MIN_CONFIDENCE = 0.25f

/** Parses the stored confidence string; falls back to [DEFAULT_MIN_CONFIDENCE] on null/invalid. */
internal fun resolveMinConfidence(stored: String?): Float =
    stored?.toFloatOrNull()?.takeIf { it in 0f..1f } ?: DEFAULT_MIN_CONFIDENCE

/**
 * Provides the [PanelSource] the comic reader uses to detect panels: the ML detector
 * (a PANEL_MODEL plugin's ONNX model) when [SettingsRepository.useMlDetection] is on and such a
 * plugin is installed, otherwise the geometric fallback. The reader is agnostic to the choice; only
 * the raw panel detection differs. The chosen source is cached (model loading is expensive) and
 * invalidated when [SettingsRepository.pluginConfig] for [PANEL_YOLO_PKG]'s `min_confidence` changes.
 * Any failure (no plugin, ONNX init error) degrades gracefully to [GeometricPanelSource].
 */
@Singleton
class PanelSourceProvider @Inject constructor(
    private val pluginHost: PluginHost,
    private val settings: SettingsRepository,
) {
    @Volatile private var cached: PanelSource? = null
    @Volatile private var cachedConfidence: Float = -1f

    suspend fun current(): PanelSource {
        val conf = resolveMinConfidence(settings.pluginConfig(PANEL_YOLO_PKG, "min_confidence").first())
        cached?.let { if (conf == cachedConfidence) return it }
        val source = build(conf)
        cached = source
        cachedConfidence = conf
        return source
    }

    private suspend fun build(minConfidence: Float): PanelSource {
        if (!settings.useMlDetection.first()) return GeometricPanelSource()
        val bytes = pluginHost.binaryDataPluginBytes(PluginCategory.PANEL_MODEL) ?: return GeometricPanelSource()
        return runCatching {
            MlPanelSource(OnnxModelRunner(bytes), MlFilter(minConfidence, 0.7f, 0.0f, null))
        }.getOrDefault(GeometricPanelSource())
    }
}
