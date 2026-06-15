package com.komgareader.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.documentfile.provider.DocumentFile
import com.komgareader.domain.repository.DownloadRepository
import com.komgareader.domain.repository.DownloadedBook
import com.komgareader.domain.repository.SettingsRepository
import com.komgareader.domain.source.SourceId
import com.komgareader.domain.usecase.detectBookFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** The reader route + the file name for an external open. */
data class ExternalOpenTarget(val route: String, val fileName: String)

/**
 * Wires an externally-opened file (VIEW intent content:// URI) into the existing
 * reader by inserting a TRANSIENT download row under [SourceId.EXTERNAL] (the reader
 * reads content:// rows via the download table). Also imports a copy into the
 * local(=download) folder, and purges the transient rows.
 */
@Singleton
class ExternalBookOpener @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloads: DownloadRepository,
    private val settings: SettingsRepository,
) {
    /**
     * Detect the format, register a transient download row, and return the reader
     * route. Returns null if the URI is not a supported book.
     */
    suspend fun prepareEphemeral(uri: Uri): ExternalOpenTarget? {
        val name = displayName(uri)
        val mime = context.contentResolver.getType(uri)
        val format = detectBookFormat(mime, name) ?: return null
        val bookId = Base64.encodeToString(uri.toString().toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        downloads.put(
            DownloadedBook(
                bookRemoteId = bookId,
                sourceId = SourceId.EXTERNAL,
                seriesRemoteId = bookId,
                title = name ?: "Buch",
                format = format.name.lowercase(),
                localPath = uri.toString(),
                totalPages = 0,
            ),
        )
        val route = "reader/$bookId/${SourceId.EXTERNAL}/0/${format.name}/false/PAGED"
        return ExternalOpenTarget(route, name ?: "Buch")
    }

    /** Copy the URI bytes into the [treeUri] SAF folder. Returns true on success. */
    suspend fun importToFolder(uri: Uri, treeUri: Uri, fileName: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return@runCatching false
            val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val target = tree.createFile(mime, uniqueName(tree, fileName)) ?: return@runCatching false
            context.contentResolver.openInputStream(uri)?.use { input ->
                context.contentResolver.openOutputStream(target.uri)?.use { out -> input.copyTo(out) }
                    ?: return@runCatching false
            } ?: return@runCatching false
            true
        }.getOrDefault(false)
    }

    /** The configured download(=local) folder tree URI, or null if none is set. */
    suspend fun configuredFolder(): Uri? = settings.downloadDir.first()?.let(Uri::parse)

    /** Drop all transient external rows (called on app start and reader exit). */
    suspend fun purgeTransient() = downloads.removeBySourceId(SourceId.EXTERNAL)

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')

    private fun uniqueName(tree: DocumentFile, name: String): String {
        if (tree.findFile(name) == null) return name
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        var i = 1
        while (true) {
            val candidate = if (ext.isEmpty()) "$base ($i)" else "$base ($i).$ext"
            if (tree.findFile(candidate) == null) return candidate
            i++
        }
    }
}
