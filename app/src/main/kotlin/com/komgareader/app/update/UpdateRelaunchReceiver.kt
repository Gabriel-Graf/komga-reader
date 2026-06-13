package com.komgareader.app.update

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.komgareader.app.i18n.Language
import com.komgareader.app.i18n.Strings
import com.komgareader.app.i18n.stringsFor
import com.komgareader.domain.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * Brings the user back into the app right after a self-update. The system delivers
 * ACTION_MY_PACKAGE_REPLACED to the freshly installed package. A direct activity start is best-effort
 * only — Android's background-activity-launch restrictions block it on stock 12+ — so the reliable
 * path is a high-importance notification with a full-screen intent: it relaunches the app directly
 * where the platform allows (like an alarm), otherwise one tap reopens it onto the "what's new" modal.
 */
@AndroidEntryPoint
class UpdateRelaunchReceiver : BroadcastReceiver() {

    @Inject lateinit var settings: SettingsRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            ?: return
        // Best effort: bring the app straight back (works on lenient OEMs / older Android).
        runCatching { context.startActivity(launch) }
        notifyReopen(context, launch)
    }

    private fun notifyReopen(context: Context, launch: Intent) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "App update", NotificationManager.IMPORTANCE_HIGH),
            )
        }
        val s = localizedStrings()
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pi = PendingIntent.getActivity(context, 0, launch, flags)
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(s?.appName ?: "Komga Reader")
            .setContentText(s?.updateInstalledNotice ?: "Update installed — tap to open")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setFullScreenIntent(pi, true)
            .build()
        runCatching { nm.notify(NOTIF_ID, notification) }
    }

    /** Resolves the user's chosen language for the notification text; null falls back to English. */
    private fun localizedStrings(): Strings? = runCatching {
        val code = runBlocking { settings.language.first() }
        stringsFor(Language.entries.firstOrNull { it.code == code } ?: Language.EN)
    }.getOrNull()

    private companion object {
        const val CHANNEL = "app_update"
        const val NOTIF_ID = 4711
    }
}
