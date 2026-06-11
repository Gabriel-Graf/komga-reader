package com.komgareader.app.data

import com.komgareader.domain.usecase.VanishedCollection
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SyncCoordinatorTest {

    private class Spy {
        var fullSyncCount = 0
        var pullOnlyCount = 0
        var scanLocalCount = 0
        var fetchReposCount = 0
    }

    private fun coordinator(spy: Spy, displayMode: String) = SyncCoordinator(
        fullSync = { spy.fullSyncCount++; emptyList() },
        pullOnlySync = { spy.pullOnlyCount++ },
        scanLocal = { spy.scanLocalCount++ },
        fetchRepos = { spy.fetchReposCount++ },
        displayMode = { displayMode },
    )

    @Test
    fun `onAppStart runs once even if called twice`() = runTest {
        val spy = Spy()
        val c = coordinator(spy, "LCD")
        c.onAppStart()
        c.onAppStart()
        assertEquals(1, spy.fullSyncCount)
        assertEquals(1, spy.scanLocalCount)
        assertEquals(1, spy.fetchReposCount)
    }

    @Test
    fun `onAppStart on EINK still runs full sync once (app-start is allowed)`() = runTest {
        val spy = Spy()
        coordinator(spy, "EINK").onAppStart()
        assertEquals(1, spy.fullSyncCount)
    }

    @Test
    fun `onServerChanged pulls only`() = runTest {
        val spy = Spy()
        coordinator(spy, "EINK").onServerChanged()
        assertEquals(1, spy.pullOnlyCount)
        assertEquals(0, spy.fullSyncCount)
    }

    @Test
    fun `onManualReload fetches repos and rescans local`() = runTest {
        val spy = Spy()
        coordinator(spy, "EINK").onManualReload()
        assertEquals(1, spy.fetchReposCount)
        assertEquals(1, spy.scanLocalCount)
    }

    @Test
    fun `onCollectionsTabEntered full-syncs on LCD`() = runTest {
        val spy = Spy()
        coordinator(spy, "LCD").onCollectionsTabEntered()
        assertEquals(1, spy.fullSyncCount)
    }

    @Test
    fun `onCollectionsTabEntered does not full-sync on EINK`() = runTest {
        val spy = Spy()
        coordinator(spy, "EINK").onCollectionsTabEntered()
        assertEquals(0, spy.fullSyncCount)
    }
}
