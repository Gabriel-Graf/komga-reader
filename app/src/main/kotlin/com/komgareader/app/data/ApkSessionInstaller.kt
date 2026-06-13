package com.komgareader.app.data

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import com.komgareader.app.ui.plugins.repo.PluginInstallReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Result of a session commit. */
enum class ApkSessionResult { STARTED, FAILED }

/**
 * The shared PackageInstaller mechanics: writes an APK into a session and commits it, the OS shows
 * the install dialog (via [PluginInstallReceiver]). ONE place for the session lifecycle — shared by
 * plugin install (with a preceding fingerprint check) and app self-update
 * (`shared-structure-before-variants`). Never orphans a session on failure; does NOT delete the APK
 * (file ownership stays with the caller).
 */
@Singleton
class ApkSessionInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun commit(apk: File): ApkSessionResult {
        val pi = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = runCatching { pi.createSession(params) }.getOrElse { return ApkSessionResult.FAILED }
        return runCatching {
            pi.openSession(sessionId).use { session ->
                session.openWrite("package.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                val intent = Intent(context, PluginInstallReceiver::class.java)
                // FLAG_MUTABLE is required: PackageInstaller fills EXTRA_STATUS/EXTRA_INTENT only when
                // sending. FLAG_IMMUTABLE would throw a SecurityException on API 31+.
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
                session.commit(pending.intentSender)
            }
            ApkSessionResult.STARTED
        }.getOrElse {
            // Don't let an open session leak (timeout leak otherwise).
            runCatching { pi.abandonSession(sessionId) }
            ApkSessionResult.FAILED
        }
    }
}
