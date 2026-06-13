package com.komgareader.data.update

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
fun parseLatestRelease(json: String): ReleaseInfo? = runCatching {
    val o = JSONObject(json)
    val tag = o.optString("tag_name").ifBlank { return null }
    val htmlUrl = o.optString("html_url")
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
    ReleaseInfo(
        tag = tag,
        versionName = tag.removePrefix("v").removePrefix("V"),
        htmlUrl = htmlUrl,
        apkUrl = apkUrl,
        body = o.optString("body"),
    )
}.getOrNull()
