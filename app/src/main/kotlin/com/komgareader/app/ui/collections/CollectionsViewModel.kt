package com.komgareader.app.ui.collections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komgareader.app.data.CollectionSyncManager
import com.komgareader.domain.model.CollectionKind
import com.komgareader.domain.model.CollectionMember
import com.komgareader.domain.model.UserCollection
import com.komgareader.domain.repository.CollectionRepository
import com.komgareader.domain.repository.SettingsRepository
import com.komgareader.domain.usecase.VanishedCollection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.komgareader.domain.model.SyncStatus
import javax.inject.Inject

@HiltViewModel
class CollectionsViewModel @Inject constructor(
    private val repo: CollectionRepository,
    private val sync: CollectionSyncManager,
    private val settings: SettingsRepository,
) : ViewModel() {

    val collections: StateFlow<List<UserCollection>> =
        repo.collections.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Anzeigemodus des Sammlungen-Tabs (Liste/Kachel/große Kachel), persistiert. Default LARGE_TILE. */
    val viewMode: StateFlow<String> =
        settings.collectionsViewMode.stateIn(viewModelScope, SharingStarted.Eagerly, "LARGE_TILE")

    fun setViewMode(mode: String) = viewModelScope.launch { settings.setCollectionsViewMode(mode) }

    private val _vanished = MutableStateFlow<List<VanishedCollection>>(emptyList())
    val vanished: StateFlow<List<VanishedCollection>> = _vanished.asStateFlow()

    private var autoSyncedOnce = false

    /** Einmaliger Auto-Sync beim ersten Sichtbarwerden (Recompositions lösen keinen Sturm aus). */
    fun syncOnceOnEnter() {
        if (autoSyncedOnce) return
        autoSyncedOnce = true
        runFullSync()
    }

    /** Tab-Öffnen: nur auf Nicht-E-Ink zusätzlich voll synchronisieren (Akku-Schonung auf E-Ink). */
    fun syncOnTabOpen() = viewModelScope.launch {
        if (aggressiveSyncAllowed(settings.displayMode.first())) runFullSync()
    }

    private fun runFullSync() = viewModelScope.launch {
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
    fun syncNow() = viewModelScope.launch {
        _vanished.value = sync.fullSync()
    }

    /** Alle Sync-Links einer Collection, als Flow — für den Erklär-Dialog. */
    fun syncLinks(collectionId: Long) = repo.syncLinks(collectionId)

    companion object {
        private val problemStatuses = setOf(SyncStatus.LOCAL_ONLY, SyncStatus.UNSUPPORTED, SyncStatus.FORBIDDEN)
    }
}
