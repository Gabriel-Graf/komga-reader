package com.komgareader.data.plugin.repo

/**
 * Das offizielle Plugin-Repo. **Platzhalter** — wird vor dem echten Release auf die offizielle
 * Komga-Reader-Repo-URL gesetzt. Bewusst eine Konstante (kein DB-Seed): der spätere URL-Tausch
 * ist ein Ein-Zeilen-Edit, ohne Migrations-/Seed-Falle. Im Browser über das Settings-Flag
 * `official_repo_enabled` abschaltbar.
 */
object PluginRepoDefaults {
    const val OFFICIAL_NAME = "Komga Reader Official"
    const val OFFICIAL_URL = "https://example.invalid/komga-reader-plugins/repo.json"
}
