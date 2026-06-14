package com.komgareader.app.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komgareader.app.data.EinkContextController
import com.komgareader.app.eink.HardwareButtonBus
import com.komgareader.domain.eink.HardwareButton
import com.komgareader.domain.eink.PressKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Shared hardware long-press shortcut handler for every reader type (paged/webtoon/comic/novel):
 * a long VOLUME_UP goes Home, a long VOLUME_DOWN triggers a manual anti-ghosting full refresh.
 * Lives in one place (not per reader VM) per shared-structure-before-variants.
 */
@HiltViewModel
class ReaderShortcutsViewModel @Inject constructor(
    bus: HardwareButtonBus,
    private val eink: EinkContextController,
) : ViewModel() {
    private val _homeRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val homeRequests: SharedFlow<Unit> = _homeRequests

    init {
        viewModelScope.launch {
            bus.events.collect { event ->
                if (event.press != PressKind.LONG) return@collect
                when (event.button) {
                    HardwareButton.VOLUME_UP -> _homeRequests.tryEmit(Unit)
                    HardwareButton.VOLUME_DOWN -> eink.manualFullRefresh()
                    else -> {}
                }
            }
        }
    }
}
