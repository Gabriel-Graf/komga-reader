package com.komgareader.app.ci

import com.komgareader.domain.model.SourceKind
import com.komgareader.domain.repository.ServerConfig

/**
 * Die CI-Komga-Test-Topologie (Plan 1 `tools/ci-fixtures`). Eine [ServerConfig] pro Instanz.
 *
 * Auth: **statische HTTP Basic Auth** mit den Fixture-Admin-Creds — `KomgaSource` nutzt Basic
 * Auth, wenn `apiKey` leer ist. Dadurch braucht kein Test die dynamisch erzeugten API-Keys aus
 * dem host-seitigen `.keys.env`; die Topologie ist rein statisch und deterministisch.
 *
 * URLs zeigen auf `10.0.2.2` — so erreicht der Emulator die Host-Container.
 */
object CiKomga {
    const val ADMIN_USER = "admin@ci.local"
    const val ADMIN_PASS = "ci-testpass-123"

    private fun komga(name: String, port: Int) = ServerConfig(
        name = name,
        baseUrl = "http://10.0.2.2:$port/api/v1/",
        username = ADMIN_USER,
        password = ADMIN_PASS,
        kind = SourceKind.KOMGA,
    )

    /** komga-a: Manga + Novels-A. */
    val A: ServerConfig = komga("CI-Komga-A", 25701)

    /** komga-b: Webtoon + Novels-B (disjunkt zu A). */
    val B: ServerConfig = komga("CI-Komga-B", 25702)

    /** komga-c: Spiegel von A (für den n-gleiche-Server-Test). */
    val C: ServerConfig = komga("CI-Komga-C", 25703)

    /** OPDS-Sicht auf komga-a (für gemischte Quellenarten). Basic Auth, OPDS-Catalog-Root. */
    val A_OPDS: ServerConfig = ServerConfig(
        name = "CI-Komga-A-OPDS",
        baseUrl = "http://10.0.2.2:25701/opds/v1.2/catalog",
        username = ADMIN_USER,
        password = ADMIN_PASS,
        kind = SourceKind.OPDS,
    )
}
