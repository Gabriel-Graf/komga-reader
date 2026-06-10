# Integrationstest — Plan 2: Harness + Seam-Tests Block A & B

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eine wiederverwendbare On-Device-Test-Harness, die die drei CI-Komga-Instanzen (Plan 1) quellen-agnostisch über `ActiveSource`/`SourceManager` ansteuert, plus die Seam-Tests für Block A (Verbindung & Multi-Source) und Block B (Werk-Auflösung pro Quelle) aus der Spec.

**Architecture:** Instrumented-Tests (`app/src/androidTest`) laufen auf dem Emulator und erreichen die Host-Komga-Instanzen über `10.0.2.2:<port>`. Authentifizierung über **statische HTTP-Basic-Auth-Admin-Creds** (`KomgaSource` nutzt Basic Auth, wenn `apiKey` leer ist) — damit braucht kein Test die dynamisch erzeugten API-Keys aus dem host-seitigen `.keys.env`. Die Harness spiegelt das `MixedSourcesLiveTest`-Muster (inMemory-Room + eindeutiger Keystore-Alias + `SourceRegistration` + `ActiveSource`) in wiederverwendbare Helfer.

**Tech Stack:** Kotlin, JUnit4 (`AndroidJUnit4`), Room (inMemory), kotlinx-coroutines-test, die bestehenden `androidTest`-Deps (kein neues Compose-Test-Dep — das kommt erst in Plan 4 fürs UI-Set).

**Voraussetzung zum Ausführen:** Die CI-Komga-Instanzen müssen laufen — `tools/ci-fixtures/up.sh` (Plan 1) vorher starten. Emulator `eink_test` gestartet.

**Bezug:** Spec `docs/superpowers/specs/2026-06-10-integration-test-suite-design.md` §2, §6, §9 (Block A/B). Regeln: `source-agnostic-integration.md`, `architecture-seams.md`.

---

## Grounding (verifizierte APIs — keine Phantome)

