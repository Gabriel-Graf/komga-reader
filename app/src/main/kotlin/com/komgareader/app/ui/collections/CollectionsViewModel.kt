package com.komgareader.app.ui.collections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komgareader.app.data.CollectionSyncManager
import com.komgareader.domain.model.CollectionKind
import com.komgareader.domain.model.CollectionMember
import com.komgareader.domain.model.UserCollection
import com.komgareader.domain.repository.CollectionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CollectionsViewModel @Inject constructor(
    private val repo: CollectionRepository,
    private val sync: CollectionSyncManager,
) : ViewModel() {

    val collections: StateFlow<List<UserCollection>> =
        repo.collections.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    fun syncNow(id: Long) = viewModelScope.launch {
        repo.get(id)?.let { sync.push(it); sync.refresh(it) }
    }
}
