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
    /** A newer release is available. */
    data class Available(val release: ReleaseInfo) : AppUpdateState
}

/** true if an update is ready — basis for the tab/section badge + banner. */
val AppUpdateState.hasUpdate: Boolean get() = this is AppUpdateState.Available
