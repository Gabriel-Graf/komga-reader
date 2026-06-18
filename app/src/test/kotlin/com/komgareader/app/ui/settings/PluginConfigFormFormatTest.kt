package com.komgareader.app.ui.settings

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.util.Locale

/**
 * Guards the locale round-trip of the NUMBER-field slider. The stored value is later parsed with
 * [String.toDoubleOrNull], which accepts ONLY a dot decimal separator. Formatting with the default
 * locale emits a comma on e.g. German devices ("0,15"), so the parse returns null and the slider
 * snaps back to its minimum — the "stuck at 0.1" bug. [formatForStep] must always use a dot.
 */
class PluginConfigFormFormatTest {

    private val original = Locale.getDefault()

    @AfterEach fun restore() = Locale.setDefault(original)

    @Test fun `formats with a dot even under a comma-decimal locale`() {
        Locale.setDefault(Locale.GERMANY)
        assertEquals("0.15", formatForStep(0.15, 0.05))
        assertEquals("0.55", formatForStep(0.55, 0.05))
    }

    @Test fun `formatted value round-trips through toDoubleOrNull under German locale`() {
        Locale.setDefault(Locale.GERMANY)
        // Every step the slider writes must parse back to the same number — otherwise it pins at min.
        var value = 0.1
        repeat(9) {
            value += 0.05
            val stored = formatForStep(value, 0.05)
            val parsed = stored.toDoubleOrNull()
            assertNotNull(parsed, "stored value '$stored' must parse with a dot separator")
            assertEquals(value, parsed!!, 1e-9)
        }
    }

    @Test fun `decimal precision follows the step granularity`() {
        Locale.setDefault(Locale.GERMANY)
        assertEquals("0.250", formatForStep(0.25, 0.005)) // finer step → more decimals, no pin
        assertEquals("0.50", formatForStep(0.5, 0.5))     // coarse step → still at least 2 decimals
    }
}
