package com.komgareader.data.plugin.repo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Lädt Repo-Indizes + APKs über HTTPS. Reines I/O (kein Parsen, keine Install-Logik) — die
 * Verifikation/Installation macht der PluginInstaller. Alle Calls auf IO.
 */
class PluginRepoClient(private val http: OkHttpClient) {

    /** Lädt den Index-Text; null bei Netz-/HTTP-Fehler (Aufrufer überspringt das Repo). */
    suspend fun fetchIndex(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                resp.body?.string()
            }
        }.getOrNull()
    }

    /** Lädt [url] nach [dest]; true bei Erfolg. Bei Fehler wird [dest] gelöscht. */
    suspend fun download(url: String, dest: File): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) return@use false
                val body = resp.body ?: return@use false
                dest.outputStream().use { out -> body.byteStream().copyTo(out) }
                true
            }
        }.getOrElse { dest.delete(); false }
    }
}