- `ServerConfig(name, baseUrl, apiKey?, username?, password?, kind=SourceKind.KOMGA, id=0)` — `domain/repository`.
- `KomgaSource` macht **Basic Auth**, wenn `apiKey` leer ist und `username`+`password` gesetzt sind (`KomgaSource.kt:183-184`). Admin-Creds der CI-Instanzen: `admin@ci.local` / `ci-testpass-123` (Plan 1 `seed.sh`).
- Harness-Stack (aus `MixedSourcesLiveTest`): `Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()`; `KeystoreCredentialStore("alias-${System.nanoTime()}")`; `RoomServerRepository(db.serverDao(), store)`; `SourceManager()`; `SourceRegistration(sources, KomgaSourceProvider())`; `ActiveSource(sources, repo, registration)`.
- `ActiveSource`: `suspend all(): List<BrowsableSource>`, `suspend get(sourceId: Long): BrowsableSource?`, `suspend current(): BrowsableSource?`.
- `BrowsableSource`: `browse(page: Int, filter: SourceFilter): PagedResult<Series>`, `books(seriesRemoteId): List<Book>`, `openPage(ref): ByteArray`, `coverBytes(remoteId, isSeriesCover): ByteArray`, `pages(bookRemoteId): List<PageRef>`, `seriesIdOf(bookRemoteId): String`.
- `Series(id, sourceId, remoteId, title, contentTypeOverride?, summary?, status?, genres, …)`.
- `Book(id, sourceId, seriesId, remoteId, title, format, pageCount, …)`.
- `SourceKind { KOMGA, LOCAL, OPDS, PLUGIN, UNKNOWN }`.
- `PagedResult<T>(items, hasNextPage)`, `SourceFilter(seriesId?, containerIds)`.
- Fixture-Topologie (Plan 1 `manifest.json`): komga-a:25701 (Manga „Sample-Manga" + Novels-A „Alpha-Novel"/„Beta-Novel"), komga-b:25702 (Webtoon „Sample-Webtoon" + Novels-B „Gamma-Novel"), komga-c:25703 (Spiegel von A).

---

## File Structure

```
app/src/androidTest/kotlin/com/komgareader/app/ci/
├── CiKomga.kt          # Topologie-Konstanten: pro Instanz eine ServerConfig (Basic Auth, 10.0.2.2:<port>)
├── CiFixtures.kt       # Erwartete Serien-Namen/Counts (spiegelt manifest.json; SSOT-Verweis im Doc)
├── CiSourceStack.kt    # Baut inMemory-Room + ActiveSource-Stack (DRY für alle Seam-Tests)
├── BlockAConnectionTest.kt   # A1seam, A2, A3, A4
├── BlockAMixedSourcesTest.kt # A5, A6 (Komga-REST + OPDS gemischt)  — löst MixedSourcesLiveTest ab
└── BlockBResolutionTest.kt   # B7, B8
```

`MixedSourcesLiveTest.kt` wird durch `BlockAMixedSourcesTest` ersetzt (gegen die dedizierte CI-Instanz statt der Dev-Komga) und am Ende gelöscht.

---

## Task 1: CiKomga — Topologie-Konstanten

**Files:**
- Create: `app/src/androidTest/kotlin/com/komgareader/app/ci/CiKomga.kt`

- [ ] **Step 1: CiKomga schreiben**

```kotlin
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
```

- [ ] **Step 2: Kompiliert (kein Test-Run nötig — reine Konstanten)**

Run: `./gradlew :app:compileDebugAndroidTestKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/androidTest/kotlin/com/komgareader/app/ci/CiKomga.kt
git commit -m "test(ci): CiKomga — statische CI-Komga-Topologie (Basic Auth, 10.0.2.2)"
```

---

## Task 2: CiFixtures — erwartete Inhalte

**Files:**
- Create: `app/src/androidTest/kotlin/com/komgareader/app/ci/CiFixtures.kt`

- [ ] **Step 1: CiFixtures schreiben**

```kotlin
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
```

- [ ] **Step 2: Commit**

```bash
git add app/src/androidTest/kotlin/com/komgareader/app/ci/CiFixtures.kt
git commit -m "test(ci): CiFixtures — erwartete Inhalte (spiegelt manifest.json)"
```

---

## Task 3: CiSourceStack — wiederverwendbarer ActiveSource-Stack

**Files:**
- Create: `app/src/androidTest/kotlin/com/komgareader/app/ci/CiSourceStack.kt`

- [ ] **Step 1: CiSourceStack schreiben**

```kotlin
package com.komgareader.app.ci

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.komgareader.app.data.ActiveSource
import com.komgareader.app.data.KomgaSourceProvider
import com.komgareader.app.data.SourceRegistration
import com.komgareader.data.db.AppDatabase
import com.komgareader.data.repository.RoomServerRepository
import com.komgareader.data.security.KeystoreCredentialStore
import com.komgareader.domain.repository.ServerConfig
import com.komgareader.domain.source.SourceManager

/**
 * Baut einen frischen, isolierten quellen-agnostischen Stack für einen Seam-Test:
 * inMemory-Room (kein Zugriff auf echte App-DB) + eindeutiger Keystore-Alias (keine
 * Kollision mit App-Credentials, kein Wipe echter Daten) + verdrahtete [ActiveSource].
 *
 * Spiegelt das etablierte `MixedSourcesLiveTest`-Setup, an genau einer Stelle (DRY).
 * [close] schließt die DB — im `@After` aufrufen.
 */
class CiSourceStack {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()
    private val store = KeystoreCredentialStore("ci-seam-${System.nanoTime()}")
    private val repo = RoomServerRepository(db.serverDao(), store)
    private val sources = SourceManager()
    private val registration = SourceRegistration(sources, KomgaSourceProvider())

    val activeSource = ActiveSource(sources, repo, registration)

    /** Persistiert die gegebenen Server-Konfigurationen (wie der echte Settings-Flow). */
    suspend fun register(vararg configs: ServerConfig) {
        configs.forEach { repo.save(it) }
    }

    /** Entfernt eine zuvor registrierte Verbindung über ihre Rowid. */
    suspend fun remove(rowId: Long) = repo.remove(rowId)

    fun close() = db.close()
}
```

> Verifiziert: `ServerRepository.remove(id: Long)` (`RoomServerRepository.kt:52` → `dao.delete(id)`).

- [ ] **Step 2: Kompiliert**

Run: `./gradlew :app:compileDebugAndroidTestKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/androidTest/kotlin/com/komgareader/app/ci/CiSourceStack.kt
git commit -m "test(ci): CiSourceStack — isolierter ActiveSource-Stack (DRY, MixedSources-Muster)"
```

---

## Task 4: Block A — Verbindung & Multi-Source

**Files:**
- Create: `app/src/androidTest/kotlin/com/komgareader/app/ci/BlockAConnectionTest.kt`

Diese Tests verifizieren existierendes Verhalten gegen die **live** CI-Instanzen. „Rot zuerst"
ist hier kein neues Feature, sondern ein **Kopplungs-Detektor**: schlägt ein Test fehl, ist die
Naht A verletzt (Spec §9 Block A). Jeder Test fährt einen frischen `CiSourceStack`.

- [ ] **Step 1: Testklasse schreiben (A1seam, A2, A3, A4)**

```kotlin
package com.komgareader.app.ci

import com.komgareader.domain.model.SourceKind
import com.komgareader.domain.source.SourceFilter
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/** Spec §9 Block A — Verbindung & Multi-Source (Naht A). Live gegen die CI-Komga-Instanzen. */
@RunWith(AndroidJUnit4::class)
class BlockAConnectionTest {

    private lateinit var stack: CiSourceStack

    @Before fun setUp() { stack = CiSourceStack() }
    @After fun tearDown() { stack.close() }

    private suspend fun titlesOf(source: com.komgareader.domain.source.BrowsableSource): List<String> =
        source.browse(0, SourceFilter()).items.map { it.title }

    /** A1: Eine Quelle verbunden → Bibliothek der Quelle ist browsebar. */
    @Test fun a1_eine_quelle_verbunden_liefert_bibliothek() = runTest {
        stack.register(CiKomga.A)
        val all = stack.activeSource.all()
        assertEquals("Genau eine Quelle erwartet", 1, all.size)
        val titles = titlesOf(all.first())
        assertTrue("Manga-Serie muss erscheinen: $titles", titles.contains(CiFixtures.MANGA_SERIES))
    }

    /** A2: n unterschiedliche Server (A+B) → all() aggregiert beide, sourceId stimmt je Werk. */
    @Test fun a2_zwei_unterschiedliche_server_aggregiert() = runTest {
        stack.register(CiKomga.A, CiKomga.B)
        val all = stack.activeSource.all()
        assertEquals("Zwei Quellen erwartet", 2, all.size)

        val fromA = all.first { CiFixtures.MANGA_SERIES in titlesOf(it) }
        val fromB = all.first { CiFixtures.WEBTOON_SERIES in titlesOf(it) }
        assertNotEquals("A und B müssen unterschiedliche sourceIds haben", fromA.id, fromB.id)

        // Jede Serie trägt die sourceId IHRER Quelle (nicht „die erste/aktive").
        val mangaSeries = fromA.browse(0, SourceFilter()).items.first { it.title == CiFixtures.MANGA_SERIES }
        assertEquals("Manga-Serie muss sourceId von A tragen", fromA.id, mangaSeries.sourceId)
        val webtoonSeries = fromB.browse(0, SourceFilter()).items.first { it.title == CiFixtures.WEBTOON_SERIES }
        assertEquals("Webtoon-Serie muss sourceId von B tragen", fromB.id, webtoonSeries.sourceId)
    }

    /** A3a: derselbe Server zweimal registriert → stabile sourceId → eine Quelle (Dedup). */
    @Test fun a3_gleicher_server_doppelt_dedupliziert() = runTest {
        // Zweite Registrierung mit identischer Konfiguration (anderer Settings-Eintrag, gleiche URL/Creds).
        stack.register(CiKomga.A, CiKomga.A.copy(name = CiKomga.A.name))
        val all = stack.activeSource.all()
        assertEquals("Identische Quelle darf nur EINE sourceId erzeugen (deterministischer Hash)", 1, all.size)
    }

    /** A3b: Spiegel-Server C (gleicher Inhalt, andere URL) → zwei verschiedene sourceIds. */
    @Test fun a3_spiegel_server_zwei_quellen() = runTest {
        stack.register(CiKomga.A, CiKomga.C)
        val all = stack.activeSource.all()
        assertEquals("A und Spiegel-C sind zwei verschiedene Quellen", 2, all.size)
        // Gleicher Inhalt, aber getrennte Identitäten.
        assertNotEquals(all[0].id, all[1].id)
    }

    /** A4: Server entfernen → er verschwindet aus all(), die übrige Bibliothek bleibt. */
    @Test fun a4_server_entfernen_bricht_bibliothek_nicht() = runTest {
        stack.register(CiKomga.A, CiKomga.B)
        assertEquals(2, stack.activeSource.all().size)

        // B (zweite gespeicherte Verbindung) hat Rowid 2 (A=1). Robuster: über configs die Rowid finden.
        stack.remove(2)
        val all = stack.activeSource.all()
        assertEquals("Nach Entfernen bleibt eine Quelle", 1, all.size)
        assertEquals("Verbleibende Quelle ist A (KOMGA, Manga)", SourceKind.KOMGA, all.first().kind)
        assertTrue("A bleibt browsebar", CiFixtures.MANGA_SERIES in titlesOf(all.first()))
    }
}
```

> Implementer-Hinweis A4: Die Annahme „B hat Rowid 2" hängt an der Insert-Reihenfolge in
> `register(A, B)` (A zuerst → Rowid 1, B → Rowid 2). Falls `RoomServerRepository.save`
> keine aufsteigenden Rowids ab 1 vergibt, stattdessen die Rowid über `repo.configs.first()`
> (die Verbindung mit `baseUrl == CiKomga.B.baseUrl`) auflösen und deren `id` an `remove`
> geben. Im Report vermerken, welcher Weg nötig war.

- [ ] **Step 2: Emulator + Fixtures laufen lassen, Test ausführen**

Run (Fixtures via Plan 1 müssen laufen): `tools/ci-fixtures/up.sh` (einmal), dann
`ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.komgareader.app.ci.BlockAConnectionTest`
Expected: 5 Tests grün (a1, a2, a3a, a3b, a4).

- [ ] **Step 3: Falls a3a (Dedup) fehlschlägt**

Das wäre ein echtes Finding: identische Configs erzeugen unterschiedliche `sourceId`s →
`SourceId`-Ableitung ist nicht deterministisch. NICHT den Test aufweichen — als BLOCKED melden
mit der beobachteten ID-Differenz; das ist ein Architektur-Bug (Spec §9 A3, `architecture-seams.md`).

- [ ] **Step 4: Commit**

```bash
git add app/src/androidTest/kotlin/com/komgareader/app/ci/BlockAConnectionTest.kt
git commit -m "test(ci): Block A — Verbindung & Multi-Source (A1/A2/A3/A4) gegen CI-Komga"
```

---

## Task 5: Block A — gemischte Quellenarten (A5, A6)

**Files:**
- Create: `app/src/androidTest/kotlin/com/komgareader/app/ci/BlockAMixedSourcesTest.kt`
- Delete (am Ende): `app/src/androidTest/kotlin/com/komgareader/app/MixedSourcesLiveTest.kt`

- [ ] **Step 1: Testklasse schreiben (A5, A6)**

```kotlin
package com.komgareader.app.ci

import com.komgareader.domain.model.SourceKind
import com.komgareader.domain.source.BrowsableSource
import com.komgareader.domain.source.SourceFilter
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/**
 * Spec §9 Block A — gemischte Quellenarten live (Komga-REST + OPDS gegen dieselbe CI-Instanz A).
 * Löst `MixedSourcesLiveTest` ab (gegen die dedizierte CI-Topologie statt der Dev-Komga).
 */
@RunWith(AndroidJUnit4::class)
class BlockAMixedSourcesTest {

    private lateinit var stack: CiSourceStack

    @Before fun setUp() { stack = CiSourceStack() }
    @After fun tearDown() { stack.close() }

    /** A5: Komga-REST + OPDS gleichzeitig → beide aggregiert, beide browsebar, verschiedene IDs. */
    @Test fun a5_komga_rest_und_opds_gemischt() = runTest {
        stack.register(CiKomga.A, CiKomga.A_OPDS)
        val all = stack.activeSource.all()
        assertTrue("Mind. 2 Quellen erwartet, war ${all.size}", all.size >= 2)
        assertTrue("Komga-REST muss dabei sein", all.any { it.kind == SourceKind.KOMGA })
        assertTrue("OPDS muss dabei sein", all.any { it.kind == SourceKind.OPDS })

        val komga = all.first { it.kind == SourceKind.KOMGA }
        val opds = all.first { it.kind == SourceKind.OPDS }
        assertNotEquals("Quellen müssen verschiedene IDs haben", komga.id, opds.id)

        val opdsItems = opds.browse(0, SourceFilter()).items
        assertTrue("OPDS-Katalog muss mind. ein Werk liefern", opdsItems.isNotEmpty())
    }

    /**
     * A6 (Lackmustest): dieselbe agnostische Operation (`browse` über `BrowsableSource`) liefert
     * für KOMGA UND OPDS ein nicht-leeres Ergebnis — ohne quellen-spezifischen Code-Pfad.
     */
    @Test fun a6_lackmustest_agnostisch_ueber_browsable_source() = runTest {
        stack.register(CiKomga.A, CiKomga.A_OPDS)
        val all = stack.activeSource.all()
        // Identischer Aufruf, egal welche konkrete Quelle dahinter steckt.
        val agnostic: (BrowsableSource) -> Int = { /* keine Typprüfung! */ 0 }
        for (s in all) {
            val items = s.browse(0, SourceFilter()).items
            assertTrue("Quelle ${s.kind} muss agnostisch browsebar sein", items.isNotEmpty())
            agnostic(s)
        }
    }
}
```

- [ ] **Step 2: Ausführen**

Run: `ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.komgareader.app.ci.BlockAMixedSourcesTest`
Expected: 2 Tests grün. (OPDS-Catalog-URL ggf. anpassen, falls Komga den Root anders ausliefert —
A5 zeigt dann „leeres Werk", dann auf `/opds/v1.2/series/<id>` der Manga-Serie zeigen wie im
alten `MixedSourcesLiveTest`.)

- [ ] **Step 3: Alten Live-Test löschen + Commit**

```bash
git rm app/src/androidTest/kotlin/com/komgareader/app/MixedSourcesLiveTest.kt
git add app/src/androidTest/kotlin/com/komgareader/app/ci/BlockAMixedSourcesTest.kt
git commit -m "test(ci): Block A gemischte Quellen (A5/A6); löst MixedSourcesLiveTest ab"
```

---

## Task 6: Block B — Werk-Auflösung pro Quelle

**Files:**
- Create: `app/src/androidTest/kotlin/com/komgareader/app/ci/BlockBResolutionTest.kt`

- [ ] **Step 1: Testklasse schreiben (B7, B8)**

```kotlin
package com.komgareader.app.ci

import com.komgareader.domain.source.SourceFilter
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/** Spec §9 Block B — Werk-Auflösung pro Quelle (multi-source, get(sourceId) statt current()). */
@RunWith(AndroidJUnit4::class)
class BlockBResolutionTest {

    private lateinit var stack: CiSourceStack

    @Before fun setUp() { stack = CiSourceStack() }
    @After fun tearDown() { stack.close() }

    /**
     * B7: Bei zwei aktiven Quellen wird das Werk der ZWEITEN Quelle (Webtoon aus B) über
     * get(sourceId) aufgelöst — nicht über „die erste/aktive". Beweist multi-source pro Werk.
     */
    @Test fun b7_werk_der_zweiten_quelle_ueber_get_aufgeloest() = runTest {
        stack.register(CiKomga.A, CiKomga.B)
        val all = stack.activeSource.all()

        // Die Webtoon-Serie gehört zu B. Ihre sourceId aus der Aggregation ziehen …
        val webtoon = all
            .flatMap { it.browse(0, SourceFilter()).items }
            .first { it.title == CiFixtures.WEBTOON_SERIES }

        // … und exakt diese Quelle über get(sourceId) auflösen.
        val resolved = stack.activeSource.get(webtoon.sourceId)
        assertTrue("get(sourceId) muss die Quelle des Werks liefern", resolved != null)
        // Die aufgelöste Quelle muss die Bücher der Webtoon-Serie liefern können.
        val books = resolved!!.books(webtoon.remoteId)
        assertTrue("Webtoon-Serie muss mind. ein Buch haben", books.isNotEmpty())
        assertEquals("Buch trägt die sourceId von B", webtoon.sourceId, books.first().sourceId)
    }

    /**
     * B8: Seiten- und Cover-Bytes fließen durch die Naht (openPage/coverBytes) — kein direkter
     * URL/Auth-Pfad. Manga-Serie aus A: erste Seite des ersten Buchs muss > 1 KiB liefern.
     */
    @Test fun b8_seiten_und_cover_durch_die_naht() = runTest {
        stack.register(CiKomga.A)
        val source = stack.activeSource.all().first()
        val manga = source.browse(0, SourceFilter()).items.first { it.title == CiFixtures.MANGA_SERIES }

        val books = source.books(manga.remoteId)
        assertTrue("Manga muss Bücher haben", books.isNotEmpty())
        val pages = source.pages(books.first().remoteId)
        assertTrue("Buch muss Seiten haben", pages.isNotEmpty())

        val pageBytes = source.openPage(pages.first())
        assertTrue("Seiten-Bytes > 1 KiB (durch openPage, nicht direkt-URL)", pageBytes.size > 1024)

        val cover = source.coverBytes(manga.remoteId, isSeriesCover = true)
        assertTrue("Cover-Bytes > 1 KiB (durch coverBytes)", cover.size > 1024)
    }
}
```

- [ ] **Step 2: Ausführen**

Run: `ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.komgareader.app.ci.BlockBResolutionTest`
Expected: 2 Tests grün.

- [ ] **Step 3: Commit**

```bash
git add app/src/androidTest/kotlin/com/komgareader/app/ci/BlockBResolutionTest.kt
git commit -m "test(ci): Block B — Werk-Auflösung pro Quelle (B7/B8)"
```

---

## Task 7: Gesamtlauf Block A+B

- [ ] **Step 1: Beide Blöcke zusammen ausführen**

Run: `ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=com.komgareader.app.ci`
Expected: 9 Tests grün (A: 5, A-mixed: 2, B: 2). Test-Report unter
`app/build/reports/androidTests/connected/` sichern.

- [ ] **Step 2: Falls Tests rote Kopplungs-Findings zeigen**

Jeder Fehlschlag, der auf quellen-spezifische Kopplung zurückgeht (z.B. `current()` statt
`get()`), ist ein echtes Finding gegen `source-agnostic-integration.md` — als BLOCKED melden,
nicht den Test aufweichen.

- [ ] **Step 3: Commit (nur falls Anpassungen nötig waren)**

```bash
git add -A && git commit -m "test(ci): Block A+B Gesamtlauf grün gegen CI-Topologie"
```

---

## Self-Review (Plan-Autor)

- **Spec-Coverage:** §6 Harness (Task 1–3: CiKomga/CiFixtures/CiSourceStack), §9 Block A (Task 4: A1/A2/A3a/A3b/A4; Task 5: A5/A6), Block B (Task 6: B7/B8). C/D/E/F/G + UI bewusst in Folgeplänen.
- **Auth-Entscheidung verifiziert:** `KomgaSource.kt:183-184` nutzt Basic Auth bei leerem apiKey → statische Creds, kein `.keys.env`-Plumbing.
- **DRY:** ein `CiSourceStack` für alle Tests (kein wiederholtes inMemory/Keystore/Registration-Setup); spiegelt das bewährte `MixedSourcesLiveTest`-Muster, das danach gelöscht wird.
- **Offene Annahmen, im Plan markiert:** `RoomServerRepository.delete(rowId)`-Signatur (Task 3) und A4-Rowid-Annahme (Task 4) — Implementer verifiziert gegen echten Code und passt bei Abweichung an.

## Nächste Pläne

- **Plan 3** Seam-Tests Block C (Reader-Dispatch/Viewer), D (Sammlungen + Push/Pull-Sync), E (Fortschritt-Sync), F (Download), G (Geräteklasse/E-Ink).
- **Plan 4** Compose-UI-Test-Deps + UI-Set (A1/A4, B7, C9–11, D14, G22).
- **Plan 5** Plugin/modulare-UI-Tests (Block H, `[pending]`).
