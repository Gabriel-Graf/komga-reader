package com.komgareader.app.data

import android.content.Context
import com.komgareader.data.update.GithubReleaseClient
import com.komgareader.data.update.ReleaseInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Result of an app update attempt (for the UI). */
enum class UpdateInstall { STARTED, NO_APK, FAILED }

/**
 * Downloads the release APK and commits the install via the shared [ApkSessionInstaller] mechanics
 * (OS dialog). The OS enforces same-signature on update — a mismatching signature fails in the OS
 * dialog (no security gate needed like for third-party plugins: it's the same app).
 */
@Singleton
class AppUpdateInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: GithubReleaseClient,
    private val apkSession: ApkSessionInstaller,
) {
    suspend fun downloadAndInstall(release: ReleaseInfo): UpdateInstall = withContext(Dispatchers.IO) {
        val url = release.apkUrl ?: return@withContext UpdateInstall.NO_APK
        val dest = File(context.cacheDir, "update-${release.versionName}.apk")
        if (!client.download(url, dest)) return@withContext UpdateInstall.FAILED
        when (apkSession.commit(dest)) {
            ApkSessionResult.STARTED -> UpdateInstall.STARTED
            ApkSessionResult.FAILED -> { dest.delete(); UpdateInstall.FAILED }
        }
    }
}
