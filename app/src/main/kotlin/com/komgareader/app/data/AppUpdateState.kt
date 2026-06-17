package com.komgareader.app.data

import com.komgareader.data.update.ReleaseInfo

/** State of the app self-update check (process-wide, [AppUpdateController]). */
sealed interface AppUpdateState {
    /** Not checked yet. */
    data object Idle : AppUpdateState
    /** Check in progress. */
    data object Checking : AppUpdateState
    /** The installed version is the latest. */
    data object UpToDate : AppUpdateState
    /** Could not check (network / no release). */
    data object Unknown : AppUpdateState
    /**
     * A newer release is available. [release] is the latest (a single install applies it directly —
     * Android handles the version jump, Room migrations run cumulatively, so NO intermediate installs
     * are needed). [pendingCount] = how many versions ahead of the installed one (≥1); [combinedNotes]
     * = the changelog of all those skipped versions, newest first (informational, shown before download).
     */
    data class Available(
        val release: ReleaseInfo,
        val pendingCount: Int = 1,
        val combinedNotes: String = "",
    ) : AppUpdateState
}

/** true if an update is ready — basis for the tab/section badge + banner. */
val AppUpdateState.hasUpdate: Boolean get() = this is AppUpdateState.Available
