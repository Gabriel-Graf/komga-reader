package com.komgareader.data.plugin.repo

/**
 * Das offizielle Plugin-Repo (Sammel-Repo `Gabriel-Graf/KomgaReaderPlugins`, `repo.json` auf `main`
 * via GitHub-Raw). Bewusst eine Konstante (kein DB-Seed): der URL-Tausch ist ein Ein-Zeilen-Edit,
 * ohne Migrations-/Seed-Falle. Im Browser über das Settings-Flag `official_repo_enabled` abschaltbar.
 */
object PluginRepoDefaults {
    const val OFFICIAL_NAME = "Komga Reader Official"
    const val OFFICIAL_URL = "https://raw.githubusercontent.com/Gabriel-Graf/KomgaReaderPlugins/main/repo.json"
}
