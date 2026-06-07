package com.komgareader.domain.color

/**
 * Baut eine row-major 4x5-ColorMatrix (FloatArray, länge 20) im 0..255-Wertebereich,
 * kompatibel mit androidx.compose.ui.graphics.ColorMatrix.
 *
 * Reihenfolge der Wirkung: Sättigung (Rec.709-Luminanz) → Kontrast (Pivot 127.5) →
 * lineare Helligkeit. Da Kontrast/Helligkeit reine Skalierung+Offset sind, lässt sich
 * die Verkettung analytisch zusammenfassen: out = c * (Sat·in) + ((1-c)*127.5 + b*255).
 *
 * @param saturation 1.0 = neutral, 0.0 = Graustufen.
 * @param contrast   1.0 = neutral.
 * @param brightness 0.0 = neutral; 1.0 entspricht +255.
 */
fun buildColorMatrix(saturation: Float, contrast: Float, brightness: Float): FloatArray {
    val lr = 0.213f
    val lg = 0.715f
    val lb = 0.072f
    val s = saturation
    val inv = 1f - s
    val m00 = lr * inv + s; val m01 = lg * inv;      val m02 = lb * inv
    val m10 = lr * inv;     val m11 = lg * inv + s;  val m12 = lb * inv
    val m20 = lr * inv;     val m21 = lg * inv;      val m22 = lb * inv + s
    val c = contrast
    val offset = (1f - c) * 127.5f + brightness * 255f
    return floatArrayOf(
        c * m00, c * m01, c * m02, 0f, offset,
        c * m10, c * m11, c * m12, 0f, offset,
        c * m20, c * m21, c * m22, 0f, offset,
        0f,      0f,      0f,      1f, 0f,
    )
}
