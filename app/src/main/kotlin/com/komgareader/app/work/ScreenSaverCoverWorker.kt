package com.komgareader.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.komgareader.app.data.ScreenSaverCoverResolver
import com.komgareader.app.ui.reader.ViewerMode
import com.komgareader.domain.model.BookFormat
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Generates the device standby cover off the reader-open path. Enqueued when a book opens (see
 * [ScreenSaverScheduler]); delegates to [ScreenSaverCoverResolver], which sets a baseline server
 * cover first (the crash fallback) and then upgrades to a high-res cover when warranted. Always
 * returns success — the screensaver is best-effort and must never surface a failure or block reading.
 */
@HiltWorker
class ScreenSaverCoverWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val resolver: ScreenSaverCoverResolver,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val sourceId = inputData.getLong(KEY_SOURCE_ID, -1L)
        val bookId = inputData.getString(KEY_BOOK_ID) ?: return Result.success()
        if (sourceId < 0L) return Result.success()
        val viewerMode = runCatching { ViewerMode.valueOf(inputData.getString(KEY_VIEWER_MODE) ?: "PAGED") }
            .getOrDefault(ViewerMode.PAGED)
        val format = runCatching { BookFormat.valueOf(inputData.getString(KEY_FORMAT) ?: "CBZ") }
            .getOrDefault(BookFormat.CBZ)
        runCatching { resolver.refresh(sourceId, bookId, viewerMode, format) }
        return Result.success()
    }

    companion object {
        const val KEY_SOURCE_ID = "sourceId"
        const val KEY_BOOK_ID = "bookId"
        const val KEY_VIEWER_MODE = "viewerMode"
        const val KEY_FORMAT = "format"
    }
}
