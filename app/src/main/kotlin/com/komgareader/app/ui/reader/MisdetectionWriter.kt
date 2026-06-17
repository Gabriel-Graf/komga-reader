package com.komgareader.app.ui.reader

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Writes a page image (PNG) and a sidecar JSON into a SAF tree folder.
 *
 * The sidecar file follows the mllabeltool naming convention: `<name>.png.json`.
 * Collision-safe: if `<base>.png` already exists the next free suffix `_2`, `_3`, … is used.
 */
class MisdetectionWriter(private val context: Context) {

    /**
     * Writes [bitmap] as a lossless PNG and [sidecarJson] as `<name>.png.json` into the SAF
     * tree at [treeUri].
     *
     * @return `true` when the PNG (and the sidecar, if creation succeeded) was written without
     *         error; `false` on any I/O failure.
     */
    suspend fun write(
        treeUri: String,
        baseName: String,
        bitmap: Bitmap,
        sidecarJson: String,
    ): Boolean = withContext(Dispatchers.IO) {
        val dir = DocumentFile.fromTreeUri(context, Uri.parse(treeUri))
            ?: return@withContext false
        val name = uniqueBase(dir, baseName)

        val png = dir.createFile("image/png", "$name.png")
            ?: return@withContext false
        val pngOk = context.contentResolver.openOutputStream(png.uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        } ?: false
        if (!pngOk) return@withContext false

        // Sidecar creation failure is non-fatal: the PNG is already saved and is the primary
        // artefact.  Return true so the user gets a success signal.
        val sidecar = dir.createFile("application/json", "$name.png.json")
            ?: return@withContext true
        context.contentResolver.openOutputStream(sidecar.uri)?.use { out ->
            out.write(sidecarJson.toByteArray(Charsets.UTF_8))
        }
        true
    }

    /** Returns [base] if `<base>.png` does not exist yet, otherwise `<base>_2`, `_3`, … */
    private fun uniqueBase(dir: DocumentFile, base: String): String {
        if (dir.findFile("$base.png") == null) return base
        var i = 2
        while (dir.findFile("${base}_$i.png") != null) i++
        return "${base}_$i"
    }
}
