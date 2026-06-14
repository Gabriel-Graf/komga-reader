package com.komgareader.app.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.komgareader.app.data.ReadingSessionTracker
import com.komgareader.domain.model.ReaderKind
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Bridges the singleton [ReadingSessionTracker] into Composition (mirrors EinkContextHolder). */
@HiltViewModel
class ReadingSessionHolder @Inject constructor(
    val tracker: ReadingSessionTracker,
) : ViewModel()

/**
 * Drives reading-time tracking for the current reader screen. Enter on book load, accumulate a
 * capped delta on every page change, flush on dispose. No ticking timer (E-Ink). A settings
 * detour does not over-count: no page turns happen there, and the one delta on return is
 * cap-bounded by [ReadingSessionTracker].
 */
@Composable
fun ReadingSessionEffect(
    readerKind: ReaderKind,
    bookRemoteId: String,
    sourceId: Long,
    currentPage: Int,
) {
    val holder = hiltViewModel<ReadingSessionHolder>()
    DisposableEffect(readerKind, bookRemoteId, sourceId) {
        holder.tracker.enter(readerKind, bookRemoteId, sourceId)
        onDispose { holder.tracker.leave() }
    }
    LaunchedEffect(currentPage) { holder.tracker.page() }
}
