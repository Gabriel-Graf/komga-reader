package com.komgareader.app.ui.reader

import com.panela.comiccutter.NormRect
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals

class MisdetectionSidecarTest {
    @Test
    fun serializes_panels_to_pixel_space_items() {
        val panels = listOf(NormRect(left = 0.1f, top = 0.2f, width = 0.3f, height = 0.4f, score = 0.83f))
        val json = misdetectionSidecarJson(panels, imageW = 1000, imageH = 2000)
        val items = JSONObject(json).getJSONArray("items")
        assertEquals(1, items.length())
        val box = items.getJSONObject(0).getJSONArray("box")
        assertEquals(100, box.getInt(0)); assertEquals(400, box.getInt(1))
        assertEquals(300, box.getInt(2)); assertEquals(800, box.getInt(3))
        assertEquals("panel", items.getJSONObject(0).getString("label"))
        assertEquals(0.83, items.getJSONObject(0).getDouble("score"), 0.0001)
    }
}
