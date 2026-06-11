package com.komgareader.app.ui.plugins.repo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller

/**
 * Empfängt das PackageInstaller-Session-Resultat. Bei STATUS_PENDING_USER_ACTION muss der vom OS
 * gelieferte Bestätigungs-Intent gestartet werden (zeigt den Installationsdialog). Der Endzustand
 * (installiert/abgebrochen) wird NICHT hier ausgewertet — der Plugin-Tab/Browser scannt beim
 * onResume neu (kein verlässliches Cross-Process-Callback).
 */
class PluginInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
            confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            confirm?.let { context.startActivity(it) }
        }
    }
}
