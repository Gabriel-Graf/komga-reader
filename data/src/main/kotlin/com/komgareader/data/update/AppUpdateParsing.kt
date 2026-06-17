package com.komgareader.data.update

import org.json.JSONArray
import org.json.JSONObject

/**
 * true if [latest] is a higher version than [current]. Semver element-wise (1.2.10 > 1.2.9); a
 * leading `v`/`V` and pre-release suffixes (`-rc1`) are ignored, missing parts count as 0
 * (`0.1` == `0.1.0`). Unparseable versions count as `0.0.0` → never "newer".
 */
fun isNewerVersion(latest: String, current: String): Boolean {
    val l = parseVersion(latest)
    val c = parseVersion(current)
    val n = maxOf(l.size, c.size)
    for (i in 0 until n) {
        val a = l.getOrElse(i) { 0 }
        val b = c.getOrElse(i) { 0 }
        if (a != b) return a > b
    }
    return false
}

private fun parseVersion(v: String): List<Int> =
    v.trim().removePrefix("v").removePrefix("V")
        .takeWhile { it.isDigit() || it == '.' }   // cut off pre-release suffixes: "0.1.1-rc1" → "0.1.1"
        .split('.')
        .mapNotNull { it.toIntOrNull() }

/**
 * Parses the GitHub `/releases/latest` response into a [ReleaseInfo]; null on a missing tag or
 * invalid JSON (the caller treats that as "no update info"). Picks the first `.apk` asset.
 */
fun parseLatestRelease(json: String): ReleaseInfo? =
    runCatching { parseRelease(JSONObject(json)) }.getOrNull()

/** Parses a GitHub `/releases` array (newest first) into [ReleaseInfo]s; empty on invalid JSON. */
fun parseReleaseList(json: String): List<ReleaseInfo> = runCatching {
    val arr = JSONArray(json)
    (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(::parseRelease) }
}.getOrDefault(emptyList())

private fun parseRelease(o: JSONObject): ReleaseInfo? {
    val tag = o.optString("tag_name").ifBlank { return null }
    val assets = o.optJSONArray("assets")
    var apkUrl: String? = null
    if (assets != null) {
        for (i in 0 until assets.length()) {
            val a = assets.optJSONObject(i) ?: continue
            if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                apkUrl = a.optString("browser_download_url").ifBlank { null }
                break
            }
        }
    }
    return ReleaseInfo(
        tag = tag,
        versionName = tag.removePrefix("v").removePrefix("V"),
        htmlUrl = o.optString("html_url"),
        apkUrl = apkUrl,
        body = o.optString("body"),
    )
}

/**
 * The releases newer than [currentVersion], newest first — the versions a single install will apply
 * at once. Pure (sortable view over a fetched list); used for the "N versions behind" changelog.
 */
fun pendingReleases(all: List<ReleaseInfo>, currentVersion: String): List<ReleaseInfo> =
    all.filter { isNewerVersion(it.versionName, currentVersion) }
        .sortedWith { a, b -> if (isNewerVersion(a.versionName, b.versionName)) -1 else 1 }

/**
 * Combined release notes for [pending] (already newest-first): each version's tag as a header
 * followed by its notes, blocks separated by a blank line. Versions without notes are skipped.
 */
fun combinedReleaseNotes(pending: List<ReleaseInfo>): String =
    pending.filter { it.body.isNotBlank() }
        .joinToString("\n\n") { "${it.tag}\n${it.body.trim()}" }
