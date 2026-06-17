package com.komgareader.data.update

/**
 * Pure view of the app's latest GitHub release (no Android, no network). Source of the app
 * self-update check: [tag]/[versionName] for the version comparison, [apkUrl] for the in-app
 * install, [htmlUrl] as a browser fallback.
 */
data class ReleaseInfo(
    /** Raw release tag, e.g. `v0.1.1`. */
    val tag: String,
    /** Tag without leading `v`/`V`, e.g. `0.1.1` — comparable to `BuildConfig.VERSION_NAME`. */
    val versionName: String,
    /** Release page (browser fallback). */
    val htmlUrl: String,
    /** `browser_download_url` of the first `.apk` asset; null = no installable asset. */
    val apkUrl: String?,
    /** Release notes / tag description (GitHub `body`, markdown). Empty = no notes. */
    val body: String = "",
)

/** Default origin of app updates: the official GitHub repo (`owner/repo`). */
object AppUpdateDefaults {
    const val REPO_SLUG = "Gabriel-Graf/komga-reader"
    fun latestReleaseApi(slug: String = REPO_SLUG) = "https://api.github.com/repos/$slug/releases/latest"
    fun releaseByTagApi(tag: String, slug: String = REPO_SLUG) =
        "https://api.github.com/repos/$slug/releases/tags/$tag"
    /** List of recent releases (newest first), for the "you are N versions behind" changelog. */
    fun releasesApi(slug: String = REPO_SLUG) = "https://api.github.com/repos/$slug/releases?per_page=30"
}
