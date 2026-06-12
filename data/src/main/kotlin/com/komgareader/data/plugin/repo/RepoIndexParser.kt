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

/** Löst [apkUrl] auf: absolute http(s)-URL unverändert; sonst relativ gegen die Basis der [repoUrl]. */
fun resolveApkUrl(repoUrl: String, apkUrl: String): String {
    if (apkUrl.startsWith("http://") || apkUrl.startsWith("https://")) return apkUrl
    return URI(repoUrl).resolve(apkUrl).toString()
}

/** Mappt den Index-`type` auf [PluginKind]; Unbekanntes → SOURCE (konservativ). */
fun pluginKindOf(type: String): PluginKind = when {
    type.equals("preset", ignoreCase = true) -> PluginKind.PRESET
    type.equals("language", ignoreCase = true) -> PluginKind.LANGUAGE
    type.equals("reader_preset", ignoreCase = true) -> PluginKind.READER_PRESET
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
