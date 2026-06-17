package com.komgareader.app.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
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
        val decoded = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
            ?: run { Log.w(TAG, "could not decode image bytes"); return false }
        // The Onyx screensaver only accepts an image at the EXACT device resolution — a cover/photo at
        // its native size (e.g. 720x1024) is silently rejected (no standby update). Fit it onto a
        // full-screen canvas first (letterbox or fill-crop per setting).
        val bitmap = fitToScreen(decoded)
        val path = publishToPictures(bitmap) ?: return false
        return eink.setScreenSaverImage(path)
    }

    /** App-private cache of the last applied original image, for re-fitting on a setting change. */
    private val sourceCache get() = java.io.File(context.filesDir, "screensaver_source")

    /**
     * Draws [src] onto a device-screen-sized bitmap. Per the user's screensaver setting: letterbox
     * (contain, whole image on white) or fill+crop (cover — fills the width, crops top/bottom).
     */
    private suspend fun fitToScreen(src: Bitmap): Bitmap {
        val dm = context.resources.displayMetrics
        val w = dm.widthPixels.coerceAtLeast(1)
        val h = dm.heightPixels.coerceAtLeast(1)
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
     *  Prior `komga_screensaver_*` files (ours + the system's Pictures-root copies) are pruned first. */
    private fun publishToPictures(bitmap: Bitmap): String? {
        val resolver = context.contentResolver
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        // Prune previous screensaver files everywhere so the gallery doesn't accumulate them.
        runCatching {
            resolver.delete(
                collection,
                "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?",
                arrayOf("$FILE_PREFIX%"),
            )
        }
        val uniqueName = "$FILE_PREFIX${System.currentTimeMillis()}.png"
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
        const val FILE_PREFIX = "komga_screensaver_"
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
