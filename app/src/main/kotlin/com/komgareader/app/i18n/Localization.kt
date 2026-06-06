package com.komgareader.app.i18n

import androidx.compose.runtime.staticCompositionLocalOf

/** Liefert die aktuell aktiven [Strings] an jede Composable. Default Deutsch. */
val LocalStrings = staticCompositionLocalOf<Strings> { StringsDe }
