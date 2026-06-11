package com.komgareader.data.plugin.repo

import com.komgareader.data.db.PluginRepoDao
import com.komgareader.data.db.PluginRepoEntity
import com.komgareader.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Eine konfigurierte Repo-Quelle (offiziell oder vom Nutzer). [id] = null beim offiziellen Repo. */
data class RepoSource(val id: Long?, val url: String, val name: String, val official: Boolean)

/**
 * Verwaltet die aktiven Repo-Quellen: das offizielle Repo (Konstante + Toggle) plus die vom Nutzer
 * hinzugefügten ([PluginRepoDao]). Reine Verwaltung — kein Netz.
 */
class RepoStore(
    private val dao: PluginRepoDao,
    private val settings: SettingsRepository,
) {
    /** Alle aktiven Repos (offiziell zuerst, falls aktiviert), reaktiv. */
    fun observeActive(): Flow<List<RepoSource>> =
        combine(dao.observeAll(), settings.officialRepoEnabled) { userRepos, officialOn ->
            buildList {
                if (officialOn) add(RepoSource(null, PluginRepoDefaults.OFFICIAL_URL, PluginRepoDefaults.OFFICIAL_NAME, official = true))
                userRepos.forEach { add(RepoSource(it.id, it.url, it.name ?: it.url, official = false)) }
            }
        }

    suspend fun addUserRepo(url: String) {
        val trimmed = url.trim()
        if (trimmed.isNotBlank()) dao.insert(PluginRepoEntity(url = trimmed))
    }

    suspend fun removeUserRepo(id: Long) = dao.delete(id)

    suspend fun setOfficialEnabled(enabled: Boolean) = settings.setOfficialRepoEnabled(enabled)

    /** Nach erfolgreichem Index-Laden den lesbaren Repo-Namen für eine User-Repo-URL nachtragen. */
    suspend fun rememberName(url: String, name: String) = dao.setName(url, name.ifBlank { null })
}
