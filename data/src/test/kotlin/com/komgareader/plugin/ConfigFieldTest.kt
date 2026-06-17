package com.komgareader.plugin

import kotlin.test.Test
import kotlin.test.assertEquals

class ConfigFieldTest {
    @Test
    fun number_field_carries_range() {
        val f = ConfigField("min_confidence", "Mindest-Confidence", FieldType.NUMBER, required = false, default = "0.25", min = 0.1, max = 1.0, step = 0.05)
        assertEquals(FieldType.NUMBER, f.type)
        assertEquals(0.1, f.min)
        assertEquals(1.0, f.max)
        assertEquals(0.05, f.step)
    }
}
