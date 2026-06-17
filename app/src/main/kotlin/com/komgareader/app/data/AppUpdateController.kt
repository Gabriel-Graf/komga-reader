package com.komgareader.app.data

import com.komgareader.app.BuildConfig
import com.komgareader.data.update.AppUpdateDefaults
import com.komgareader.data.update.GithubReleaseClient
import com.komgareader.data.update.ReleaseInfo
import com.komgareader.data.update.combinedReleaseNotes
import com.komgareader.data.update.isNewerVersion
import com.komgareader.data.update.pendingReleases
import com.komgareader.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide app self-update state. ONE singleton observed jointly by the home shell (tab badge +
 * start banner), settings (section badge) and the "About" screen via [state]. Carries no UI — it
 * only compares the installed [currentVersion] against the latest GitHub release. It also shows the
 * release notes once after a freshly installed update ([releaseNotes]).
 */
@Singleton
class AppUpdateController @Inject constructor(
    private val client: GithubReleaseClient,
    private val settings: SettingsRepository,
) {
    val currentVersion: String = BuildConfig.VERSION_NAME

    private val _state = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    val state: StateFlow<AppUpdateState> = _state.asStateFlow()

    /** Release notes shown once as a read-only modal after a version bump; null = none. */
    private val _releaseNotes = MutableStateFlow<ReleaseInfo?>(null)
    val releaseNotes: StateFlow<ReleaseInfo?> = _releaseNotes.asStateFlow()

    /** Checks against the latest release; idempotent (no parallel double run). */
    suspend fun check() {
        if (_state.value == AppUpdateState.Checking) return
        _state.value = AppUpdateState.Checking
        val release = client.fetchLatest(AppUpdateDefaults.REPO_SLUG)
        _state.value = when {
            release == null -> AppUpdateState.Unknown
            isNewerVersion(release.versionName, currentVersion) -> available(release)
            else -> AppUpdateState.UpToDate
        }
    }

    /**
     * Builds the [AppUpdateState.Available] for [latest], enriched with how many versions the user is
     * behind and the combined changelog of all of them (one install still applies them all at once —
     * Android + cumulative Room migrations handle the jump). Falls back to just the latest's notes if
     * the releases list can't be fetched.
     */
    private suspend fun available(latest: ReleaseInfo): AppUpdateState.Available {
        val pending = pendingReleases(client.fetchReleases(), currentVersion)
        return if (pending.isEmpty()) {
            AppUpdateState.Available(latest, pendingCount = 1, combinedNotes = latest.body)
        } else {
            AppUpdateState.Available(latest, pendingCount = pending.size, combinedNotes = combinedReleaseNotes(pending))
        }
    }

    /**
     * Detects a version bump since the last start (= freshly installed update) and fetches the
     * release notes once for the "what's new" display. First run (no stored value) just records the
     * version — no modal. Idempotent: records the version before showing the modal (no re-trigger).
     */
    suspend fun checkJustUpdated() {
        val lastSeen = settings.lastSeenVersion.first()
        if (lastSeen.isBlank()) {
            settings.setLastSeenVersion(currentVersion)
            return
        }
        if (lastSeen == currentVersion || !isNewerVersion(currentVersion, lastSeen)) return
        // Version bump → fetch notes by tag (fallback: latest), record the version (no re-trigger).
        val rel = client.fetchByTag("v$currentVersion") ?: client.fetchLatest()
        settings.setLastSeenVersion(currentVersion)
        if (rel != null && rel.versionName == currentVersion && rel.body.isNotBlank()) {
            _releaseNotes.value = rel
        }
    }

    fun dismissReleaseNotes() {
        _releaseNotes.value = null
    }
}
