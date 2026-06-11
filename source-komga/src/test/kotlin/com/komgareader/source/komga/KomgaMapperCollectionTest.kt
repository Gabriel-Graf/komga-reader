package com.komgareader.source.komga

import com.komgareader.source.komga.dto.CollectionDto
import com.komgareader.source.komga.dto.ReadListDto
import kotlin.test.Test
import kotlin.test.assertEquals

class KomgaMapperCollectionTest {
    private val mapper = KomgaMapper(sourceId = 0L, baseUrl = "")

    @Test fun `parseIsoUtcMillis liest Zulu-Zeit als UTC`() {
        // 2024-01-15T10:30:00Z = 1705314600000
        assertEquals(1705314600000L, mapper.parseIsoUtcMillis("2024-01-15T10:30:00Z"))
    }

    @Test fun `parseIsoUtcMillis ohne Offset wird als UTC interpretiert`() {
        // Komga liefert oft ohne Zone — muss GMT/UTC sein, nicht lokale Zone.
        assertEquals(1705314600000L, mapper.parseIsoUtcMillis("2024-01-15T10:30:00"))
    }

    @Test fun `parseIsoUtcMillis mit Offset rechnet auf UTC zurück`() {
        // 12:30 +02:00 == 10:30 UTC == 1705314600000
        assertEquals(1705314600000L, mapper.parseIsoUtcMillis("2024-01-15T12:30:00+02:00"))
    }

    @Test fun `parseIsoUtcMillis bei leer gibt 0`() {
        assertEquals(0L, mapper.parseIsoUtcMillis(""))
    }

    @Test fun `toRemoteCollection mappt lastModifiedDate als updatedAt`() {
        val dto = CollectionDto(id = "c1", name = "X", seriesIds = listOf("s1"), lastModifiedDate = "2024-01-15T10:30:00Z")
        val rc = mapper.toRemoteCollection(dto)
        assertEquals("c1", rc.remoteId)
        assertEquals(listOf("s1"), rc.memberRemoteIds)
        assertEquals(1705314600000L, rc.updatedAt)
    }

    @Test fun `toRemoteCollection fuer ReadList mappt updatedAt`() {
        val dto = ReadListDto(id = "r1", name = "Y", bookIds = listOf("b1"), lastModifiedDate = "2024-01-15T10:30:00Z")
        val rc = mapper.toRemoteCollection(dto)
        assertEquals("r1", rc.remoteId)
        assertEquals(listOf("b1"), rc.memberRemoteIds)
        assertEquals(1705314600000L, rc.updatedAt)
    }
}
