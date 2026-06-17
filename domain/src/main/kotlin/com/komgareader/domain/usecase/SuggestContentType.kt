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
        // Calibrated against the labelled NAS corpus (45 books/type, 3 interior samples each,
        // box-downsampled to 200px long edge — same as ContentTypeDetector):
        //   manga   grayFraction = 1.000 at every percentile (true-grayscale storage)
        //   comic   grayFraction median 0.44, p90 0.56 (coloured)
        //   webtoon grayFraction median 0.67, max 0.98; aspect median 1.71, p90 5.56
        // Manga is cleanly separable on gray (>=0.96 excludes even the greyest webtoon at 0.977).
        // Webtoon is only reliably caught when stored as a tall strip (aspect >=3); paginated
        // webtoons overlap comics on both signals and fall to null (no guess) — see KDoc.
        const val WEBTOON_MIN_ASPECT = 3.0f
        const val MANGA_MIN_GRAY = 0.96f
        const val COMIC_MAX_GRAY = 0.60f
    }
}
