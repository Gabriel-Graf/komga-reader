package com.komgareader.data.plugin.repo

import kotlinx.serialization.Serializable

/** Plugin-Typ im Repo-Index (nur für Label/Filter — Install ist für beide gleich). */
enum class PluginKind { SOURCE, PRESET, LANGUAGE, READER_PRESET }

/** Installations-Zustand eines Index-Eintrags relativ zum installierten Paket. */
enum class InstallState { NOT_INSTALLED, INSTALLED, UPDATE_AVAILABLE }

/** Ein `repo.json`-Index. Unbekannte Felder werden beim Parsen ignoriert (vorwärtskompatibel). */
@Serializable
data class RepoIndex(
    val name: String = "",
    val plugins: List<RepoPluginEntry> = emptyList(),
)

/** Ein einzelner Plugin-Eintrag im Index. Defaults erlauben tolerantes Parsen; Pflichtfelder
 *  werden in [parseRepoIndex] geprüft (leere → Eintrag verworfen). */
@Serializable
data class RepoPluginEntry(
    val packageName: String = "",
    val name: String = "",
    val description: String = "",
    val type: String = "source",
    val abiVersion: Int = 0,
    val versionCode: Long = 0,
    val versionName: String = "",
    val apkUrl: String = "",
    val fingerprint: String = "",
)

/** Ein gemergter, anzeigefertiger Eintrag mit Repo-Herkunft. */
data class BrowsableEntry(
    val entry: RepoPluginEntry,
    val repoName: String,
    val repoUrl: String,
    val kind: PluginKind,
)
