package com.komgareader.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.komgareader.domain.color.buildColorMatrix
import com.komgareader.domain.model.ColorProfile

/**
 * App-weiter Farbfilter aus dem aktiven [ColorProfile]. `null` = kein Filter.
 * Wird in MainActivity aus dem aktiven Profil bereitgestellt; alle Bild-Wrapper lesen ihn.
 */
val LocalImageFilter = staticCompositionLocalOf<ColorFilter?> { null }

/** Wandelt ein Profil in einen ColorFilter (nur linearer Teil) — oder `null`, wenn linear neutral. */
fun ColorProfile.toColorFilterOrNull(): ColorFilter? =
    if (isLinearNeutral) null
    else ColorFilter.colorMatrix(ColorMatrix(buildColorMatrix(saturation, contrast, brightness)))

/**
 * Coil-`AsyncImage` mit zentralem E-Ink-Farbfilter. Drop-in-Ersatz für `AsyncImage`
 * an allen Cover-/Seiten-Stellen. `colorFilterOverride` übersteuert den globalen Filter
 * (für die Live-Vorschau im Profil-Editor).
 */
@Composable
fun FilteredAsyncImage(
    model: ImageRequest,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    colorFilterOverride: ColorFilter? = null,
    useOverride: Boolean = false,
) {
    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        contentScale = contentScale,
        colorFilter = if (useOverride) colorFilterOverride else LocalImageFilter.current,
        modifier = modifier,
    )
}

/** Compose-`Image` (MuPDF-Bitmap) mit zentralem E-Ink-Farbfilter. */
@Composable
fun FilteredImage(
    bitmap: ImageBitmap,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    Image(
        bitmap = bitmap,
        contentDescription = contentDescription,
        contentScale = contentScale,
        colorFilter = LocalImageFilter.current,
        modifier = modifier,
    )
}
