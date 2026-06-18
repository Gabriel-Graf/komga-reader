package com.komgareader.app.work

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.komgareader.app.ui.reader.ViewerMode
import com.komgareader.domain.model.BookFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enqueues the [ScreenSaverCoverWorker] when a book opens. Uniquely named with REPLACE so opening a
 * new book supersedes any still-running cover job — only the latest book's cover matters for the
 * standby. Runs entirely off the reader-open path.
 */
@Singleton
class ScreenSaverScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun schedule(sourceId: Long, bookRemoteId: String, viewerMode: ViewerMode, format: BookFormat) {
        val request = OneTimeWorkRequestBuilder<ScreenSaverCoverWorker>()
            .setInputData(
                workDataOf(
                    ScreenSaverCoverWorker.KEY_SOURCE_ID to sourceId,
                    ScreenSaverCoverWorker.KEY_BOOK_ID to bookRemoteId,
                    ScreenSaverCoverWorker.KEY_VIEWER_MODE to viewerMode.name,
                    ScreenSaverCoverWorker.KEY_FORMAT to format.name,
                ),
            )
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    private companion object {
        const val UNIQUE_NAME = "screensaver-cover"
    }
}
