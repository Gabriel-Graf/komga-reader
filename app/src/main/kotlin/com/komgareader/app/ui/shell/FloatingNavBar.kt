package com.komgareader.app.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.komgareader.app.ui.components.BottomNavItem
import com.komgareader.ui.theme.EinkTokens
import com.komgareader.ui.theme.LocalDesignTokens

/**
 * Schwebende Pill-Bottom-Nav für den Aurora-/Mobile-Look: rundum 24dp-Pille, Tiefe über Schatten
 * (LCD: `tokens.usesShadows`) statt Border, aktives Item als gefüllte Akzent-Pille hinter dem Icon.
 * Token-getrieben (Akzent/Elevation) — derselbe `BottomNavItem`-Vertrag wie EinkBottomBar.
 */
@Composable
fun FloatingNavBar(
    items: List<BottomNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalDesignTokens.current
    val shape = RoundedCornerShape(24.dp)
    val dock = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
        val base = Modifier.fillMaxWidth()
        // Extra-Lift über der Karten-Elevation, damit die schwebende Pille klar über dem Inhalt liegt.
        val navElevation = tokens.cardElevation + 5.dp
        val withDepth = if (tokens.usesShadows) {
            base.shadow(navElevation, shape, clip = false).clip(shape).background(dock)
        } else {
            base.clip(shape).background(dock).border(EinkTokens.hairline, MaterialTheme.colorScheme.outline, shape)
        }
        Row(
            modifier = withDepth.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            items.forEachIndexed { index, item ->
                FloatingNavCell(item, index == selectedIndex, { onSelect(index) }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun FloatingNavCell(item: BottomNavItem, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val accent = LocalDesignTokens.current.accent
    val onAccent = LocalDesignTokens.current.onAccent
    val inactive = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier.clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(width = 40.dp, height = 28.dp)
                .clip(RoundedCornerShape(99.dp))
                .then(if (selected) Modifier.background(accent) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                modifier = Modifier.size(20.dp),
                tint = if (selected) onAccent else inactive,
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = item.label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) accent else inactive,
        )
    }
}
