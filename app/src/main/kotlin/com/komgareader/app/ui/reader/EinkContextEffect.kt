package com.komgareader.app.ui.reader

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.komgareader.app.data.EinkContextController
import com.komgareader.domain.eink.EinkContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/** Bridges the singleton [EinkContextController] into Composition (mirrors ReaderEinkHolder). */
@HiltViewModel
class EinkContextHolder @Inject constructor(
    val controller: EinkContextController,
) : ViewModel()

/**
 * Declares the E-Ink context of the current screen. Re-applies on every resume so the correct
 * profile is restored when returning from a screen pushed on top (e.g. reader -> home).
 */
@Composable
fun EinkContextEffect(context: EinkContext) {
    val holder = hiltViewModel<EinkContextHolder>()
    LifecycleResumeEffect(context) {
        runBlocking { holder.controller.applyFor(context) }
        onPauseOrDispose { }
    }
}
