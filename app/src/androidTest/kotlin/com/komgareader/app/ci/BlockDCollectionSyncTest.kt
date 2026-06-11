package com.komgareader.app.ci

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.komgareader.domain.model.CollectionKind
import com.komgareader.domain.model.CollectionMember
import com.komgareader.domain.source.CollectionSyncSource
import com.komgareader.domain.source.SourceFilter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec §9 Block D — Multi-Source-Collection-Sync über die CI-Topologie (A+B). Ergänzt den
 * Single-Server-CollectionSyncLiveTest um die Mehrquellen-Dimension. Skill: komga-collection-server-sync.
 */
@RunWith(AndroidJUnit4::class)
class BlockDCollectionSyncTest {

    private lateinit var stack: CiSourceStack

    @Before fun setUp() { stack = CiSourceStack() }
    @After fun tearDown() { stack.close() }

    private suspend fun seriesRemoteId(sourceId: Long, title: String): String {
        val src = stack.activeSource.get(sourceId)!!
        return src.browse(0, SourceFilter()).items.first { it.title == title }.remoteId
    }

    private suspend fun collSource(idx: Int): Pair<Long, CollectionSyncSource> =
        stack.activeSource.allCollectionSources()[idx]

    private suspend fun cleanup(name: String) {
        for ((_, src) in stack.activeSource.allCollectionSources()) {
            src.listCollections(CollectionKind.SERIES).firstOrNull { it.name == name }
                ?.let { runCatching { src.deleteCollection(CollectionKind.SERIES, it.remoteId) } }
        }
    }

    /**
     * D15: Eine App-Sammlung mit Mitgliedern aus ZWEI Servern (A: Manga, B: Webtoon) syncт jedes
     * Subset zu SEINER Quelle — A bekommt die Manga-Sammlung, B die Webtoon-Sammlung (gleicher Name).
     */
    @Test fun d15_cross_source_collection_synct_je_subset_zur_eigenen_quelle() = runTest {
        stack.register(CiKomga.A, CiKomga.B)
        val sources = stack.activeSource.allCollectionSources()
        assertTrue("Zwei collection-fähige Quellen erwartet", sources.size >= 2)
        val aId = sources.first { it.second.name.contains("A") }.first
        val bId = sources.first { it.second.name.contains("B") }.first

        val mangaRid = seriesRemoteId(aId, CiFixtures.MANGA_SERIES)
        val webtoonRid = seriesRemoteId(bId, CiFixtures.WEBTOON_SERIES)
        val name = "CI-Cross-${System.nanoTime()}"

        // Lokale Sammlung mit je einem Mitglied aus A und B.
        val localId = stack.collectionRepo.create(name, CollectionKind.SERIES)
        stack.collectionRepo.setMembers(
            localId,
            listOf(
                CollectionMember(aId, mangaRid, CiFixtures.MANGA_SERIES),
                CollectionMember(bId, webtoonRid, CiFixtures.WEBTOON_SERIES),
            ),
        )

        try {
            stack.collectionSyncManager.fullSync()

            val onA = stack.activeSource.get(aId) as CollectionSyncSource
            val onB = stack.activeSource.get(bId) as CollectionSyncSource
            val aColl = onA.listCollections(CollectionKind.SERIES).firstOrNull { it.name == name }
            val bColl = onB.listCollections(CollectionKind.SERIES).firstOrNull { it.name == name }
            assertTrue("Server A muss die Sammlung tragen", aColl != null)
            assertTrue("Server B muss die Sammlung tragen", bColl != null)
            assertEquals("A-Sammlung enthält genau das Manga-Mitglied", listOf(mangaRid), aColl!!.memberRemoteIds)
            assertEquals("B-Sammlung enthält genau das Webtoon-Mitglied", listOf(webtoonRid), bColl!!.memberRemoteIds)
        } finally {
            cleanup(name)
        }
    }

    /**
     * D16: removeSource(A) auf einer synchronisierten Multi-Source-Sammlung behält die Mitglieder
     * der anderen Quelle (B) lokal — und löscht NICHTS am Server (Invariante #2, Disconnect=lokal).
     */
    @Test fun d16_remove_source_behaelt_andere_quelle_und_loescht_nichts_am_server() = runTest {
        stack.register(CiKomga.A, CiKomga.B)
        val sources = stack.activeSource.allCollectionSources()
        val aId = sources.first { it.second.name.contains("A") }.first
        val bId = sources.first { it.second.name.contains("B") }.first
        val mangaRid = seriesRemoteId(aId, CiFixtures.MANGA_SERIES)
        val webtoonRid = seriesRemoteId(bId, CiFixtures.WEBTOON_SERIES)
        val name = "CI-Remove-${System.nanoTime()}"

        val localId = stack.collectionRepo.create(name, CollectionKind.SERIES)
        stack.collectionRepo.setMembers(
            localId,
            listOf(
                CollectionMember(aId, mangaRid, CiFixtures.MANGA_SERIES),
                CollectionMember(bId, webtoonRid, CiFixtures.WEBTOON_SERIES),
            ),
        )

        try {
            stack.collectionSyncManager.fullSync()

            // DISCONNECT A: nur lokales Cleanup der A-Mitglieder.
            stack.collectionRepo.removeSource(aId)

            val local = stack.collectionRepo.collections.first().firstOrNull { it.name == name }
            assertTrue("Sammlung muss lokal bestehen bleiben (B-Mitglied übrig)", local != null)
            assertTrue(
                "A-Mitglied (Manga) muss lokal weg sein, war: ${local!!.members.map { it.remoteId }}",
                local.members.none { it.remoteId == mangaRid },
            )
            assertTrue(
                "B-Mitglied (Webtoon) muss lokal erhalten bleiben",
                local.members.any { it.remoteId == webtoonRid },
            )

            // Server A darf die Sammlung NICHT verloren haben (Disconnect ist lokal-only).
            val onA = stack.activeSource.get(aId) as CollectionSyncSource
            assertTrue(
                "removeSource darf die Sammlung am Server A NICHT löschen",
                onA.listCollections(CollectionKind.SERIES).any { it.name == name },
            )
        } finally {
            cleanup(name)
        }
    }
}
