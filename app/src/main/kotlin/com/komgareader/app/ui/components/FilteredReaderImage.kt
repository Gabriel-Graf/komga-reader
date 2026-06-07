package com.komgareader.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.komgareader.app.color.ColorPipelineTransformation
import com.komgareader.domain.color.applyPixelPipeline
import com.komgareader.domain.model.ColorProfile

/**
 * Aktives Profil für den Reader-Pfad. Wird in MainActivity bereitgestellt. Cover lesen weiter
 * [LocalImageFilter] (nur GPU-Matrix); Reader-Wrapper lesen DIESES, um die volle Pixel-Pipeline
 * zu entscheiden.
 */
val LocalColorProfile = staticCompositionLocalOf { ColorProfile.OFF }

/**
 * Coil-`AsyncImage` für **Reader-Seiten**. Wenn das aktive Profil [ColorProfile.needsPixelPipeline]
 * verlangt, läuft die volle Pixel-Pipeline als Coil-Transformation (colorFilter = null, da der
 * Kernel die lineare Stufe mitmacht). Sonst nur die billige GPU-Matrix (wie Cover).
 * [profileOverride] übersteuert das aktive Profil (Live-Vorschau im Editor).
 * [onState] meldet den Coil-Ladezustand (z. B. damit der Webtoon-Reader die Platzhalter-
 * Höhe erst nach erfolgreichem Laden auf die echte Bildhöhe freigibt).
 */
@Composable
fun FilteredReaderAsyncImage(
    model: ImageRequest,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    profileOverride: ColorProfile? = null,
    onState: ((AsyncImagePainter.State) -> Unit)? = null,
) {
    val profile = profileOverride ?: LocalColorProfile.current
    if (profile.needsPixelPipeline) {
        val req = remember(model, profile) {
            model.newBuilder().transformations(ColorPipelineTransformation(profile)).build()
        }
        AsyncImage(model = req, contentDescription = contentDescription, contentScale = contentScale, colorFilter = null, onState = onState, modifier = modifier)
    } else {
        AsyncImage(model = model, contentDescription = contentDescription, contentScale = contentScale, colorFilter = profile.toColorFilterOrNull(), onState = onState, modifier = modifier)
    }
}

/**
 * Compose-`Image` für **MuPDF-Reader-Seiten** (EPUB/PDF). Verarbeitet das Bitmap pixelweise,
 * wenn das aktive Profil es verlangt; sonst GPU-Matrix. Verarbeitung ist auf (Bitmap, Profil)
 * gemerkt — kein Neurechnen pro Recomposition.
 */
@Composable
fun FilteredReaderImage(
    bitmap: Bitmap,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    profileOverride: ColorProfile? = null,
) {
    val profile = profileOverride ?: LocalColorProfile.current
    if (profile.needsPixelPipeline) {
        val processed = remember(bitmap, profile) {
            val out = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            val w = out.width; val h = out.height
            val px = IntArray(w * h)
            out.getPixels(px, 0, w, 0, 0, w, h)
            applyPixelPipeline(px, w, h, profile)
            out.setPixels(px, 0, w, 0, 0, w, h)
            out.asImageBitmap()
        }
        Image(bitmap = processed, contentDescription = contentDescription, contentScale = contentScale, colorFilter = null, modifier = modifier)
    } else {
        Image(bitmap = bitmap.asImageBitmap(), contentDescription = contentDescription, contentScale = contentScale, colorFilter = profile.toColorFilterOrNull(), modifier = modifier)
    }
}
