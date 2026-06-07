package com.komgareader.app.ui.reader

import com.komgareader.app.data.KomgaSourceProvider
import com.komgareader.data.download.DownloadManager
import com.komgareader.domain.repository.DownloadRepository
import com.komgareader.domain.repository.ServerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Lädt die rohen EPUB-Bytes eines Buchs über **denselben** Mechanismus wie der
 * paginierte Reader: zuerst der lokale Download (offline-first), sonst der Stream
 * von der aktiven Quelle. Zentralisiert das Byte-Holen, damit der Novel-Reader
 * keinen eigenen Abrufpfad erfindet (siehe `source-extensibility`).
 */
class EpubBytesLoader @Inject constructor(
    private val servers: ServerRepository,
    private val sourceProvider: KomgaSourceProvider,
    private val downloadRepository: DownloadRepository,
    private val downloadManager: DownloadManager,
) {

    /** Roh-Bytes des Buchs [bookId]; lokaler Download bevorzugt, außer [forceStream]. */
    suspend fun load(bookId: String, forceStream: Boolean): ByteArray = withContext(Dispatchers.IO) {
        if (!forceStream) {
            downloadRepository.get(bookId)?.let { local ->
                return@withContext downloadManager.bytesOf(local)
            }
        }
        val source = sourceProvider.from(servers.config.first())
            ?: error("Kein Server verbunden.")
        source.downloadFile(bookId)
    }

    /**
     * Server-Lesefortschritt als relative Position (0.0..1.0) — best-effort, offline 0.
     * Im Reflow-Layout hängt die Seitenzahl vom Viewport ab, daher wird der Start
     * über den Fortschrittsanteil statt einen Seitenindex bestimmt.
     */
    suspend fun startProgressFraction(bookId: String): Float = withContext(Dispatchers.IO) {
        runCatching {
            val source = sourceProvider.from(servers.config.first()) ?: return@runCatching 0f
            val progress = source.pullProgress(bookId) ?: return@runCatching 0f
            if (progress.totalPages <= 1) 0f
            else ((progress.page - 1).toFloat() / (progress.totalPages - 1)).coerceIn(0f, 1f)
        }.getOrDefault(0f)
    }
}
