package com.komgareader.ui.slots

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable

/**
 * Capability-Surface des Dialogs: ein **benannter Satz** der Dialog-Fähigkeiten, den ein
 * [DialogSlot]-Pack arrangiert. Spiegelt die echten `EinkModal`-Parameter
 * 1:1 (kein Funktionsverlust) — bis auf das reine Layout-Detail `modifier`, das nie eine Aufrufstelle
 * setzt und deshalb dem Default-Renderer gehört, nicht der Surface. Die E-Ink-Invarianten
 * (keine Animation, Akzent/Bewegung über `LocalEinkMode` & Co.) sind **host-erzwungen**, nicht Teil
 * dieser Surface — ein Pack liefert nur Inhalt/Struktur.
 */
data class DialogState(
    val title: String,
    val onDismiss: () -> Unit,
    val confirmLabel: String,
    val onConfirm: () -> Unit,
    val dismissLabel: String,
    val confirmEnabled: Boolean = true,
    val headerAction: (@Composable () -> Unit)? = null,
    val content: @Composable ColumnScope.() -> Unit,
)
