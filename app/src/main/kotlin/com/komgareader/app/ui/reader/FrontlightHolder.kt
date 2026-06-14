package com.komgareader.app.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komgareader.domain.eink.EinkController
import com.komgareader.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Holds frontlight (screen brightness) state for the reader. Reads the persisted level from
 * [SettingsRepository] and forwards changes to [EinkController] + persistence.
 *
 * Scoped to the reader's NavBackStackEntry via `hiltViewModel()` in [DefaultReaderScaffold].
 * On devices without a controllable frontlight ([EinkController.capabilities.brightnessRange] is
 * null) the caller skips the UI entirely — this VM is still created but never mutated.
 */
@HiltViewModel
class FrontlightHolder @Inject constructor(
    private val controller: EinkController,
    private val settings: SettingsRepository,
) : ViewModel() {

    /** Non-null only when the device reports a controllable frontlight brightness range. */
    val brightnessRange: IntRange? = controller.capabilities.brightnessRange

    /** Current persisted brightness level. -1 means "not set yet" (use range start as fallback). */
    val level: StateFlow<Int> = settings.frontlightLevel
        .stateIn(viewModelScope, SharingStarted.Eagerly, -1)

    /** Applies [value] immediately via the controller and persists it. */
    fun setLevel(value: Int) {
        controller.setBrightness(value)
        viewModelScope.launch { settings.setFrontlightLevel(value) }
    }
}
