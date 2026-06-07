package com.komgareader.guidedview

/**
 * Flutet das vom Seitenrand erreichbare Hintergrund-(Gutter-/Margin-)Netz.
 * Geseedet werden NUR helle Randpixel — dunkle Kanten (Full-Bleed-Panels) bleiben ungeflutet.
 * 4-Konnektivität. Reines Kotlin.
 */
object GutterFill {

    fun floodFromEdges(background: BooleanArray, width: Int, height: Int): BooleanArray {
        val flooded = BooleanArray(background.size)
        val stack = ArrayDeque<Int>()

        fun push(i: Int) {
            if (background[i] && !flooded[i]) { flooded[i] = true; stack.addLast(i) }
        }

        for (x in 0 until width) {
            push(x)
            push((height - 1) * width + x)
        }
        for (y in 0 until height) {
            push(y * width)
            push(y * width + (width - 1))
        }

        while (stack.isNotEmpty()) {
            val i = stack.removeLast()
            val x = i % width
            val y = i / width
            if (x > 0) push(i - 1)
            if (x < width - 1) push(i + 1)
            if (y > 0) push(i - width)
            if (y < height - 1) push(i + width)
        }
        return flooded
    }
}
