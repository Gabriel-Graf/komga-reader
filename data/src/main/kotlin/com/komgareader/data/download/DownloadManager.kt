package com.komgareader.data.download

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.komgareader.domain.repository.DownloadRepository
import com.komgareader.domain.repository.DownloadedBook
import com.komgareader.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Liest die Bytes eines lokal heruntergeladenen Buchs. Schmale, Android-freie Abstraktion
 * (DIP), damit Konsumenten wie [com.komgareader.app.ui.reader.EpubBytesLoader] den lokalen
 * Lesepfad nutzen und unit-testen können, ohne den Android-gebundenen [DownloadManager]
 * konstruieren zu müssen. [DownloadManager] ist die einzige produktive Implementierung.
 */
interface LocalBookBytes {
    fun bytesOf(book: DownloadedBook): ByteArray
}

@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val downloads: DownloadRepository,
    private val settings: SettingsRepository,
) : LocalBookBytes {
    private val resolver: ContentResolver get() = ctx.contentResolver

    /**
     * Schreibt [bytes] in den konfigurierten Speicherort und merkt den Download in der DB.
     *
     * Wenn ein SAF-Tree-URI in den Settings gesetzt ist, wird die Datei dort per
     * DocumentFile angelegt und der resultierende content-URI gespeichert.
     * Sonst fällt es auf [filesDir]/downloads zurück.
     */
    suspend fun store(
        bookRemoteId: String,
        sourceId: Long,
        seriesRemoteId: String,
        title: String,
        format: String,
        totalPages: Int,
        bytes: ByteArray,
        seriesTitle: String = "",
        seriesCoverUrl: String? = null,
    ) = withContext(Dispatchers.IO) {
        val ext = format.lowercase()
        val fileName = "$bookRemoteId.$ext"
        val mimeType = mimeTypeFor(ext)

        val localPath: String = storeBytes(bytes, fileName, mimeType)
        Log.i(TAG, "Gespeichert: $fileName → $localPath")

        downloads.put(
            DownloadedBook(
                bookRemoteId = bookRemoteId,
                sourceId = sourceId,
                seriesRemoteId = seriesRemoteId,
                title = title,
                format = format,
                localPath = localPath,
                totalPages = totalPages,
                seriesTitle = seriesTitle,
                seriesCoverUrl = seriesCoverUrl,
            ),
        )
    }

    private suspend fun storeBytes(bytes: ByteArray, fileName: String, mimeType: String): String {
        val treeDirUri = settings.downloadDir.firstOrNull()
        if (treeDirUri != null) {
            val treeDir = DocumentFile.fromTreeUri(ctx, Uri.parse(treeDirUri))
            checkNotNull(treeDir) { "SAF-Tree-URI nicht erreichbar: $treeDirUri" }
            check(treeDir.canWrite()) { "Kein Schreibzugriff auf $treeDirUri" }
            // Vorhandene Datei gleichen Namens entfernen (Überschreiben-Semantik)
            treeDir.findFile(fileName)?.delete()
            val docFile = treeDir.createFile(mimeType, fileName)
                ?: error("Datei konnte nicht im SAF-Ordner angelegt werden: $fileName")
            resolver.openOutputStream(docFile.uri)?.use { it.write(bytes) }
                ?: error("OutputStream für SAF-URI konnte nicht geöffnet werden: ${docFile.uri}")
            return docFile.uri.toString()
        }

        // Fallback: interner App-Speicher
        val dir = File(ctx.filesDir, "downloads").apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeBytes(bytes)
        return file.absolutePath
    }

    /**
     * Liest die Bytes eines heruntergeladenen Buches.
     * Unterstützt sowohl [localPath] als Dateipfad als auch als `content://`-URI.
     */
    fun readBytes(localPath: String): ByteArray = when {
        localPath.startsWith("content://") -> {
            resolver.openInputStream(Uri.parse(localPath))?.use { it.readBytes() }
                ?: error("InputStream für content-URI nicht erreichbar: $localPath")
        }
        else -> File(localPath).readBytes()
    }

    /** Alias für ReaderViewModel-Kompatibilität. */
    override fun bytesOf(book: DownloadedBook): ByteArray = readBytes(book.localPath)

    suspend fun delete(bookRemoteId: String) = withContext(Dispatchers.IO) {
        downloads.get(bookRemoteId)?.let { deleteFile(it.localPath) }
        downloads.remove(bookRemoteId)
    }

    private fun deleteFile(localPath: String) {
        if (localPath.startsWith("content://")) {
            val docFile = DocumentFile.fromSingleUri(ctx, Uri.parse(localPath))
            docFile?.delete()
        } else {
            File(localPath).delete()
        }
    }

    private fun mimeTypeFor(ext: String): String = when (ext) {
        "cbz" -> "application/zip"
        "cbr" -> "application/x-cbr"
        "epub" -> "application/epub+zip"
        "pdf" -> "application/pdf"
        else -> "application/octet-stream"
    }

    private companion object {
        const val TAG = "DownloadManager"
    }
}
