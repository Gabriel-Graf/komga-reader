package com.komgareader.app.ui.reader

import com.komgareader.app.data.ActiveSource
import com.komgareader.data.download.LocalBookBytes
import com.komgareader.domain.repository.DownloadRepository
import com.komgareader.domain.source.SyncingSource
import com.komgareader.domain.usecase.NovelProgressMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Lädt die rohen EPUB-Bytes eines Buchs über **denselben** Mechanismus wie der
 * paginierte Reader: zuerst der lokale Download (offline-first), sonst der Stream
 * von der aktiven Quelle. Zentralisiert das Byte-Holen, damit der Novel-Reader
 * keinen eigenen Abrufpfad erfindet (siehe `source-extensibility`).
 *
 * Quellen-agnostisch über [ActiveSource]: kein Wissen über Komga, funktioniert für
 * jede [com.komgareader.domain.source.BrowsableSource] (OPDS, Plugin, …).
 */
class EpubBytesLoader @Inject constructor(
    private val active: ActiveSource,
    private val downloadRepository: DownloadRepository,
    private val localBookBytes: LocalBookBytes,
    private val novelProgressMapper: NovelProgressMapper,
) {

    /**
     * Id der aktiven Quelle (für den quellen-übergreifenden `novel_progress`-Schlüssel),
     * oder `null` wenn nicht verbunden.
     */
    suspend fun activeSourceId(): Long? = withContext(Dispatchers.IO) {
        active.current()?.id
    }

    /**
     * Pusht den groben Leseanteil [fraction] als Prozent zur aktiven Quelle — über
     * **denselben** `pushProgress`-Pfad wie die anderen Reader (kein paralleler Sync-Weg).
     * Best-effort: scheitert der Push (offline) oder kann die Quelle keinen Fortschritt
     * syncen, bleibt der lokale `novel_progress`-Eintrag `dirty`. Gibt `true` zurück, wenn
     * der Push gelang (Aufrufer darf dann `markSynced`).
     */
    suspend fun pushNovelFraction(bookId: String, fraction: Float): Boolean = withContext(Dispatchers.IO) {
        val source = active.current() as? SyncingSource ?: return@withContext false
        runCatching {
            source.pushProgress(bookId, novelProgressMapper.toReadProgress(fraction))
        }.isSuccess
    }

    /** Roh-Bytes des Buchs [bookId]; lokaler Download bevorzugt, außer [forceStream]. */
    suspend fun load(bookId: String, forceStream: Boolean): ByteArray = withContext(Dispatchers.IO) {
        if (!forceStream) {
            downloadRepository.get(bookId)?.let { local ->
                return@withContext localBookBytes.bytesOf(local)
            }
        }
        active.current()?.downloadFile(bookId)
            ?: error("Kein Server verbunden.")
    }

    /**
     * Server-Lesefortschritt als relative Position (0.0..1.0) — best-effort, offline 0.
     * Im Reflow-Layout hängt die Seitenzahl vom Viewport ab, daher wird der Start
     * über den Fortschrittsanteil statt einen Seitenindex bestimmt.
     */
    suspend fun startProgressFraction(bookId: String): Float = withContext(Dispatchers.IO) {
        runCatching {
            val source = active.current() as? SyncingSource ?: return@runCatching 0f
            val progress = source.pullProgress(bookId) ?: return@runCatching 0f
            if (progress.totalPages <= 1) 0f
            else ((progress.page - 1).toFloat() / (progress.totalPages - 1)).coerceIn(0f, 1f)
        }.getOrDefault(0f)
    }
}
