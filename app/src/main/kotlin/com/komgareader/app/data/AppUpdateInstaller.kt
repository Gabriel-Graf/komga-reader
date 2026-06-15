package com.komgareader.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.komgareader.data.update.GithubReleaseClient
import com.komgareader.data.update.ReleaseInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Result of an app update attempt (for the UI). */
enum class UpdateInstall { STARTED, NO_APK, FAILED, NEEDS_PERMISSION }

/**
 * Downloads the release APK and commits the install via the shared [ApkSessionInstaller] mechanics
 * (OS dialog). The OS enforces same-signature on update — a mismatching signature fails in the OS
 * dialog (no security gate needed like for third-party plugins: it's the same app).
 *
 * Before downloading we check [android.content.pm.PackageManager.canRequestPackageInstalls]: without
 * the user-granted "install unknown apps" right the session commit would fail **silently**, which is
 * exactly the "tapped, nothing happened" symptom. If it is missing we open the OS settings page for
 * this app and return [UpdateInstall.NEEDS_PERMISSION] so the UI can tell the user what to do.
 */
@Singleton
class AppUpdateInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: GithubReleaseClient,
    private val apkSession: ApkSessionInstaller,
) {
    suspend fun downloadAndInstall(
        release: ReleaseInfo,
        onProgress: (Float) -> Unit = {},
    ): UpdateInstall = withContext(Dispatchers.IO) {
        val url = release.apkUrl ?: return@withContext UpdateInstall.NO_APK
        if (!context.packageManager.canRequestPackageInstalls()) {
            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}"),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            return@withContext UpdateInstall.NEEDS_PERMISSION
        }
        val dest = File(context.cacheDir, "update-${release.versionName}.apk")
        if (!client.download(url, dest, onProgress)) return@withContext UpdateInstall.FAILED
        when (apkSession.commit(dest)) {
            ApkSessionResult.STARTED -> UpdateInstall.STARTED
            ApkSessionResult.FAILED -> { dest.delete(); UpdateInstall.FAILED }
        }
    }
}
