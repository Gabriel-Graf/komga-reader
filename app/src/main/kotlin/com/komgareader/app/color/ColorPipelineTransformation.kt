package com.komgareader.app.color

import android.graphics.Bitmap
import coil.size.Size
import coil.transform.Transformation
import com.komgareader.domain.color.applyPixelPipeline
import com.komgareader.domain.model.ColorProfile

/**
 * Coil-Transformation: wendet die volle E-Ink-Pixel-Pipeline ([applyPixelPipeline]) auf das
 * dekodierte Seiten-Bitmap an. Nur an Reader-Seiten-Requests hängen (nie an Cover). Der
 * [cacheKey] enthält alle Profil-Werte → Coil cacht das Ergebnis pro Bild+Profil (einmal rechnen).
 */
class ColorPipelineTransformation(private val profile: ColorProfile) : Transformation {

    override val cacheKey: String = "colorpipe:" + listOf(
        profile.saturation, profile.contrast, profile.brightness,
        profile.blackPoint, profile.whitePoint, profile.gamma,
        profile.sharpenAmount, profile.sharpenRadius, profile.ditherMode, profile.ditherLevels,
    ).joinToString(":")

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val bmp = if (input.config == Bitmap.Config.ARGB_8888 && input.isMutable) input
        else input.copy(Bitmap.Config.ARGB_8888, true)
        val w = bmp.width; val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        applyPixelPipeline(px, w, h, profile)
        bmp.setPixels(px, 0, w, 0, 0, w, h)
        return bmp
    }

    override fun equals(other: Any?) = other is ColorPipelineTransformation && other.cacheKey == cacheKey
    override fun hashCode() = cacheKey.hashCode()
}
