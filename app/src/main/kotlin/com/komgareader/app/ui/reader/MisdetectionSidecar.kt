package com.komgareader.app.ui.reader

import com.panela.comiccutter.NormRect
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * Baut das mllabeltool-Prediction-Sidecar (pixel-space) zu den aktuell erkannten (falschen) Panels.
 * Format: { "items": [ { "box": [x,y,w,h], "label": "panel", "score": <float> } ] }.
 */
fun misdetectionSidecarJson(panels: List<NormRect>, imageW: Int, imageH: Int): String {
    val items = JSONArray()
    panels.forEach { p ->
        val box = JSONArray().apply {
            put((p.left * imageW).roundToInt())
            put((p.top * imageH).roundToInt())
            put((p.width * imageW).roundToInt())
            put((p.height * imageH).roundToInt())
        }
        items.put(JSONObject().put("box", box).put("label", "panel").put("score", p.score.toDouble()))
    }
    return JSONObject().put("items", items).toString()
}
