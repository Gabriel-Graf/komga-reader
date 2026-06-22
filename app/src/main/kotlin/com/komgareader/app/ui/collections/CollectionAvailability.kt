package com.komgareader.app.ui.collections

import com.komgareader.domain.model.CollectionKind
import com.komgareader.domain.model.CollectionMember
import com.komgareader.domain.repository.DownloadedBook

/**
 * State of a collection's member grid after the offline-availability filter. [loading] is true until
 * the source-reachability probe has resolved (screen shows a loading indicator, no member ever flashes
 * in then vanishes). [emptyOffline] is true once the probe has run and nothing is left to show (offline,
 * no local works) — the screen then shows the "no local works" message instead of an empty grid.
 */
data class CollectionMembersUi(
    val members: List<CollectionMember>,
    val emptyOffline: Boolean,
    val loading: Boolean = false,
)

/**
 * Keys (sourceId, remoteId) of members that are locally downloaded: a SERIES collection member matches
 * a download by its series id, a BOOK member by its book id. Source-keyed so a download from one server
 * never marks a same-id member of another server as available.
 */
fun downloadedMemberKeys(downloads: List<DownloadedBook>, kind: CollectionKind): Set<Pair<Long, String>> =
    downloads.mapTo(mutableSetOf()) { dl ->
        dl.sourceId to if (kind == CollectionKind.SERIES) dl.seriesRemoteId else dl.bookRemoteId
    }

/**
 * Members to show: those whose source is reachable ([onlineSources]) plus those available offline
 * ([downloadedKeys]). A member of an unreachable source that is not downloaded is hidden — offline it
 * can neither show a cover nor be opened.
 */
fun visibleMembers(
    members: List<CollectionMember>,
    downloadedKeys: Set<Pair<Long, String>>,
    onlineSources: Set<Long>,
): List<CollectionMember> =
    members.filter { it.sourceId in onlineSources || (it.sourceId to it.remoteId) in downloadedKeys }
