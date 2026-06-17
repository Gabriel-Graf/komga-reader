package com.komgareader.domain.usecase

import com.komgareader.domain.model.ContentSignals
import com.komgareader.domain.model.ContentType

/**
 * Pure content-type suggestion from sampled interior pages. Returns `null` when the
 * evidence is inconclusive — callers must NOT guess. Order strong → weak:
 *   1. median page aspect (h/w) >= [WEBTOON_MIN_ASPECT]  -> WEBTOON (long strips)
 *   2. median gray fraction    >= [MANGA_MIN_GRAY]       -> MANGA  (B/W interior)
 *   3. median gray fraction    <= [COMIC_MAX_GRAY]       -> COMIC  (coloured interior)
 *   4. otherwise -> null (ambiguous mid band)
 *
 * Medians (not means) so a single coloured spread / tall outlier cannot flip the verdict.
 * Grayscale decides B/W-vs-colour ONLY — never reading direction (RTL stays metadata-driven).
 */
class SuggestContentType {
    operator fun invoke(signals: ContentSignals): ContentType? {
        val samples = signals.samples
        if (samples.isEmpty()) return null
        val medianAspect = samples
            .map { it.heightPx.toFloat() / it.widthPx.toFloat() }
            .median()
        if (medianAspect >= WEBTOON_MIN_ASPECT) return ContentType.WEBTOON
        val medianGray = samples.map { it.grayFraction }.median()
        if (medianGray >= MANGA_MIN_GRAY) return ContentType.MANGA
        if (medianGray <= COMIC_MAX_GRAY) return ContentType.COMIC
        return null
    }

    private fun List<Float>.median(): Float {
        val sorted = sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2f
    }

    companion object {
        // Calibrated against the labelled NAS corpus (per-file median over 3 interior samples,
        // box-downsampled to 200px long edge — same as ContentTypeDetector):
        //   manga   grayFraction = 1.000 at every percentile (true-grayscale storage); aspect <= 1.50
        //   comic   grayFraction median 0.44, p90 0.56 (coloured);                     aspect <= 1.54
        //   webtoon true strips (Solo Levelling): aspect 3.15..5.56 (100% >= 3.0)
        //           paginated (Tower of God): aspect <= 1.83, gray coloured — stored as normal pages
        // Aspect gap is clean+empty between normal pages (<=1.83) and true strips (>=3.15), so
        // WEBTOON_MIN_ASPECT = 2.0 catches strips (incl. moderately tall ones from other sources)
        // with wide margin above any real comic/manga page. Manga separable on gray (>=0.96
        // excludes even the greyest webtoon at 0.977). A *paginated* webtoon is physically just
        // normal pages — no pixel signal can tell it from a comic; it falls to null (no guess) and
        // needs the library/server reading-direction tag.
        const val WEBTOON_MIN_ASPECT = 2.0f
        const val MANGA_MIN_GRAY = 0.96f
        const val COMIC_MAX_GRAY = 0.60f
    }
}
