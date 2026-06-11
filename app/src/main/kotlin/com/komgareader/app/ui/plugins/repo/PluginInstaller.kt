package com.komgareader.app.ui.plugins.repo

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import com.komgareader.data.plugin.repo.fingerprintMatches
import com.komgareader.plugin.host.PluginSignature
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** Ergebnis eines Install-Versuchs (für die UI). */
sealed interface InstallResult {
    data object SessionStarted : InstallResult       // OS-Dialog läuft; Endzustand via Tab-Re-Scan
    data object FingerprintMismatch : InstallResult
    data object Failed : InstallResult
}

/**
 * Verifiziert die Signatur eines heruntergeladenen APK gegen den erwarteten Fingerprint und startet
 * dann eine [PackageInstaller]-Session (OS zeigt den Installationsdialog). Liest NIE Plugin-Code.
 * Sicherheits-Gate: ohne Fingerprint-Match wird nichts installiert und die Datei gelöscht.
 */
class PluginInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** SHA-256 des Signaturzertifikats einer noch-nicht-installierten APK-Datei (API 28+). */
    private fun apkCertSha256(apk: File): String? = runCatching {
        val info = context.packageManager.getPackageArchiveInfo(
            apk.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES,
        ) ?: return null
        val cert = info.signingInfo?.apkContentsSigners?.firstOrNull() ?: return null
        PluginSignature.sha256(cert.toByteArray())
    }.getOrNull()

    /**
     * Verifiziert [apk] gegen [expectedFingerprint]; bei Match → PackageInstaller-Session committen
     * (OS-Dialog). Bei Mismatch/Fehler wird [apk] gelöscht.
     */
    suspend fun verifyAndInstall(apk: File, expectedFingerprint: String): InstallResult =
        withContext(Dispatchers.IO) {
            val actual = apkCertSha256(apk)
            if (actual == null || !fingerprintMatches(expectedFingerprint, actual)) {
                apk.delete()
                return@withContext if (actual == null) InstallResult.Failed else InstallResult.FingerprintMismatch
            }
            runCatching {
                val pi = context.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                val sessionId = pi.createSession(params)
                pi.openSession(sessionId).use { session ->
                    session.openWrite("plugin.apk", 0, apk.length()).use { out ->
                        apk.inputStream().use { it.copyTo(out) }
                        session.fsync(out)
                    }
                    val intent = Intent(context, PluginInstallReceiver::class.java)
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                    val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
                    session.commit(pending.intentSender)
                }
                InstallResult.SessionStarted
            }.getOrElse { apk.delete(); InstallResult.Failed }
        }
}
