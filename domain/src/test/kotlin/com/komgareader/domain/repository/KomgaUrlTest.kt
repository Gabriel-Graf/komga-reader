package com.komgareader.domain.repository

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class KomgaUrlTest {

    @Test
    fun `normalisiert URL ohne Pfad`() {
        assertEquals(
            "https://komga.intern.gg-tech.work/api/v1/",
            KomgaUrl.normalize("https://komga.intern.gg-tech.work"),
        )
    }

    @Test
    fun `normalisiert URL mit abschliessendem Slash`() {
        assertEquals(
            "https://komga.intern.gg-tech.work/api/v1/",
            KomgaUrl.normalize("https://komga.intern.gg-tech.work/"),
        )
    }

    @Test
    fun `behaelt vollstaendigen API-Pfad mit Slash unveraendert`() {
        assertEquals(
            "https://komga.intern.gg-tech.work/api/v1/",
            KomgaUrl.normalize("https://komga.intern.gg-tech.work/api/v1/"),
        )
    }

    @Test
    fun `fuegt Slash an API-Pfad ohne abschliessenden Slash an`() {
        assertEquals(
            "https://komga.intern.gg-tech.work/api/v1/",
            KomgaUrl.normalize("https://komga.intern.gg-tech.work/api/v1"),
        )
    }

    @Test
    fun `ergaenzt fehlendes Schema https`() {
        assertEquals(
            "https://komga.intern.gg-tech.work/api/v1/",
            KomgaUrl.normalize("komga.intern.gg-tech.work"),
        )
    }

    @Test
    fun `trimmt Leerzeichen am Anfang und Ende`() {
        assertEquals(
            "https://komga.intern.gg-tech.work/api/v1/",
            KomgaUrl.normalize("  https://komga.intern.gg-tech.work  "),
        )
    }
}
