package com.komgareader.app.ui.components

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.util.Date

/**
 * Ersetzt den (jetzt überflüssigen) TopBar-Titel der Top-Level-Tabs: links die
 * Uhrzeit, daneben der Akkustand mit Prozent. Vorher kam diese Info aus der
 * System-Statusleiste, die im Vollbild-Modus ausgeblendet ist.
 */
@Composable
fun StatusCluster(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var time by remember { mutableStateOf(currentTime(ctx)) }
    var battery by remember { mutableIntStateOf(batteryPercent(ctx)) }

    // Minütlich aktualisieren — auf E-Ink reicht das, kein BroadcastReceiver-Lifecycle nötig.
    LaunchedEffect(Unit) {
        while (true) {
            time = currentTime(ctx)
            battery = batteryPercent(ctx)
            delay(30_000)
        }
    }

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(time, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(12.dp))
        Icon(
            Icons.Outlined.BatteryStd,
            contentDescription = null,
            // BatteryStd ist senkrecht — 90° drehen für waagerechte Akku-Darstellung (Nub rechts).
            modifier = Modifier.size(20.dp).rotate(90f),
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(2.dp))
        Text("$battery %", style = MaterialTheme.typography.titleMedium)
    }
}

private fun currentTime(ctx: Context): String =
    DateFormat.getTimeFormat(ctx).format(Date())

private fun batteryPercent(ctx: Context): Int {
    val intent = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    return if (level >= 0 && scale > 0) level * 100 / scale else 0
}
