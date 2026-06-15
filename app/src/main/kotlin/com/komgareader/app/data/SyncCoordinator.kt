package com.komgareader.app.data

import com.komgareader.domain.repository.SettingsRepository
import com.komgareader.domain.usecase.VanishedCollection
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Die EINE Stelle der Sync-/Discovery-Entscheidungen. Call-Sites melden nur das Ereignis
 * (App-Start, Server geändert, manueller Reload, Sammlungen-Tab betreten); WAS dann synct/entdeckt,
 * entscheidet ausschließlich diese Klasse. Quellen-agnostisch — delegiert an CollectionSyncManager
 * (Sammlungen) und PluginCatalog (Plugins). Werke/Libs laden reaktiv über die bestehenden
 * ActiveSource-Flows (LibraryViewModel); der App-Start-Sync stößt deren Sammlungs-/Plugin-Teil an.
 *
 * App lädt einmalig pro Launch (akkuschonend auf E-Ink): [onAppStart] ist latch-geschützt.
 */
@Singleton
class SyncCoordinator(
    private val fullSync: suspend () -> List<VanishedCollection>,
    private val pullOnlySync: suspend () -> Unit,
    private val scanLocal: suspend () -> Unit,
    private val fetchRepos: suspend () -> Unit,
    private val syncLocalDownloads: suspend () -> Unit,
    private val purgeExternal: suspend () -> Unit,
    private val displayMode: suspend () -> String,
) {
    @Inject constructor(
        sync: CollectionSyncManager,
        catalog: PluginCatalog,
        localDownloads: LocalDownloadSync,
        externalOpener: ExternalBookOpener,
        settings: SettingsRepository,
    ) : this(
        fullSync = { sync.fullSync() },
        pullOnlySync = { sync.pullOnlySync() },
        scanLocal = { catalog.scanLocal() },
        fetchRepos = { catalog.fetchRepos() },
        syncLocalDownloads = { localDownloads.sync() },
        purgeExternal = { externalOpener.purgeTransient() },
        displayMode = { settings.displayMode.first() },
    )

    private val appStartMutex = Mutex()
    private var appStartDone = false

    /** Einmalig pro App-Launch: Sammlungs-Voll-Sync + lokaler Plugin-Scan + 1× Repo-Fetch. Best-effort je Domäne. */
    suspend fun onAppStart() {
        appStartMutex.withLock {
            if (appStartDone) return
            appStartDone = true
        }
        runCatching { fullSync() }
        runCatching { scanLocal() }
        runCatching { fetchRepos() }
        runCatching { syncLocalDownloads() }
        runCatching { purgeExternal() }
    }

    /** Server hinzugefügt/aktualisiert/entfernt (auch lokaler Ordner) → Sammlungen pullen + lokale Downloads spiegeln. */
    suspend fun onServerChanged() {
        runCatching { pullOnlySync() }
        runCatching { syncLocalDownloads() }
    }

    /** Reload-Button im Plugins-Tab: Repo neu holen + lokal neu scannen + lokale Downloads spiegeln. */
    suspend fun onManualReload() {
        runCatching { fetchRepos() }
        runCatching { scanLocal() }
        runCatching { syncLocalDownloads() }
    }

    /**
     * Plugins-Tab wird sichtbar (onResume): nur lokal neu scannen (kein Netz). Fängt Install/
     * Uninstall ab, die über den OS-Dialog passierten — der lokale Scan prunt deinstallierte
     * Quellen/Profile und zieht die Install-States der Entdeckungs-Liste nach. KEIN Repo-Fetch
     * (akkuschonend; der Netz-Index ändert sich beim Uninstall nicht).
     */
    suspend fun onPluginsTabResumed() { runCatching { scanLocal() } }

    /** Sammlungen-Tab betreten: auf Nicht-E-Ink zusätzlich voll synchronisieren (Akku-Gating). */
    suspend fun onCollectionsTabEntered() {
        if (aggressiveSyncAllowed(displayMode())) runCatching { fullSync() }
    }
}
