package com.komgareader.source.local

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Copies a SAF document into the app cache once (random access from a real File),
 * then serves the cached copy. Size-capped LRU by last-modified time.
 */
class LocalFileCache(
    private val context: Context,
    private val maxBytes: Long = 512L * 1024 * 1024,
) {
    private val dir: File = File(context.cacheDir, "local-source").apply { mkdirs() }

    fun materialize(documentUri: Uri, cacheKey: String): File {
        val target = File(dir, safe(cacheKey))
        if (!target.exists() || target.length() == 0L) {
            context.contentResolver.openInputStream(documentUri)?.use { input ->
                target.outputStream().use { input.copyTo(it) }
            } ?: error("Cannot open local document: $documentUri")
        }
        target.setLastModified(System.currentTimeMillis())
        evictIfNeeded()
        return target
    }

    private fun evictIfNeeded() {
        val files = dir.listFiles()?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        var i = 0
        while (total > maxBytes && i < files.size) {
            total -= files[i].length(); files[i].delete(); i++
        }
    }

    private fun safe(key: String): String = key.replace(Regex("[^A-Za-z0-9._-]"), "_").take(180)
}
