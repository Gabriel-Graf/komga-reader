package com.komgareader.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komgareader.app.data.AppUpdateController
import com.komgareader.app.data.AppUpdateInstaller
import com.komgareader.app.data.AppUpdateState
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

    /** Trigger a check (app start + "Check for updates" button). */
    fun check() = viewModelScope.launch { controller.check() }

    /** On start, check whether an update was just installed → load the release notes if so. */
    fun checkJustUpdated() = viewModelScope.launch { controller.checkJustUpdated() }

    fun dismissReleaseNotes() = controller.dismissReleaseNotes()

    /** Download the APK + start the install (OS dialog). [installing] drives the button label meanwhile. */
    fun install(release: ReleaseInfo) = viewModelScope.launch {
        _installing.value = true
        try {
            installer.downloadAndInstall(release)
        } finally {
            _installing.value = false
        }
    }
}
