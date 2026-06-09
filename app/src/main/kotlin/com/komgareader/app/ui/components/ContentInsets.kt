package com.komgareader.app.ui.components

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.dp

/**
 * Unterer Freiraum, den scrollende Inhalte als `contentPadding` reservieren sollen, damit ihre
 * **letzten Items über der schwebenden Bottom-Menubar frei stehen** — während der Inhalt selbst
 * voll bis zur Unterkante reicht und **hinter den transparenten Rändern der Bar durchscheint**.
 *
 * Vom App-Gerüst ([com.komgareader.app.ui.home.HomeScreen]) auf die gemessene Bar-Höhe gesetzt.
 * Ein Scroller liest `LocalContentBottomInset.current` in seinem `contentPadding(bottom = …)`.
 * Default 0 — Screens ohne überlagernde Bar bleiben unberührt.
 */
val LocalContentBottomInset = compositionLocalOf { 0.dp }
