package com.komgareader.app.ui.components

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.komgareader.app.ui.theme.EinkTokens

/** Ein Tab der Bottom-Menubar. */
data class BottomNavItem(
    val icon: ImageVector,
    val label: String,
)

/**
 * Onyx-Look Bottom-Menubar: gleich breite Items, Icon über Label.
 * Aktives Item bekommt einen kurzen schwarzen Akzent-Balken über dem Icon
 * und ein fettes Label. Flach, keine Material-Indikator-Pille, keine Animation.
 */
@Composable
fun EinkBottomBar(
    items: List<BottomNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Kleiner sichtbarer Rand zum Bildschirmrand (nicht bündig), rundum gerundeter
    // schwarzer Rahmen — schwebende Menubar.
    val barShape = RoundedCornerShape(14.dp)
    Box(modifier.fillMaxWidth().padding(start = 5.dp, end = 5.dp, top = 1.dp, bottom = 6.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(barShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(EinkTokens.strongBorder, MaterialTheme.colorScheme.outline, barShape)
                // Padding reduziert, damit die Bar trotz größerer Icons/Labels gleich hoch bleibt.
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            items.forEachIndexed { index, item ->
                NavCell(
                    item = item,
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun NavCell(
    item: BottomNavItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = if (selected) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Akzent-Balken über dem aktiven Item
        Box(
            Modifier
                .padding(bottom = 4.dp)
                .height(3.dp)
                .width(24.dp)
                .then(
                    if (selected) {
                        Modifier.background(
                            MaterialTheme.colorScheme.onSurface,
                            RoundedCornerShape(2.dp),
                        )
                    } else {
                        Modifier
                    },
                ),
        )
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            modifier = Modifier.size(EinkTokens.navIcon),
            tint = tint,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = item.label,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            // Auch ungewählt mind. Medium — auf E-Ink sind dünne Schriften schwer lesbar.
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = tint,
        )
    }
}
