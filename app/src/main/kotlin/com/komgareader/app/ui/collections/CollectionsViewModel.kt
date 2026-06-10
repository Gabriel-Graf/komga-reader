package com.komgareader.app.ui.collections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komgareader.app.data.CollectionSyncManager
import com.komgareader.domain.model.CollectionKind
import com.komgareader.domain.model.CollectionMember
import com.komgareader.domain.model.UserCollection
import com.komgareader.domain.repository.CollectionRepository
import com.komgareader.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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

    fun removeMember(id: Long, sourceId: Long, remoteId: String) = viewModelScope.launch {
        repo.removeMember(id, sourceId, remoteId)
        repo.get(id)?.let { sync.push(it) }
    }

    // Nutzer-initiiertes „jetzt synchronisieren" = lokalen Stand hochschieben (Status → SYNCED).
    // Kein anschließendes refresh — dessen setMembers würde die Links sofort wieder DIRTY markieren
    // und das Badge fälschlich auf „nur lokal" zurücksetzen. (Server-Pull ist eine eigene Aktion.)
    fun syncNow(id: Long) = viewModelScope.launch {
        repo.get(id)?.let { sync.push(it) }
    }

    /** Alle Sync-Links einer Collection, als Flow — für den Erklär-Dialog. */
    fun syncLinks(collectionId: Long) = repo.syncLinks(collectionId)

    companion object {
        private val problemStatuses = setOf(SyncStatus.LOCAL_ONLY, SyncStatus.UNSUPPORTED, SyncStatus.FORBIDDEN)
    }
}
