package com.komgareader.app.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.komgareader.ui.icons.AppIcons

/**
 * The one home for every sync / reload / refresh button — feedback that a sync is in flight, gated by
 * device class (`shared-structure-before-variants`). **LCD:** the icon spins while [syncing] (loops;
 * always ≥1 full turn via [AnimatedAppIcon]). **E-Ink:** the panel can't render smooth motion (a
 * sub-pixel rotation never refreshes), so instead of spinning the glyph swaps discretely to a "busy"
 * hourglass — one state change refreshes reliably — and reverts when done. Same reason the shared
 * `LoadingIndicator` shows static text on E-Ink, not a spinner. Every refresh button uses this instead
 * of a bare `IconButton { Icon(AppIcons.Refresh) }`. The [syncing] flag is held for a minimum span at
 * the source (`holdSpinning`) so even an instant sync is observed and the busy state stays visible.
 */
@Composable
fun SyncIconButton(
    onClick: () -> Unit,
    syncing: Boolean,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    icon: ImageVector = AppIcons.Refresh,
) {
    val eink = LocalEinkMode.current
    IconButton(onClick = onClick, modifier = modifier) {
        if (eink) {
            Icon(
                imageVector = if (syncing) AppIcons.Busy else icon,
                contentDescription = contentDescription,
            )
        } else {
            AnimatedAppIcon(
                imageVector = icon,
                animation = IconAnimation.SpinClockwise,
                running = syncing,
                contentDescription = contentDescription,
            )
        }
    }
}
