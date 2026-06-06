package com.komgareader.data.repository

import com.komgareader.domain.model.ShelfSource
import kotlin.test.Test
import kotlin.test.assertEquals

class RoomShelfRepositoryEncodingTest {

    @Test
    fun `encode und decode sind invers fuer mehrere Quellen mit Containern`() {
        val sources = listOf(
            ShelfSource(sourceId = 11L, containerIds = listOf("L1", "L2")),
            ShelfSource(sourceId = 22L, containerIds = emptyList()),
        )
        val encoded = ShelfSourceCodec.encode(sources)
        assertEquals(sources, ShelfSourceCodec.decode(encoded))
    }

    @Test
    fun `decode toleriert leeren String`() {
        assertEquals(emptyList(), ShelfSourceCodec.decode(""))
    }

    @Test
    fun `decode der Migrations-Form ganze-Quelle ergibt leere Container`() {
        // MIGRATION_5_6 wandelt altes "11,22" in "11=|22="
        assertEquals(
            listOf(ShelfSource(11L, emptyList()), ShelfSource(22L, emptyList())),
            ShelfSourceCodec.decode("11=|22="),
        )
    }
}
