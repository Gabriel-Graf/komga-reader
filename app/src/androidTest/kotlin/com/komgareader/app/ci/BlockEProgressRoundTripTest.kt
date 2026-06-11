package com.komgareader.app.ci

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.komgareader.domain.model.ReadProgress
import com.komgareader.domain.source.SourceFilter
import com.komgareader.domain.source.SyncingSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec §9 Block E — Server-Round-Trip des Lese-Fortschritts über einen lokalen Wipe / Reconnect.
 * „Gerät 1" liest (push) → Server hält den Fortschritt → eine FRISCHE lokale DB („Gerät 2" /
 * neu verbundener selber Account) pullt ihn zurück. Beweist offline-first + server-seitige Persistenz.
 */
@RunWith(AndroidJUnit4::class)
class BlockEProgressRoundTripTest {

    /** Setzt eine bekannte, vom Default verschiedene Seite — damit „kam wirklich vom Server" eindeutig ist. */
    @Test fun fortschritt_ueberlebt_lokalen_wipe_via_server_pull() = runTest {
        // GERÄT 1: liest ein Manga-Buch bis zu einer mittleren Seite.
        val device1 = CiSourceStack()
        val targetPage: Int
        val bookRemoteId: String
        try {
            device1.register(CiKomga.A)
            val src1 = device1.activeSource.all().first()
            val series = src1.browse(0, SourceFilter()).items.first { it.title == CiFixtures.MANGA_SERIES }
            val book = src1.books(series.remoteId).first()
            bookRemoteId = book.remoteId
            val pages = src1.pages(bookRemoteId)
            targetPage = (pages.size / 2).coerceAtLeast(2)   // mittig, ≠ 1 (Default), ≠ 0
            (src1 as SyncingSource).pushProgress(
                bookRemoteId,
                ReadProgress(bookId = 0L, page = targetPage, totalPages = pages.size, updatedAt = 1_700_000_000_000L),
            )
        } finally {
            device1.close()   // lokaler Stand „weg"
        }

        // GERÄT 2: frische lokale DB, selber Server/User. Ohne je lokal gelesen zu haben, muss der
        // Server den Fortschritt zurückliefern.
        val device2 = CiSourceStack()
        try {
            device2.register(CiKomga.A)
            val src2 = device2.activeSource.all().first() as SyncingSource
            val pulled = src2.pullProgress(bookRemoteId)
            assertNotNull("Server muss den auf Gerät 1 gesetzten Fortschritt liefern", pulled)
            assertEquals("Gerät 2 muss exakt die auf Gerät 1 gelesene Seite vom Server bekommen", targetPage, pulled!!.page)
            assertTrue("Fortschritt darf nicht der Default (Seite 1) sein", pulled.page >= 2)
        } finally {
            device2.close()
        }
    }
}
