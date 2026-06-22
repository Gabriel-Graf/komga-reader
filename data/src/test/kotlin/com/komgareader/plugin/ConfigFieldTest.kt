package com.komgareader.plugin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConfigFieldTest {
    @Test
    fun number_field_carries_range() {
        val f = ConfigField("min_confidence", "Mindest-Confidence", FieldType.NUMBER, required = false, default = "0.25", min = 0.1, max = 1.0, step = 0.05)
        assertEquals(FieldType.NUMBER, f.type)
        assertEquals(0.1, f.min)
        assertEquals(1.0, f.max)
        assertEquals(0.05, f.step)
    }

    /**
     * ABI binary-compatibility guard. Plugin APKs built against the pre-NUMBER SDK emit a call to
     * the 5-arg constructor `(String, String, FieldType, boolean, String)`. Adding min/max/step to
     * the data class changed the primary constructor's JVM signature and dropped that overload, so
     * an old plugin throws NoSuchMethodError at load (observed on-device with the Kavita plugin).
     * Reflection — not a plain Kotlin call — is required: a source-level call always recompiles
     * against the current class and can never reproduce the binary break. This asserts the explicit
     * legacy constructor stays present and leaves the new fields null.
     */
    @Test
    fun legacy_five_arg_constructor_stays_binary_compatible() {
        val ctor = ConfigField::class.java.getDeclaredConstructor(
            String::class.java,
            String::class.java,
            FieldType::class.java,
            Boolean::class.javaPrimitiveType,
            String::class.java,
        )
        val field = ctor.newInstance("url", "Server-URL", FieldType.URL, true, "")
        assertEquals("url", field.key)
        assertEquals(FieldType.URL, field.type)
        assertNull(field.min)
        assertNull(field.max)
        assertNull(field.step)
    }
}
