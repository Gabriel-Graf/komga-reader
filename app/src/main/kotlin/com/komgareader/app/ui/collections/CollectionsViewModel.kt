package com.komgareader.app.ui.collections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komgareader.app.data.ActiveSource
import com.komgareader.app.data.CollectionSyncManager
import com.komgareader.app.data.SyncCoordinator
import com.komgareader.app.ui.common.holdSpinning
import com.komgareader.domain.model.CollectionKind
import com.komgareader.domain.model.CollectionMember
import com.komgareader.domain.model.UserCollection
import com.komgareader.domain.repository.CollectionRepository
import com.komgareader.domain.repository.DownloadRepository
import com.komgareader.domain.repository.SettingsRepository
import com.komgareader.domain.usecase.VanishedCollection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.komgareader.domain.model.SyncStatus
import javax.inject.Inject

@HiltViewModel
class CollectionsViewModel @Inject constructor(
    private val repo: CollectionRepository,
    private val sync: CollectionSyncManager,
    private val settings: SettingsRepository,
    private val coordinator: SyncCoordinator,
    private val active: ActiveSource,
    private val downloads: DownloadRepository,
) : ViewModel() {

    val collections: StateFlow<List<UserCollection>> =
        repo.collections.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Anzeigemodus des Sammlungen-Tabs (Liste/Kachel/große Kachel), persistiert. Default LARGE_TILE. */
    val viewMode: StateFlow<String> =
        settings.collectionsViewMode.stateIn(viewModelScope, SharingStarted.Eagerly, "LARGE_TILE")

    fun setViewMode(mode: String) = viewModelScope.launch { settings.setCollectionsViewMode(mode) }

    private val _vanished = MutableStateFlow<List<VanishedCollection>>(emptyList())
    val vanished: StateFlow<List<VanishedCollection>> = _vanished.asStateFlow()

    /** True while a full sync runs — drives the spinning sync button (list header + detail). */
    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private var autoSyncedOnce = false

    /** Einmaliger Auto-Sync beim ersten Sichtbarwerden (Recompositions lösen keinen Sturm aus). */
    fun syncOnceOnEnter() {
        if (autoSyncedOnce) return
        autoSyncedOnce = true
        runFullSync()
    }

    /** Tab-Öffnen: Gating-Entscheidung liegt jetzt im Koordinator (zentralisiert). */
    fun syncOnTabOpen() = viewModelScope.launch { coordinator.onCollectionsTabEntered() }

    private fun runFullSync() = viewModelScope.launch { runSync() }

    private suspend fun runSync() = _syncing.holdSpinning {
        _vanished.value = sync.fullSync()
    }

    /** „Hier auch löschen": lokal entfernen (Server-Stand bleibt — Sammlung ist dort weg). */
    fun confirmVanishedDelete(ids: List<Long>) = viewModelScope.launch {
        ids.forEach { repo.delete(it) }
        _vanished.value = emptyList()
    }

    /** „Hier behalten": Modal schließen; Sammlung bleibt, nächster Push legt sie am Server neu an. */
    fun dismissVanished() { _vanished.value = emptyList() }

    /**
     * Liefert für jede Collection-ID, ob sie „nur lokal" ist — d. h. mindestens ein Sync-Link
     * hat Status LOCAL_ONLY, UNSUPPORTED oder FORBIDDEN. Wird im Übersichts-Screen als Badge angezeigt.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val localOnly: StateFlow<Map<Long, Boolean>> = repo.collections
        .flatMapLatest { cols ->
            if (cols.isEmpty()) return@flatMapLatest flowOf(emptyMap())
            val linkFlows = cols.map { col ->
                repo.syncLinks(col.id).map { links ->
                    col.id to links.any { it.status in problemStatuses }
                }
            }
            combine(linkFlows) { pairs -> pairs.toMap() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun create(name: String, kind: CollectionKind) = viewModelScope.launch {
        repo.create(name, kind)
    }

    fun rename(id: Long, name: String) = viewModelScope.launch { repo.rename(id, name) }

    fun delete(id: Long, serverToo: Boolean) = viewModelScope.launch {
        if (serverToo) repo.get(id)?.let { sync.deleteEverywhere(it) }
        repo.delete(id)
    }

    fun addMember(id: Long, member: CollectionMember) = viewModelScope.launch {
        repo.addMember(id, member)
        repo.get(id)?.let { sync.push(it) }
    }

    /**
     * Mehrere Werke in **einer** Coroutine sequentiell hinzufügen — `addMember` ist read-modify-write,
     * parallele `launch`-Aufrufe würden sich gegenseitig überschreiben (nur das letzte bliebe). Ein
     * abschließender Push.
     */
    fun addMembers(id: Long, members: List<CollectionMember>) = viewModelScope.launch {
        members.forEach { repo.addMember(id, it) }
        repo.get(id)?.let { sync.push(it) }
    }

    fun removeMember(id: Long, sourceId: Long, remoteId: String) = viewModelScope.launch {
        repo.removeMember(id, sourceId, remoteId)
        repo.get(id)?.let { sync.push(it) }
    }

    // Nutzer-initiiertes „jetzt synchronisieren" = voller bidirektionaler Sync (push + pull)
    // über alle Sammlungen/Quellen. Kein Argument: fullSync deckt ohnehin die ganze Bibliothek ab.
    fun syncNow() = viewModelScope.launch { runSync() }

    /** Alle Sync-Links einer Collection, als Flow — für den Erklär-Dialog. */
    fun syncLinks(collectionId: Long) = repo.syncLinks(collectionId)

    // --- Offline-Verfügbarkeit der Mitglieder (Sammlungs-Detail) ---

    /** Erreichbarkeit je Quelle der gerade geöffneten Sammlung; `null` = noch nicht geprobt. */
    private val _onlineSources = MutableStateFlow<Set<Long>?>(null)

    /**
     * Probt je distinkter Member-Quelle EINMAL die Erreichbarkeit (ein leichter Cover-Abruf, kurzer
     * Timeout) — quellen-agnostisch über [ActiveSource], gilt für Komga/OPDS/Plugin gleich. Das Ergebnis
     * steuert den Offline-Filter in [membersFor]: eine unerreichbare Quelle zeigt nur ihre lokal
     * heruntergeladenen Mitglieder.
     */
    fun probeCollectionSources(collectionId: Long) = viewModelScope.launch {
        val col = repo.get(collectionId) ?: return@launch
        val online = col.members.map { it.sourceId }.distinct().filter { sourceId ->
            val source = active.get(sourceId) ?: return@filter false
            val probe = col.members.first { it.sourceId == sourceId }
            runCatching {
                withTimeoutOrNull(REACH_TIMEOUT_MS) {
                    source.coverBytes(probe.remoteId, isSeriesCover = col.kind == CollectionKind.SERIES)
                } != null
            }.getOrDefault(false)
        }.toSet()
        _onlineSources.value = online
    }

    /**
     * Sichtbare Mitglieder einer Sammlung nach dem Offline-Filter. Bis die Erreichbarkeits-Probe landet
     * (`_onlineSources == null`) ist [CollectionMembersUi.loading] true — der Screen zeigt eine
     * Lade-Anzeige, kein Mitglied erscheint kurz und verschwindet wieder. Danach greift der Filter;
     * [CollectionMembersUi.emptyOffline] wird erst nach der Probe true.
     */
    fun membersFor(collectionId: Long): Flow<CollectionMembersUi> =
        combine(repo.collections, downloads.downloads, _onlineSources) { cols, dls, online ->
            val col = cols.find { it.id == collectionId }
                ?: return@combine CollectionMembersUi(emptyList(), emptyOffline = false)
            if (online == null) return@combine CollectionMembersUi(emptyList(), emptyOffline = false, loading = true)
            val visible = visibleMembers(col.members, downloadedMemberKeys(dls, col.kind), online)
            CollectionMembersUi(
                members = visible,
                emptyOffline = visible.isEmpty() && col.members.isNotEmpty(),
            )
        }

    companion object {
        private val problemStatuses = setOf(SyncStatus.LOCAL_ONLY, SyncStatus.UNSUPPORTED, SyncStatus.FORBIDDEN)

        /** Kurzer Timeout je Quellen-Erreichbarkeits-Probe — offline schnell scheitern, nicht hängen. */
        private const val REACH_TIMEOUT_MS = 4_000L
    }
}
