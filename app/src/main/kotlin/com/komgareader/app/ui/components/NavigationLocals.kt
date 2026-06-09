package com.komgareader.app.ui.components

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * „Zur Bibliothek"-Aktion: räumt den Navigations-Stack bis zur Graph-Wurzel ab und bringt den
 * Home/Bibliotheks-Screen wieder nach oben — von **jedem** Detail-Screen aus erreichbar, ohne den
 * Callback durch jede Route-Signatur zu fädeln.
 *
 * Vom App-Gerüst ([com.komgareader.app.MainActivity]) um den NavHost bereitgestellt (dieselbe
 * Home-Navigation wie der Reader-`onHome`). Default = No-Op, damit Previews/Tests ohne Navigation
 * komponieren. Consumer lesen `LocalOnHome.current` (z. B. der Home-Button im Serien-Detail-Header).
 */
val LocalOnHome = staticCompositionLocalOf<() -> Unit> { {} }
