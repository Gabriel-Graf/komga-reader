package com.komgareader.data.plugin.repo

import kotlinx.serialization.json.Json
import java.net.URI

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

/** Parst einen `repo.json`-Index. Gibt null zurück, wenn das JSON kein Index-Objekt ist/kaputt ist.
 *  Einzelne Einträge ohne Pflichtfeld (packageName/apkUrl/fingerprint/versionCode>0) werden verworfen. */
fun parseRepoIndex(text: String): RepoIndex? {
    val idx = runCatching { json.decodeFromString(RepoIndex.serializer(), text) }.getOrNull() ?: return null
    val valid = idx.plugins.filter {
        it.packageName.isNotBlank() && it.apkUrl.isNotBlank() && it.fingerprint.isNotBlank() && it.versionCode > 0
    }
    return idx.copy(plugins = valid)
}

/** Resolves a repo-relative URL against the base of [repoUrl]; an absolute http(s) URL is returned unchanged.
 *  Generic for apkUrl/previewUrl/readmeUrl. */
fun resolveRepoUrl(repoUrl: String, urlOrPath: String): String {
    if (urlOrPath.startsWith("http://") || urlOrPath.startsWith("https://")) return urlOrPath
    return URI(repoUrl).resolve(urlOrPath).toString()
}

/** Mappt den Index-`type` auf [PluginKind]; Unbekanntes → SOURCE (konservativ). */
fun pluginKindOf(type: String): PluginKind = when {
    type.equals("preset", ignoreCase = true) -> PluginKind.PRESET
    type.equals("language", ignoreCase = true) -> PluginKind.LANGUAGE
    type.equals("reader_preset", ignoreCase = true) -> PluginKind.READER_PRESET
    type.equals("ui_pack", ignoreCase = true) -> PluginKind.UI_PACK
    type.equals("font", ignoreCase = true) -> PluginKind.FONT
    else -> PluginKind.SOURCE
}

/** Dedupt nach packageName, behält den Eintrag mit der höchsten versionCode (inkl. Repo-Herkunft). */
fun mergeRepoEntries(all: List<BrowsableEntry>): List<BrowsableEntry> =
    all.groupBy { it.entry.packageName }
        .map { (_, group) -> group.maxBy { it.entry.versionCode } }

/** Zustand eines Eintrags relativ zum installierten Paket (null = nicht installiert). */
fun installState(entryVersionCode: Long, installedVersionCode: Long?): InstallState = when {
    installedVersionCode == null -> InstallState.NOT_INSTALLED
    installedVersionCode >= entryVersionCode -> InstallState.INSTALLED
    else -> InstallState.UPDATE_AVAILABLE
}

/** SHA-256-Hex ohne Doppelpunkte/Whitespace, lowercase. */
fun normalizeFingerprint(s: String): String =
    s.filterNot { it == ':' || it.isWhitespace() }.lowercase()

/** True, wenn Index-Fingerprint und APK-Cert-SHA nach Normalisierung gleich sind. */
fun fingerprintMatches(indexFingerprint: String, apkCertSha256: String): Boolean =
    normalizeFingerprint(indexFingerprint) == normalizeFingerprint(apkCertSha256)
