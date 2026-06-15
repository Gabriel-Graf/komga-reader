package com.komgareader.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komgareader.app.data.AppUpdateController
import com.komgareader.app.data.AppUpdateInstaller
import com.komgareader.app.data.AppUpdateState
import com.komgareader.app.data.UpdateInstall
import com.komgareader.data.update.ReleaseInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Thin UI adapter over the process-wide [AppUpdateController] + [AppUpdateInstaller]. Multiple
 * instances (home shell, "About" screen) read the same singleton [state] → consistent badges/banner.
 */
@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    private val controller: AppUpdateController,
    private val installer: AppUpdateInstaller,
) : ViewModel() {
    val state: StateFlow<AppUpdateState> = controller.state
    val currentVersion: String = controller.currentVersion
    /** Release notes for the "what's new" modal after a freshly installed update (null = none). */
    val releaseNotes = controller.releaseNotes

    private val _installing = MutableStateFlow(false)
    val installing: StateFlow<Boolean> = _installing.asStateFlow()

    /** Download progress `0f..1f` while [installing]; null when not downloading. */
    private val _progress = MutableStateFlow<Float?>(null)
    val progress: StateFlow<Float?> = _progress.asStateFlow()

    /** Outcome of the last install attempt (shown as a status line); null until an attempt finishes. */
    private val _result = MutableStateFlow<UpdateInstall?>(null)
    val result: StateFlow<UpdateInstall?> = _result.asStateFlow()

    /** Trigger a check (app start + "Check for updates" button). */
    fun check() = viewModelScope.launch { controller.check() }

    /** On start, check whether an update was just installed → load the release notes if so. */
    fun checkJustUpdated() = viewModelScope.launch { controller.checkJustUpdated() }

    fun dismissReleaseNotes() = controller.dismissReleaseNotes()

    /**
     * Download the APK + start the install (OS dialog). [installing]/[progress] drive the live label;
     * [result] surfaces the outcome so a failure / missing permission is never silent.
     */
    fun install(release: ReleaseInfo) = viewModelScope.launch {
        _installing.value = true
        _result.value = null
        _progress.value = 0f
        try {
            _result.value = installer.downloadAndInstall(release) { _progress.value = it }
        } finally {
            _installing.value = false
            _progress.value = null
        }
    }

    /** Clear the last result line (e.g. when the user retries or leaves). */
    fun clearResult() { _result.value = null }
}
