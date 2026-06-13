package com.komgareader.app

import com.komgareader.domain.model.SourceKind
import com.komgareader.domain.repository.ServerConfig
import org.junit.Assume

/**
 * Connection for the **dev-local** live tests (`*LiveTest` / `*InstrumentedTest`) that run against
 * your own Komga docker — NOT the CI integration suite in the `ci.` package, which uses static
 * fixture Basic-Auth (see [com.komgareader.app.ci.CiKomga]).
 *
 * `baseUrl` + `apiKey` come from `BuildConfig`, which `app/build.gradle.kts` sources from a Gradle
 * property / env var / the **gitignored** `local.properties` — the key is never committed. To run
 * these tests, set `komga.test.apiKey=<your key>` in `local.properties` (see CONTRIBUTING.md).
 * When no key is configured the tests **skip** (via `assumeTrue`) instead of failing.
 */
object LocalTestServer {
    val baseUrl: String get() = BuildConfig.KOMGA_TEST_BASE_URL
    val apiKey: String get() = BuildConfig.KOMGA_TEST_API_KEY

    /** Skip the calling test when no dev-local key is configured. */
    fun assumeConfigured() = Assume.assumeTrue(
        "Set komga.test.apiKey in local.properties (or the KOMGA_TEST_API_KEY env var) to run the dev-local live tests",
        apiKey.isNotBlank(),
    )

    /** A [ServerConfig] for the dev-local Komga; skips the test if no key is configured. */
    fun config(name: String = "Test", kind: SourceKind = SourceKind.KOMGA): ServerConfig {
        assumeConfigured()
        return ServerConfig(name = name, baseUrl = baseUrl, apiKey = apiKey, kind = kind)
    }
}
