package com.komgareader.app.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.komgareader.domain.eink.EinkController
import com.komgareader.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Installs an image as the device standby/screensaver via the device seam
 * ([EinkController.setScreenSaverImage]). The Onyx system screensaver process runs as a different
 * uid and cannot read app-private storage (`filesDir` → "Image file not exists!") nor the
 * FUSE-locked `Android/data` dir (EACCES). So the image is published into the shared **Pictures**
 * collection via MediaStore — those media files are world-readable — and the resolved absolute path
 * is handed to the controller. No-op (returns false) on hardware without a controllable screensaver.
 *
 * Reuses one MediaStore entry (deletes the prior one) so book covers don't pile up in the gallery.
 */
@Singleton
class ScreenSaverManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val eink: EinkController,
    private val settings: SettingsRepository,
) {
    /** Reads image bytes from a content [uri] (e.g. a user-picked file) and installs them. */
    suspend fun applyUri(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }
            .getOrNull() ?: return@withContext false
        applyBytes(bytes)
    }

    /** Decodes [bytes] to a bitmap, fits it to the screen, publishes it, and sets it as the screensaver.
     *  The ORIGINAL (un-fitted) bytes are cached so [reapply] can re-fit them when the fill/crop setting
     *  changes — even in BOOK_COVER mode, where Settings has no "current book" to re-fetch. */
    suspend fun applyBytes(bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        runCatching { sourceCache.writeBytes(bytes) }
        applyCached(bytes)
    }

    /** Re-fits and re-publishes the last applied source with the CURRENT fill/crop setting. No-op if
     *  nothing was ever set. Used when the user toggles fill/crop so the change takes effect at once
     *  regardless of mode (custom or book cover). */
    suspend fun reapply(): Boolean = withContext(Dispatchers.IO) {
        val bytes = runCatching { sourceCache.takeIf { it.exists() }?.readBytes() }.getOrNull()
            ?: return@withContext false
        applyCached(bytes)
    }

    private suspend fun applyCached(bytes: ByteArray): Boolean {
        // Only devices with a controllable standby do anything here. With BOOK_COVER now the default,
        // this stops a non-E-Ink device (NoOp controller) from pointlessly decoding + publishing a
        // cover into the shared gallery on every book open.
        if (!eink.capabilities.hasEink) return false
        val decoded = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
            ?: run { Log.w(TAG, "could not decode image bytes"); return false }
        Log.i(TAG, "source cover decoded ${decoded.width}x${decoded.height} (${bytes.size} bytes)")
        // The Onyx screensaver only accepts an image at the EXACT device resolution — a cover/photo at
        // its native size (e.g. 720x1024) is silently rejected (no standby update). Fit it onto a
        // full-screen canvas first (letterbox or fill-crop per setting).
        val bitmap = enhanceForEink(fitToScreen(decoded))
        val ssPath = publishToPictures(bitmap, PREFIX_SCREENSAVER) ?: return false
        val ssOk = eink.setScreenSaverImage(ssPath)
        // Mirror the same image as the power-off screen, but with a "Power Off" script label added in the
        // lower third (the standby has no label). Best-effort: a power-off failure must not fail the
        // screensaver result.
        runCatching {
            val poPath = publishToPictures(drawPowerOffLabel(bitmap), PREFIX_POWEROFF)
            if (poPath != null) eink.setPowerOffImage(poPath)
        }
        return ssOk
    }

    /** Script typeface for the power-off label (bundled Lobster Two, OFL-1.1); falls back to the system cursive. */
    private val scriptTypeface: Typeface by lazy {
        runCatching { Typeface.createFromAsset(context.assets, "fonts/LobsterTwo-Italic.otf") }.getOrNull()
            ?: Typeface.create("cursive", Typeface.ITALIC)
    }

    /**
     * Returns a copy of [src] with a "Power Off" script label drawn centered in the lower third. Legible
     * on ANY background WITHOUT a solid box: a white fill over a thick black outline (readable on both
     * light and dark areas) plus a soft drop shadow for separation on busy/mid-tone backgrounds.
     */
    private fun drawPowerOffLabel(src: Bitmap): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val w = out.width.toFloat()
        val h = out.height.toFloat()
        val text = "Power Off"
        val size = w * 0.13f
        val cx = w / 2f
        val cy = h * 0.82f // lower third
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = scriptTypeface
            textSize = size
            textAlign = Paint.Align.CENTER
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = size * 0.08f
            strokeJoin = Paint.Join.ROUND
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = scriptTypeface
            textSize = size
            textAlign = Paint.Align.CENTER
            color = Color.WHITE
            style = Paint.Style.FILL
            setShadowLayer(size * 0.18f, 0f, size * 0.04f, Color.argb(200, 0, 0, 0))
        }
        // Baseline so the glyphs are vertically centered on cy.
        val baseline = cy - (fill.descent() + fill.ascent()) / 2f
        Canvas(out).apply {
            drawText(text, cx, baseline, stroke) // black outline underneath
            drawText(text, cx, baseline, fill) // white fill + soft shadow on top
        }
        return out
    }

    /**
     * Pre-processes the standby image to survive the Onyx E-Ink standby renderer, which softens and
     * mutes a colour image (downscale + dither on a Kaleido panel) — a loss we cannot change through the
     * SDK. Compensate at the source: a contrast + saturation lift so colours still read after the panel
     * mutes them, then a mild unsharp (Laplacian) pass so edges stay crisp through the dither. Tuned
     * conservatively to avoid halos/banding on E-Ink. Runs on [Dispatchers.IO] (called from there).
     */
    private fun enhanceForEink(src: Bitmap): Bitmap = sharpen(boostContrastSaturation(src))

    private fun boostContrastSaturation(src: Bitmap): Bitmap {
        val c = 1.18f // contrast
        val t = (1f - c) * 127.5f
        val matrix = ColorMatrix().apply {
            setSaturation(1.35f)
            postConcat(
                ColorMatrix(
                    floatArrayOf(
                        c, 0f, 0f, 0f, t,
                        0f, c, 0f, 0f, t,
                        0f, 0f, c, 0f, t,
                        0f, 0f, 0f, 1f, 0f,
                    ),
                ),
            )
        }
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(src, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) })
        return out
    }

    /**
     * Light unsharp mask via a 3×3 Laplacian kernel (center 1+4*amount, 4-neighbours -amount). Borders
     * are copied through unchanged. Single pass over the pixel buffer; clamps each channel to 0..255.
     */
    private fun sharpen(src: Bitmap, amount: Float = 0.6f): Bitmap {
        val w = src.width
        val h = src.height
        if (w < 3 || h < 3) return src
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val out = px.copyOf()
        val center = 1f + 4f * amount
        for (y in 1 until h - 1) {
            val row = y * w
            for (x in 1 until w - 1) {
                val i = row + x
                val cP = px[i]; val up = px[i - w]; val dn = px[i + w]; val lf = px[i - 1]; val rt = px[i + 1]
                val r = (((cP shr 16) and 0xFF) * center - (((up shr 16) and 0xFF) + ((dn shr 16) and 0xFF) + ((lf shr 16) and 0xFF) + ((rt shr 16) and 0xFF)) * amount)
                val g = (((cP shr 8) and 0xFF) * center - (((up shr 8) and 0xFF) + ((dn shr 8) and 0xFF) + ((lf shr 8) and 0xFF) + ((rt shr 8) and 0xFF)) * amount)
                val b = (((cP) and 0xFF) * center - (((up) and 0xFF) + ((dn) and 0xFF) + ((lf) and 0xFF) + ((rt) and 0xFF)) * amount)
                // Keep the source alpha (covers are opaque; this is defensive).
                out[i] = (cP and 0xFF000000.toInt()) or
                    (r.toInt().coerceIn(0, 255) shl 16) or
                    (g.toInt().coerceIn(0, 255) shl 8) or
                    b.toInt().coerceIn(0, 255)
            }
        }
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(out, 0, w, 0, 0, w, h)
        return result
    }

    /** App-private cache of the last applied original image, for re-fitting on a setting change. */
    private val sourceCache get() = java.io.File(context.filesDir, "screensaver_source")

    /**
     * Draws [src] onto a device-screen-sized bitmap. Per the user's screensaver setting: letterbox
     * (contain, whole image on white) or fill+crop (cover — fills the width, crops top/bottom).
     */
    private suspend fun fitToScreen(src: Bitmap): Bitmap {
        val dm = context.resources.displayMetrics
        // The Onyx standby canvas is ALWAYS portrait, independent of the app's current orientation.
        // Build the target portrait (short edge = width, long edge = height) so the published image is
        // never landscape — otherwise, when the app happens to be rotated, the standby shows it sideways.
        val w = minOf(dm.widthPixels, dm.heightPixels).coerceAtLeast(1)
        val h = maxOf(dm.widthPixels, dm.heightPixels).coerceAtLeast(1)
        val cover = settings.screenSaverFillCrop.first()
        if (!cover && src.width == w && src.height == h) return src
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(out).apply {
            drawColor(Color.WHITE)
            val r = fitCentered(src.width, src.height, w, h, cover = cover)
            drawBitmap(src, null, Rect(r.left, r.top, r.right, r.bottom), Paint(Paint.FILTER_BITMAP_FLAG))
        }
        return out
    }

    /** Writes [bitmap] as a PNG into Pictures/KomgaReader under a UNIQUE name and returns its absolute
     *  filesystem path, or null on failure. The name must be unique per set: the Onyx screensaver
     *  importer dedupes by source path — reusing one filename makes it skip every set after the first
     *  (verified on device: the system stopped re-copying the file), so the live standby never updated.
     *  Prior files of the same [prefix] (ours + the system's Pictures-root copies) are pruned first. */
    private fun publishToPictures(bitmap: Bitmap, prefix: String): String? {
        val resolver = context.contentResolver
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        // Prune previous files of this kind everywhere so the gallery doesn't accumulate them.
        runCatching {
            resolver.delete(
                collection,
                "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?",
                arrayOf("$prefix%"),
            )
        }
        val uniqueName = "$prefix${System.currentTimeMillis()}.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, uniqueName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, RELATIVE_DIR)
        }
        val uri = resolver.insert(collection, values) ?: run { Log.e(TAG, "MediaStore insert failed"); return null }
        val wrote = runCatching {
            resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: false
        }.getOrDefault(false)
        if (wrote != true) { runCatching { resolver.delete(uri, null, null) }; return null }
        return resolver.query(uri, arrayOf(MediaStore.Images.Media.DATA), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }

    private companion object {
        const val TAG = "ScreenSaverManager"
        const val PREFIX_SCREENSAVER = "komga_screensaver_"
        const val PREFIX_POWEROFF = "komga_poweroff_"
        const val RELATIVE_DIR = "Pictures/KomgaReader"
    }
}

/** A destination rectangle (pixels). */
internal data class FitRect(val left: Int, val top: Int, val right: Int, val bottom: Int)

/**
 * Computes the centered destination rectangle for a [srcW]x[srcH] image inside a [dstW]x[dstH]
 * target. [cover] = false → aspect-fit ("contain", letterboxed, whole image visible); [cover] = true
 * → aspect-fill ("cover", fills the target and crops the overflow — the rect may extend past the
 * target, which the caller's canvas clips). Pure (no Android types) so the logic is unit-testable.
 */
internal fun fitCentered(srcW: Int, srcH: Int, dstW: Int, dstH: Int, cover: Boolean = false): FitRect {
    if (srcW <= 0 || srcH <= 0) return FitRect(0, 0, dstW, dstH)
    val scale = if (cover) {
        maxOf(dstW.toFloat() / srcW, dstH.toFloat() / srcH)
    } else {
        minOf(dstW.toFloat() / srcW, dstH.toFloat() / srcH)
    }
    val w = (srcW * scale).toInt()
    val h = (srcH * scale).toInt()
    val left = (dstW - w) / 2
    val top = (dstH - h) / 2
    return FitRect(left, top, left + w, top + h)
}
