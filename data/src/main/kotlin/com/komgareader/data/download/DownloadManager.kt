package com.komgareader.data.download

import android.content.Context
import com.komgareader.domain.repository.DownloadRepository
import com.komgareader.domain.repository.DownloadedBook
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val downloads: DownloadRepository,
) {
    /**
     * Schreibt [bytes] in den lokalen App-Speicher und merkt den Download in der DB.
     * Die Bytes liefert der Aufrufer (KomgaSource lebt im :app-Layer).
     */
    suspend fun store(
        bookRemoteId: String,
        sourceId: Long,
        seriesRemoteId: String,
        title: String,
        format: String,
        totalPages: Int,
        bytes: ByteArray,
    ) = withContext(Dispatchers.IO) {
        val dir = File(ctx.filesDir, "downloads").apply { mkdirs() }
        val ext = format.lowercase()
        val file = File(dir, "$bookRemoteId.$ext")
        file.writeBytes(bytes)
        downloads.put(
            DownloadedBook(
                bookRemoteId = bookRemoteId,
                sourceId = sourceId,
                seriesRemoteId = seriesRemoteId,
                title = title,
                format = format,
                localPath = file.absolutePath,
                totalPages = totalPages,
            ),
        )
    }

    suspend fun delete(bookRemoteId: String) = withContext(Dispatchers.IO) {
        downloads.get(bookRemoteId)?.let { File(it.localPath).delete() }
        downloads.remove(bookRemoteId)
    }

    fun bytesOf(d: DownloadedBook): ByteArray = File(d.localPath).readBytes()
}
