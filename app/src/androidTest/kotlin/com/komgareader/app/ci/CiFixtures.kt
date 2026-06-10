package com.komgareader.app.ci

/**
 * Erwartete Fixture-Inhalte zum Asserten in Seam-Tests. Spiegelt
 * `tools/ci-fixtures/manifest.json` (SSOT) — bei Fixture-Änderung beide nachziehen.
 * (Die Tests laufen on-device und können das host-seitige manifest.json nicht lesen,
 * daher hier als Konstanten.)
 */
object CiFixtures {
    const val MANGA_SERIES = "Sample-Manga"        // komga-a, 2 Bände
    const val WEBTOON_SERIES = "Sample-Webtoon"    // komga-b, 1 Band
    val NOVELS_A = listOf("Alpha-Novel", "Beta-Novel")  // komga-a
    val NOVELS_B = listOf("Gamma-Novel")                // komga-b

    /** Erwartete Serien-Gesamtzahl je Instanz (Manga 1 + Novels-A 2; Webtoon 1 + Novels-B 1). */
    const val SERIES_TOTAL_A = 3
    const val SERIES_TOTAL_B = 2
    const val SERIES_TOTAL_C = 3   // Spiegel von A
}
