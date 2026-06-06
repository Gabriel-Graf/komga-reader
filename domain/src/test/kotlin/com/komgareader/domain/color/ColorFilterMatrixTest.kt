package com.komgareader.domain.color

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class ColorFilterMatrixTest {

    private val tol = 1e-4f

    /** Float-toleranter Vergleich als Ersatz für assertEquals mit Delta-Parameter. */
    private fun assertNear(expected: Float, actual: Float, msg: String? = null) {
        assertTrue(abs(expected - actual) <= tol, "${msg ?: ""} expected=$expected actual=$actual delta=${abs(expected - actual)}")
    }

    @Test
    fun `neutrale Werte ergeben die Identitaetsmatrix`() {
        val m = buildColorMatrix(saturation = 1f, contrast = 1f, brightness = 0f)
        val expected = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
        for (i in expected.indices) assertNear(expected[i], m[i], "Index $i")
    }

    @Test
    fun `Saettigung 0 mappt jeden Kanal auf die Rec709-Luminanz`() {
        val m = buildColorMatrix(saturation = 0f, contrast = 1f, brightness = 0f)
        listOf(0, 5, 10).forEach { row ->
            assertNear(0.213f, m[row + 0])
            assertNear(0.715f, m[row + 1])
            assertNear(0.072f, m[row + 2])
        }
    }

    @Test
    fun `Kontrast 0_5 halbiert die Diagonale und versetzt um den Pivot`() {
        val m = buildColorMatrix(saturation = 1f, contrast = 0.5f, brightness = 0f)
        assertNear(0.5f, m[0])
        assertNear(0.5f * 127.5f, m[4])
    }

    @Test
    fun `Helligkeit addiert einen linearen Offset`() {
        val m = buildColorMatrix(saturation = 1f, contrast = 1f, brightness = 0.5f)
        assertNear(0.5f * 255f, m[4])
        assertNear(0.5f * 255f, m[9])
        assertNear(0.5f * 255f, m[14])
    }

    @Test
    fun `Alpha-Zeile bleibt unveraendert`() {
        val m = buildColorMatrix(saturation = 1.4f, contrast = 1.2f, brightness = 0.1f)
        assertNear(0f, m[15]); assertNear(0f, m[16])
        assertNear(0f, m[17]); assertNear(1f, m[18]); assertNear(0f, m[19])
    }
}
