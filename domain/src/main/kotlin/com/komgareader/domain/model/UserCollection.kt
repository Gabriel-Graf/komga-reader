package com.komgareader.domain.model

/** Granularität einer Collection. SERIES → Komga /collections, BOOK → Komga /readlists. */
enum class CollectionKind { SERIES, BOOK }

/** Sync-Status einer Collection bezogen auf EINE Quelle. */
enum class SyncStatus { SYNCED, DIRTY, LOCAL_ONLY, UNSUPPORTED, FORBIDDEN }

/** Ein Mitglied: ein Werk (Serie oder Buch) einer bestimmten Quelle. */
data class CollectionMember(
    val sourceId: Long,
    val remoteId: String,
    val title: String,
)

/**
 * Kanonische, quellen-übergreifende Collection. Identität über Quellen hinweg = [name].
 * [members] ist geordnet; die App hält die kanonische Reihenfolge.
 */
data class UserCollection(
    val id: Long,
    val name: String,
    val kind: CollectionKind,
    val members: List<CollectionMember>,
)
