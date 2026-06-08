package com.komgareader.app.ui.components

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.domain.model.DisplayBehavior

/**
 * App-weit bereitgestelltes Geräte-Verhalten (zwei orthogonale Achsen: Bewegung ⟂ Akzentfarbe).
 * Wird in `MainActivity` aus Anzeige-Modus + `EinkController.capabilities` abgeleitet
 * ([com.komgareader.domain.model.displayBehaviorFor]). Default = mono E-Ink (sicherster Fall).
 */
val LocalDisplayBehavior = staticCompositionLocalOf { DisplayBehavior.MONO_EINK }

/**
 * App-weit bereitgestellter Anzeige-Modus: `true` = E-Ink (= keine Bewegung). **Abgeleitete
 * dünne Brücke** über [LocalDisplayBehavior] (`!allowsMotion`), damit bestehende Animations-
 * Gates unverändert weiterlaufen. Neue, farbsensitive Stellen lesen stattdessen
 * [LocalDisplayBehavior]`.allowsAccentColor`. Default `true` (E-Ink), passend zum Gerät.
 */
val LocalEinkMode = staticCompositionLocalOf { true }

/**
 * Ladeindikator, der den E-Ink-Modus respektiert (Single Source of Truth fürs Laden):
 * auf E-Ink ein **statischer „Lädt…"-Text** (kein ghostender Spinner), im Smartphone-
 * Modus die animierte [CircularProgressIndicator]. [onDark] für helle Schrift/Spinner
 * auf dunklem Grund (Reader-Vollbild).
 */
@Composable
fun LoadingIndicator(
    modifier: Modifier = Modifier,
    onDark: Boolean = false,
    strokeWidth: Dp? = null,
) {
    if (LocalEinkMode.current) {
        Text(
            LocalStrings.current.loading,
            color = if (onDark) Color.White else MaterialTheme.colorScheme.onSurface,
        )
        return
    }
    when {
        onDark -> CircularProgressIndicator(modifier = modifier, color = Color.White)
        strokeWidth != null -> CircularProgressIndicator(modifier = modifier, strokeWidth = strokeWidth)
        else -> CircularProgressIndicator(modifier = modifier)
    }
}
