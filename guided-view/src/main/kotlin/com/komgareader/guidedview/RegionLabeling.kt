package com.komgareader.guidedview

/**
 * Labelt zusammenhängende NICHT-geflutete Regionen (= von Guttern umschlossene Panels)
 * per 8-Konnektivitäts-BFS und liefert deren Bounding-Boxes. Reines Kotlin.
 */
object RegionLabeling {

    fun labelRegions(flooded: BooleanArray, width: Int, height: Int): List<PanelRect> {
        val visited = BooleanArray(flooded.size)
        val boxes = mutableListOf<PanelRect>()
        val stack = ArrayDeque<Int>()

        for (start in flooded.indices) {
            if (flooded[start] || visited[start]) continue
            visited[start] = true
            stack.addLast(start)
            var minX = Int.MAX_VALUE; var minY = Int.MAX_VALUE
            var maxX = Int.MIN_VALUE; var maxY = Int.MIN_VALUE
            while (stack.isNotEmpty()) {
                val i = stack.removeLast()
                val x = i % width; val y = i / width
                if (x < minX) minX = x; if (x > maxX) maxX = x
                if (y < minY) minY = y; if (y > maxY) maxY = y
                var dy = -1
                while (dy <= 1) {
                    var dx = -1
                    while (dx <= 1) {
                        if (!(dx == 0 && dy == 0)) {
                            val nx = x + dx; val ny = y + dy
                            if (nx in 0 until width && ny in 0 until height) {
                                val ni = ny * width + nx
                                if (!flooded[ni] && !visited[ni]) { visited[ni] = true; stack.addLast(ni) }
                            }
                        }
                        dx++
                    }
                    dy++
                }
            }
            boxes.add(PanelRect(minX, minY, maxX - minX + 1, maxY - minY + 1))
        }
        return boxes
    }
}
