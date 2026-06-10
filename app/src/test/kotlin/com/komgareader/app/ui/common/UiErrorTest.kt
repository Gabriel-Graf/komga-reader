package com.komgareader.app.ui.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.IOException

class UiErrorTest {

    @Test fun `HTTP 401 wird auf UNAUTHORIZED abgebildet, Detail roh`() {
        val e = uiErrorOf(RuntimeException("HTTP 401 Unauthorized"))
        assertEquals(ErrorKind.UNAUTHORIZED, e.kind)
        assertEquals("HTTP 401 Unauthorized", e.detail)
    }

    @Test fun `HTTP 403 wird auf FORBIDDEN abgebildet`() {
        assertEquals(ErrorKind.FORBIDDEN, uiErrorOf(RuntimeException("HTTP 403 Forbidden")).kind)
    }

    @Test fun `HTTP 404 wird auf NOT_FOUND abgebildet`() {
        assertEquals(ErrorKind.NOT_FOUND, uiErrorOf(RuntimeException("HTTP 404 Not Found")).kind)
    }

    @Test fun `HTTP 500 wird auf SERVER abgebildet`() {
        assertEquals(ErrorKind.SERVER, uiErrorOf(RuntimeException("HTTP 500 Internal Server Error")).kind)
    }

    @Test fun `HTTP 503 wird auf SERVER abgebildet`() {
        assertEquals(ErrorKind.SERVER, uiErrorOf(RuntimeException("HTTP 503 Service Unavailable")).kind)
    }

    @Test fun `RuntimeException mit IOException-Ursache ohne Code wird NO_CONNECTION`() {
        val cause = IOException("connection refused")
        val e = uiErrorOf(RuntimeException("Wrapper", cause))
        assertEquals(ErrorKind.NO_CONNECTION, e.kind)
        assertEquals("Wrapper", e.detail)
    }

    @Test fun `direkte IOException wird NO_CONNECTION`() {
        val e = uiErrorOf(IOException("Failed to connect to /10.0.2.2:25600"))
        assertEquals(ErrorKind.NO_CONNECTION, e.kind)
        assertEquals("Failed to connect to /10.0.2.2:25600", e.detail)
    }

    @Test fun `null wird UNKNOWN mit leerem Detail`() {
        val e = uiErrorOf(null)
        assertEquals(ErrorKind.UNKNOWN, e.kind)
        assertEquals("", e.detail)
    }

    @Test fun `generischer Fehler ohne Code und ohne IO wird UNKNOWN`() {
        val e = uiErrorOf(Exception("weird"))
        assertEquals(ErrorKind.UNKNOWN, e.kind)
        assertEquals("weird", e.detail)
    }

    @Test fun `sonstiger 4xx-Code wird UNKNOWN`() {
        assertEquals(ErrorKind.UNKNOWN, uiErrorOf(RuntimeException("HTTP 418 I'm a teapot")).kind)
    }
}
