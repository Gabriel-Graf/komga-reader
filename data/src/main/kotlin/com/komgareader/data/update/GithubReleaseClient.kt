package com.komgareader.data.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Fetches the latest GitHub release + its APK over HTTPS. Pure I/O (parsing is done by
 * [parseLatestRelease]). All calls on IO; failures → null/false (the caller handles that
 * tolerantly). GitHub requires a `User-Agent` — without it the API answers with 403.
 */
class GithubReleaseClient(private val http: OkHttpClient) {

    /** Fetches + parses the latest release; null on network/HTTP error or missing release. */
    suspend fun fetchLatest(slug: String = AppUpdateDefaults.REPO_SLUG): ReleaseInfo? =
        fetch(AppUpdateDefaults.latestReleaseApi(slug))

    /** Fetches + parses the release for a specific tag (e.g. "v0.1.1"); null on error/missing. */
    suspend fun fetchByTag(tag: String, slug: String = AppUpdateDefaults.REPO_SLUG): ReleaseInfo? =
        fetch(AppUpdateDefaults.releaseByTagApi(tag, slug))

    private suspend fun fetch(url: String): ReleaseInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "komga-reader-app")
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                resp.body?.string()?.let { parseLatestRelease(it) }
            }
        }.getOrNull()
    }

    /** Downloads [url] to [dest]; true on success. On failure [dest] is deleted. */
    suspend fun download(url: String, dest: File): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url(url).header("User-Agent", "komga-reader-app").build())
                .execute().use { resp ->
                    if (!resp.isSuccessful) return@use false
                    val body = resp.body ?: return@use false
                    dest.outputStream().use { out -> body.byteStream().copyTo(out) }
                    true
                }
        }.getOrElse { dest.delete(); false }
    }
}
