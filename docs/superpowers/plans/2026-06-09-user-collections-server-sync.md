# User-Collections mit server-agnostischem Teil-Sync — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Der Nutzer legt benannte Collections an (ganze Serien oder einzelne Bücher), sammelt handverlesene Werke und synct sie lokal-first / best-effort server-agnostisch zum Server (Komga `/collections` bzw. `/readlists`), quellen-übergreifend per Fan-out + Merge.

**Architecture:** Neue *optionale* Naht-A-Capability `CollectionSyncSource` (additiv wie `ContainerSource`). Eine App-Collection ist die kanonische Vereinigung über N Quellen; eine `CollectionSyncManager`-Engine (imperative Shell in `app`) gruppiert Mitglieder nach `sourceId`, pusht pro sync-fähiger Quelle das Subset (Replace-Semantik), merged beim Pull. Offline-first über Room mit `dirty`/Status pro Quelle — gleiches Muster wie der bestehende Lesefortschritt-Sync. Pure Merge-/Gruppierungs-Funktionen im `domain` (TDD).

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Room, Retrofit/OkHttp + kotlinx.serialization, JUnit + MockWebServer + Turbine (vorhandene Test-Infra).

**Spec:** `docs/superpowers/specs/2026-06-09-user-collections-server-sync-design.md`

**Konventionen (Pflicht):**
- TDD: pure Funktionen/Mapper zuerst rot, dann grün. Domain/Mapper ohne I/O.
- Quellen-agnostik: kein `KomgaSource`/`*Provider`-Typ in ViewModel/UI; Auflösung über `ActiveSource`.
- E-Ink-Designsprache: `EinkModal`, `ChoiceRow`, `SettingsGroup`, `EinkInfoDialog`, `AppIcons.*`, Hairline-Token; Animation über `LocalDisplayBehavior`/`LocalEinkMode` gegatet.
- i18n: jeder sichtbare Text als `Strings`-Key in DE **und** EN, echte Umlaute/ß.
- Häufig committen (pro Task mindestens einmal).
- Migration: **kein** destruktives `ALTER … DEFAULT`; neue Tabellen via `CREATE TABLE`. Upgrade-Test auf echter DB (kein inMemory-Falsch-Grün) — siehe `memory/room-migration-destructive-pitfall.md`.

---

## File Structure

**Neu:**
- `domain/.../model/UserCollection.kt` — `CollectionKind`, `UserCollection`, `CollectionMember`, `SyncStatus`.
- `domain/.../source/CollectionSyncSource.kt` — Naht-A-Capability + `RemoteCollection`.
- `domain/.../repository/CollectionRepository.kt` — Repo-Interface.
- `domain/.../usecase/CollectionMerge.kt` — pure `groupBySource`/`mergeSubsets`/`deriveStatus`.
- `data/.../db/CollectionEntities.kt` — 3 Entities.
- `data/.../db/CollectionDao.kt` — DAO.
- `data/.../repository/RoomCollectionRepository.kt` — Impl.
- `source-komga/.../dto/CollectionDtos.kt` — DTOs (collection/readlist/me).
- `app/.../data/CollectionSyncManager.kt` — Fan-out/Merge-Engine.
- `app/.../ui/collections/CollectionsViewModel.kt`, `CollectionsScreen.kt`, `CollectionDetailScreen.kt`, `AddToCollectionSheet.kt`.

**Geändert:**
- `source-api/.../source/MediaSource.kt` *(nein — eigene Datei `CollectionSyncSource.kt` im selben Package `com.komgareader.domain.source`)*. Hinweis: das Package liegt physisch in `source-api` **und** `domain`? → es liegt in **`domain`** (Modell/Interface) bzw. **`source-api`** je nach Ist. **Prüfen:** `MediaSource.kt` liegt in `source-api/src/main/kotlin/com/komgareader/domain/source/` → neue Interface-Datei dort daneben.
- `source-komga/.../KomgaApi.kt` — collections/readlists/me-Endpunkte.
- `source-komga/.../KomgaSource.kt` — implementiert `CollectionSyncSource`.
- `source-komga/.../KomgaMapper.kt` — `toRemoteCollection`.
- `data/.../db/AppDatabase.kt` — Entities + Version 13→14 + Migration.
- `data/.../di/DataModule.kt` — `CollectionRepository`-Binding.
- `app/.../data/ActiveSource.kt` — `collectionSource(sourceId)`-Grenze.
- `app/.../i18n/Strings.kt` — neue Keys.
- App-Navigation (Groups-Region) — Einstieg „Collections".

---

## Phase A — Domain-Fundament (pure, kein Server)

### Task 1: Domänen-Modell

**Files:**
- Create: `domain/src/main/kotlin/com/komgareader/domain/model/UserCollection.kt`

- [ ] **Step 1: Modell anlegen**

```kotlin
package com.komgareader.domain.model

/** Granularität einer Collection. SERIES → Komga /collections, BOOK → Komga /readlists. */
enum class CollectionKind { SERIES, BOOK }

/** Sync-Status einer Collection bezogen auf EINE Quelle. */
enum class SyncStatus { SYNCED, DIRTY, LOCAL_ONLY, UNSUPPORTED, FORBIDDEN }

/** Ein Mitglied: ein Werk (Serie oder Buch) einer bestimmten Quelle. */
data class CollectionMember(
    val sourceId: Long,
    val remoteId: String,
    val title: String,
)

/**
 * Kanonische, quellen-übergreifende Collection. Identität über Quellen hinweg = [name].
 * [members] ist geordnet; die App hält die kanonische Reihenfolge.
 */
data class UserCollection(
    val id: Long,
    val name: String,
    val kind: CollectionKind,
    val members: List<CollectionMember>,
)
```

- [ ] **Step 2: Build**

Run: `./gradlew :domain:compileKotlin -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add domain/src/main/kotlin/com/komgareader/domain/model/UserCollection.kt
git commit -m "feat(domain): UserCollection-Modell (kind, members, SyncStatus)"
```

---

### Task 2: Naht-A-Capability `CollectionSyncSource`

**Files:**
- Create: `source-api/src/main/kotlin/com/komgareader/domain/source/CollectionSyncSource.kt`

> Lege die Datei im selben Verzeichnis wie `MediaSource.kt` ab (`find . -name MediaSource.kt -path '*/main/*'` bestätigt den Pfad).

- [ ] **Step 1: Interface anlegen**

```kotlin
package com.komgareader.domain.source

import com.komgareader.domain.model.CollectionKind

/** Eine vom Server gehaltene Collection/Read-List (innerhalb EINER Quelle). */
data class RemoteCollection(
    val remoteId: String,
    val name: String,
    val memberRemoteIds: List<String>,
)

/**
 * Optionale Capability (Naht A): Quelle kann Nutzer-Collections server-seitig schreiben.
 * Quellen ohne Schreibpfad (OPDS, Stub) implementieren das **nicht** → die UI hält deren
 * Mitglieder rein lokal. [kind] wählt im Impl die Endpunkt-Familie (Komga: collections vs
 * readlists). Mitgliedschaft wird per Voll-Liste gesetzt (Replace-Semantik).
 */
interface CollectionSyncSource : MediaSource {
    /** Darf diese Sitzung wirklich schreiben? (Komga: Rolle ADMIN). Bei false nur lesen/lokal. */
    suspend fun canWriteCollections(): Boolean
    suspend fun listCollections(kind: CollectionKind): List<RemoteCollection>
    suspend fun createCollection(kind: CollectionKind, name: String, memberRemoteIds: List<String>): RemoteCollection
    suspend fun updateCollection(kind: CollectionKind, remoteId: String, name: String, memberRemoteIds: List<String>)
    suspend fun deleteCollection(kind: CollectionKind, remoteId: String)
}
```

- [ ] **Step 2: Build**

Run: `./gradlew :source-api:compileKotlin -q`
Expected: BUILD SUCCESSFUL (source-api hängt an domain — `CollectionKind` ist sichtbar)

- [ ] **Step 3: Commit**

```bash
git add source-api/src/main/kotlin/com/komgareader/domain/source/CollectionSyncSource.kt
git commit -m "feat(source-api): CollectionSyncSource-Capability (Naht A, additiv)"
```

---

### Task 3: Pure Merge-/Status-Funktionen (TDD)

**Files:**
- Create: `domain/src/main/kotlin/com/komgareader/domain/usecase/CollectionMerge.kt`
- Test: `domain/src/test/kotlin/com/komgareader/domain/usecase/CollectionMergeTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package com.komgareader.domain.usecase

import com.komgareader.domain.model.CollectionMember
import com.komgareader.domain.model.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class CollectionMergeTest {

    private fun m(source: Long, remote: String) = CollectionMember(source, remote, "T-$remote")

    @Test
    fun `groupBySource splits members by sourceId preserving order`() {
        val members = listOf(m(1, "a"), m(2, "b"), m(1, "c"))
        val grouped = groupBySource(members)
        assertEquals(listOf("a", "c"), grouped.getValue(1).map { it.remoteId })
        assertEquals(listOf("b"), grouped.getValue(2).map { it.remoteId })
    }

    @Test
    fun `mergeSubsets keeps canonical order, appends new remote members, drops removed`() {
        val canonical = listOf(m(1, "a"), m(2, "b"), m(1, "c"))
        // Quelle 1 meldet jetzt a, c (unverändert) + d (neu); Quelle 2 meldet leer (b entfernt).
        val perSource = mapOf(
            1L to listOf("a", "c", "d"),
            2L to emptyList(),
        )
        val merged = mergeSubsets(canonical, perSource, titleFor = { _, r -> "srv-$r" })
        // a, c bleiben in kanonischer Reihenfolge; b weg (Quelle 2 leer); d hinten angehängt.
        assertEquals(listOf("a" to 1L, "c" to 1L, "d" to 1L), merged.map { it.remoteId to it.sourceId })
        assertEquals("srv-d", merged.first { it.remoteId == "d" }.title)
    }

    @Test
    fun `mergeSubsets leaves members of non-syncable sources untouched`() {
        val canonical = listOf(m(9, "opds1"), m(1, "a"))
        // Nur Quelle 1 ist sync-fähig (im Map). Quelle 9 fehlt → ihre Mitglieder bleiben.
        val merged = mergeSubsets(canonical, mapOf(1L to listOf("a")), titleFor = { _, r -> r })
        assertEquals(listOf("opds1" to 9L, "a" to 1L), merged.map { it.remoteId to it.sourceId })
    }

    @Test
    fun `deriveStatus maps capability and dirty flag`() {
        assertEquals(SyncStatus.UNSUPPORTED, deriveStatus(syncable = false, canWrite = false, dirty = true))
        assertEquals(SyncStatus.FORBIDDEN, deriveStatus(syncable = true, canWrite = false, dirty = true))
        assertEquals(SyncStatus.DIRTY, deriveStatus(syncable = true, canWrite = true, dirty = true))
        assertEquals(SyncStatus.SYNCED, deriveStatus(syncable = true, canWrite = true, dirty = false))
    }
}
```

- [ ] **Step 2: Run — verify fails**

Run: `./gradlew :domain:test --tests '*CollectionMergeTest*' -q`
Expected: FAIL (unresolved `groupBySource`/`mergeSubsets`/`deriveStatus`)

- [ ] **Step 3: Implement**

```kotlin
package com.komgareader.domain.usecase

import com.komgareader.domain.model.CollectionMember
import com.komgareader.domain.model.SyncStatus

/** Mitglieder nach Quelle gruppieren, Reihenfolge je Quelle erhalten. */
fun groupBySource(members: List<CollectionMember>): Map<Long, List<CollectionMember>> =
    members.groupBy { it.sourceId }

/**
 * Merge: kanonische App-Liste mit den von sync-fähigen Quellen gemeldeten Subsets abgleichen.
 * - Mitglieder einer Quelle, die im Map vorkommt, werden auf deren gemeldete remoteIds gefiltert
 *   (entfernte verschwinden), in kanonischer Reihenfolge.
 * - Neue remoteIds einer Quelle (im Map, aber nicht kanonisch) werden hinten angehängt.
 * - Mitglieder von Quellen, die NICHT im Map stehen (nicht sync-fähig), bleiben unangetastet.
 */
fun mergeSubsets(
    canonical: List<CollectionMember>,
    perSourceRemoteIds: Map<Long, List<String>>,
    titleFor: (sourceId: Long, remoteId: String) -> String,
): List<CollectionMember> {
    val result = mutableListOf<CollectionMember>()
    val seen = mutableSetOf<Pair<Long, String>>()
    for (member in canonical) {
        val reported = perSourceRemoteIds[member.sourceId]
        if (reported == null) {
            // Quelle nicht sync-fähig → Mitglied bleibt.
            result += member
            seen += member.sourceId to member.remoteId
        } else if (member.remoteId in reported) {
            result += member
            seen += member.sourceId to member.remoteId
        }
        // sonst: am Server entfernt → fallenlassen.
    }
    // Neue Server-Mitglieder hinten anhängen.
    for ((sourceId, remoteIds) in perSourceRemoteIds) {
        for (remoteId in remoteIds) {
            if ((sourceId to remoteId) !in seen) {
                result += CollectionMember(sourceId, remoteId, titleFor(sourceId, remoteId))
                seen += sourceId to remoteId
            }
        }
    }
    return result
}

/** Status pro Quelle aus Capability + dirty ableiten. */
fun deriveStatus(syncable: Boolean, canWrite: Boolean, dirty: Boolean): SyncStatus = when {
    !syncable -> SyncStatus.UNSUPPORTED
    !canWrite -> SyncStatus.FORBIDDEN
    dirty -> SyncStatus.DIRTY
    else -> SyncStatus.SYNCED
}
```

- [ ] **Step 4: Run — verify passes**

Run: `./gradlew :domain:test --tests '*CollectionMergeTest*' -q`
Expected: PASS (4 Tests)

- [ ] **Step 5: Commit**

```bash
git add domain/src/main/kotlin/com/komgareader/domain/usecase/CollectionMerge.kt domain/src/test/kotlin/com/komgareader/domain/usecase/CollectionMergeTest.kt
git commit -m "feat(domain): pure groupBySource/mergeSubsets/deriveStatus + Tests"
```

---

### Task 4: Repository-Interface

**Files:**
- Create: `domain/src/main/kotlin/com/komgareader/domain/repository/CollectionRepository.kt`

- [ ] **Step 1: Interface**

```kotlin
package com.komgareader.domain.repository

import com.komgareader.domain.model.CollectionKind
import com.komgareader.domain.model.CollectionMember
import com.komgareader.domain.model.SyncStatus
import com.komgareader.domain.model.UserCollection
import kotlinx.coroutines.flow.Flow

/** Pro-Quelle-Sync-Verknüpfung einer Collection (App-seitige Wahrheit über den Sync-Stand). */
data class CollectionSyncLink(
    val collectionId: Long,
    val sourceId: Long,
    val remoteCollectionId: String?,
    val status: SyncStatus,
    val dirty: Boolean,
)

interface CollectionRepository {
    val collections: Flow<List<UserCollection>>
    fun syncLinks(collectionId: Long): Flow<List<CollectionSyncLink>>

    suspend fun create(name: String, kind: CollectionKind): Long
    suspend fun rename(collectionId: Long, name: String)
    suspend fun delete(collectionId: Long)

    /** Setzt die geordnete Mitgliederliste (kanonisch) + markiert betroffene Quellen dirty. */
    suspend fun setMembers(collectionId: Long, members: List<CollectionMember>)
    suspend fun addMember(collectionId: Long, member: CollectionMember)
    suspend fun removeMember(collectionId: Long, sourceId: Long, remoteId: String)

    /** Sync-Engine schreibt Ergebnis zurück. */
    suspend fun updateSyncLink(link: CollectionSyncLink)
    suspend fun get(collectionId: Long): UserCollection?
}
```

- [ ] **Step 2: Build**

Run: `./gradlew :domain:compileKotlin -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add domain/src/main/kotlin/com/komgareader/domain/repository/CollectionRepository.kt
git commit -m "feat(domain): CollectionRepository-Interface + CollectionSyncLink"
```

---

## Phase B — Persistenz (Room)

### Task 5: Room-Entities + DAO

**Files:**
- Create: `data/src/main/kotlin/com/komgareader/data/db/CollectionEntities.kt`
- Create: `data/src/main/kotlin/com/komgareader/data/db/CollectionDao.kt`

- [ ] **Step 1: Entities**

```kotlin
package com.komgareader.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: String,                       // CollectionKind.name
)

@Entity(
    tableName = "collection_members",
    indices = [Index("collectionId")],
)
data class CollectionMemberEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val collectionId: Long,
    val sourceId: Long,
    val remoteId: String,
    val title: String,
    val position: Int,
)

@Entity(
    tableName = "collection_sync_links",
    primaryKeys = ["collectionId", "sourceId"],
)
data class CollectionSyncLinkEntity(
    val collectionId: Long,
    val sourceId: Long,
    val remoteCollectionId: String?,
    val status: String,                     // SyncStatus.name
    val dirty: Boolean,
    val updatedAt: Long,
)
```

- [ ] **Step 2: DAO**

```kotlin
package com.komgareader.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionDao {
    @Query("SELECT * FROM collections ORDER BY id ASC")
    fun observeCollections(): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collection_members ORDER BY collectionId ASC, position ASC")
    fun observeMembers(): Flow<List<CollectionMemberEntity>>

    @Query("SELECT * FROM collection_sync_links WHERE collectionId = :collectionId")
    fun observeLinks(collectionId: Long): Flow<List<CollectionSyncLinkEntity>>

    @Query("SELECT * FROM collections WHERE id = :id")
    suspend fun getCollection(id: Long): CollectionEntity?

    @Query("SELECT * FROM collection_members WHERE collectionId = :id ORDER BY position ASC")
    suspend fun getMembers(id: Long): List<CollectionMemberEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollection(entity: CollectionEntity): Long

    @Query("UPDATE collections SET name = :name WHERE id = :id")
    suspend fun renameCollection(id: Long, name: String)

    @Query("DELETE FROM collections WHERE id = :id")
    suspend fun deleteCollection(id: Long)

    @Query("DELETE FROM collection_members WHERE collectionId = :id")
    suspend fun clearMembers(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMembers(members: List<CollectionMemberEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLink(link: CollectionSyncLinkEntity)

    @Query("DELETE FROM collection_sync_links WHERE collectionId = :id")
    suspend fun clearLinks(id: Long)

    /** Mitglieder atomar ersetzen (kanonische Reihenfolge via position). */
    @Transaction
    suspend fun replaceMembers(collectionId: Long, members: List<CollectionMemberEntity>) {
        clearMembers(collectionId)
        insertMembers(members)
    }
}
```

- [ ] **Step 3: Build**

Run: `./gradlew :data:compileKotlin -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add data/src/main/kotlin/com/komgareader/data/db/CollectionEntities.kt data/src/main/kotlin/com/komgareader/data/db/CollectionDao.kt
git commit -m "feat(data): Room-Entities + DAO für Collections"
```

---

### Task 6: AppDatabase registrieren + Migration 13→14 (mit Upgrade-Test)

**Files:**
- Modify: `data/src/main/kotlin/com/komgareader/data/db/AppDatabase.kt`
- Test: `data/src/androidTest/kotlin/com/komgareader/data/db/CollectionMigrationTest.kt` *(falls androidTest fehlt: `data/src/test/...` mit Robolectric-Migration-Helper analog vorhandener Migrationstests — prüfe `find data -name '*MigrationTest*'` und folge dem dort etablierten Muster)*

- [ ] **Step 1: Migration + Registrierung**

In `AppDatabase.kt`: `entities`-Liste um die 3 neuen Entities ergänzen, `version = 13` → `version = 14`, `abstract fun collectionDao(): CollectionDao` ergänzen, und am Migrations-Block (wo `MIGRATION_4_5` etc. stehen) anhängen:

```kotlin
/** v13 → v14: Collections (Nutzer-kuratierte Werk-Listen) + Mitglieder + Sync-Links. */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `collections` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `kind` TEXT NOT NULL
            )""",
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `collection_members` (
                `rowId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `collectionId` INTEGER NOT NULL,
                `sourceId` INTEGER NOT NULL,
                `remoteId` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `position` INTEGER NOT NULL
            )""",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_collection_members_collectionId` ON `collection_members` (`collectionId`)",
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `collection_sync_links` (
                `collectionId` INTEGER NOT NULL,
                `sourceId` INTEGER NOT NULL,
                `remoteCollectionId` TEXT,
                `status` TEXT NOT NULL,
                `dirty` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`collectionId`, `sourceId`)
            )""",
        )
    }
}
```

> Danach die `MIGRATION_13_14` dort registrieren, wo die DB gebaut wird (`Room.databaseBuilder(...).addMigrations(...)` in `DataModule.kt` — die bestehenden `MIGRATION_*` als Vorlage; **niemals** `fallbackToDestructiveMigration`).

- [ ] **Step 2: Migration-Test (echte DB, kein inMemory)**

Folge dem im Repo etablierten Migration-Test-Muster (`find data -name '*Migration*Test*'`). Minimal: alte DB auf v13 öffnen, `MIGRATION_13_14` anwenden, prüfen dass `collections`/`collection_members`/`collection_sync_links` existieren und ein Insert in `collections` durchläuft.

```kotlin
// Skizze (an vorhandenes MigrationTestHelper-Muster anpassen):
@Test
fun migrate13To14_createsCollectionTables() {
    helper.createDatabase(TEST_DB, 13).close()
    val db = helper.runMigrationsAndValidate(TEST_DB, 14, true, MIGRATION_13_14)
    db.execSQL("INSERT INTO collections (name, kind) VALUES ('X', 'SERIES')")
    val c = db.query("SELECT COUNT(*) FROM collections")
    c.moveToFirst(); assertEquals(1, c.getInt(0))
}
```

- [ ] **Step 3: Run**

Run: `./gradlew :data:test --tests '*CollectionMigration*' -q` (bzw. `:data:connectedAndroidTest` wenn androidTest)
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add data/src/main/kotlin/com/komgareader/data/db/AppDatabase.kt data/src/main/kotlin/com/komgareader/data/di/DataModule.kt data/src/*/kotlin/com/komgareader/data/db/CollectionMigrationTest.kt
git commit -m "feat(data): DB v14 + Migration 13->14 für Collections (Upgrade-Test)"
```

---

### Task 7: RoomCollectionRepository + DI

**Files:**
- Create: `data/src/main/kotlin/com/komgareader/data/repository/RoomCollectionRepository.kt`
- Modify: `data/src/main/kotlin/com/komgareader/data/di/DataModule.kt`
- Test: `data/src/test/kotlin/com/komgareader/data/repository/RoomCollectionRepositoryTest.kt`

- [ ] **Step 1: Failing test (in-memory DB für Repo-Verhalten — NICHT für Migration)**

```kotlin
package com.komgareader.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.komgareader.data.db.AppDatabase
import com.komgareader.domain.model.CollectionKind
import com.komgareader.domain.model.CollectionMember
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomCollectionRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: RoomCollectionRepository

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = RoomCollectionRepository(db.collectionDao())
    }
    @After fun tear() = db.close()

    @Test fun `create then setMembers yields ordered canonical collection`() = runBlocking {
        val id = repo.create("Lese gerade", CollectionKind.SERIES)
        repo.setMembers(id, listOf(CollectionMember(1, "a", "A"), CollectionMember(2, "b", "B")))
        val c = repo.collections.first().single()
        assertEquals("Lese gerade", c.name)
        assertEquals(listOf("a", "b"), c.members.map { it.remoteId })
        // Beide Quellen wurden dirty markiert.
        val links = repo.syncLinks(id).first().associateBy { it.sourceId }
        assertEquals(true, links.getValue(1).dirty)
        assertEquals(true, links.getValue(2).dirty)
    }
}
```

- [ ] **Step 2: Run — fails**

Run: `./gradlew :data:test --tests '*RoomCollectionRepositoryTest*' -q`
Expected: FAIL (RoomCollectionRepository fehlt)

- [ ] **Step 3: Implement**

```kotlin
package com.komgareader.data.repository

import com.komgareader.data.db.CollectionDao
import com.komgareader.data.db.CollectionEntity
import com.komgareader.data.db.CollectionMemberEntity
import com.komgareader.data.db.CollectionSyncLinkEntity
import com.komgareader.domain.model.CollectionKind
import com.komgareader.domain.model.CollectionMember
import com.komgareader.domain.model.SyncStatus
import com.komgareader.domain.model.UserCollection
import com.komgareader.domain.repository.CollectionRepository
import com.komgareader.domain.repository.CollectionSyncLink
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class RoomCollectionRepository(private val dao: CollectionDao) : CollectionRepository {

    override val collections: Flow<List<UserCollection>> =
        combine(dao.observeCollections(), dao.observeMembers()) { cols, members ->
            val byCol = members.groupBy { it.collectionId }
            cols.map { c ->
                UserCollection(
                    id = c.id,
                    name = c.name,
                    kind = CollectionKind.valueOf(c.kind),
                    members = byCol[c.id].orEmpty().sortedBy { it.position }
                        .map { CollectionMember(it.sourceId, it.remoteId, it.title) },
                )
            }
        }

    override fun syncLinks(collectionId: Long): Flow<List<CollectionSyncLink>> =
        dao.observeLinks(collectionId).map { rows -> rows.map(::toLink) }

    override suspend fun create(name: String, kind: CollectionKind): Long =
        dao.insertCollection(CollectionEntity(name = name, kind = kind.name))

    override suspend fun rename(collectionId: Long, name: String) = dao.renameCollection(collectionId, name)

    override suspend fun delete(collectionId: Long) = dao.deleteCollection(collectionId)

    override suspend fun setMembers(collectionId: Long, members: List<CollectionMember>) {
        dao.replaceMembers(
            collectionId,
            members.mapIndexed { i, m ->
                CollectionMemberEntity(
                    collectionId = collectionId, sourceId = m.sourceId,
                    remoteId = m.remoteId, title = m.title, position = i,
                )
            },
        )
        // Alle betroffenen Quellen dirty markieren (Status DIRTY bis die Engine sie auflöst).
        members.map { it.sourceId }.toSet().forEach { sourceId ->
            dao.upsertLink(
                CollectionSyncLinkEntity(
                    collectionId = collectionId, sourceId = sourceId,
                    remoteCollectionId = existingRemoteId(collectionId, sourceId),
                    status = SyncStatus.DIRTY.name, dirty = true, updatedAt = nowMillis(),
                ),
            )
        }
    }

    override suspend fun addMember(collectionId: Long, member: CollectionMember) {
        val current = currentMembers(collectionId)
        if (current.any { it.sourceId == member.sourceId && it.remoteId == member.remoteId }) return
        setMembers(collectionId, current + member)
    }

    override suspend fun removeMember(collectionId: Long, sourceId: Long, remoteId: String) {
        val current = currentMembers(collectionId)
        setMembers(collectionId, current.filterNot { it.sourceId == sourceId && it.remoteId == remoteId })
    }

    override suspend fun updateSyncLink(link: CollectionSyncLink) {
        dao.upsertLink(
            CollectionSyncLinkEntity(
                collectionId = link.collectionId, sourceId = link.sourceId,
                remoteCollectionId = link.remoteCollectionId,
                status = link.status.name, dirty = link.dirty, updatedAt = nowMillis(),
            ),
        )
    }

    override suspend fun get(collectionId: Long): UserCollection? {
        val c = dao.getCollection(collectionId) ?: return null
        return UserCollection(
            id = c.id, name = c.name, kind = CollectionKind.valueOf(c.kind),
            members = dao.getMembers(collectionId).map { CollectionMember(it.sourceId, it.remoteId, it.title) },
        )
    }

    private suspend fun currentMembers(collectionId: Long): List<CollectionMember> =
        dao.getMembers(collectionId).map { CollectionMember(it.sourceId, it.remoteId, it.title) }

    private suspend fun existingRemoteId(collectionId: Long, sourceId: Long): String? = null // Engine setzt den.

    private fun toLink(e: CollectionSyncLinkEntity) = CollectionSyncLink(
        collectionId = e.collectionId, sourceId = e.sourceId,
        remoteCollectionId = e.remoteCollectionId, status = SyncStatus.valueOf(e.status), dirty = e.dirty,
    )

    private fun nowMillis(): Long = System.currentTimeMillis()
}
```

- [ ] **Step 4: DI-Binding**

In `DataModule.kt` analog zum bestehenden `ShelfRepository`-Provider:

```kotlin
@Provides @Singleton
fun provideCollectionRepository(db: AppDatabase): CollectionRepository =
    RoomCollectionRepository(db.collectionDao())
```

- [ ] **Step 5: Run — passes**

Run: `./gradlew :data:test --tests '*RoomCollectionRepositoryTest*' -q`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add data/src/main/kotlin/com/komgareader/data/repository/RoomCollectionRepository.kt data/src/main/kotlin/com/komgareader/data/di/DataModule.kt data/src/test/kotlin/com/komgareader/data/repository/RoomCollectionRepositoryTest.kt
git commit -m "feat(data): RoomCollectionRepository + DI + Test"
```

---

## Phase C — Komga-Impl (SERIES zuerst, BOOK gleich mitnehmen)

### Task 8: Komga-DTOs + API-Endpunkte

**Files:**
- Create: `source-komga/src/main/kotlin/com/komgareader/source/komga/dto/CollectionDtos.kt`
- Modify: `source-komga/src/main/kotlin/com/komgareader/source/komga/KomgaApi.kt`

- [ ] **Step 1: DTOs**

```kotlin
package com.komgareader.source.komga.dto

import kotlinx.serialization.Serializable

@Serializable
data class CollectionDto(
    val id: String,
    val name: String,
    val ordered: Boolean = false,
    val seriesIds: List<String> = emptyList(),
)

@Serializable
data class CollectionCreationDto(
    val name: String,
    val ordered: Boolean = true,
    val seriesIds: List<String>,
)

@Serializable
data class CollectionUpdateDto(
    val name: String? = null,
    val ordered: Boolean? = null,
    val seriesIds: List<String>? = null,
)

@Serializable
data class ReadListDto(
    val id: String,
    val name: String,
    val summary: String = "",
    val ordered: Boolean = true,
    val bookIds: List<String> = emptyList(),
)

@Serializable
data class ReadListCreationDto(
    val name: String,
    val summary: String = "",
    val ordered: Boolean = true,
    val bookIds: List<String>,
)

@Serializable
data class ReadListUpdateDto(
    val name: String? = null,
    val summary: String? = null,
    val ordered: Boolean? = null,
    val bookIds: List<String>? = null,
)

/** Ausschnitt aus GET /users/me — nur die Rollen für die Schreib-Capability. */
@Serializable
data class KomgaUserDto(val roles: List<String> = emptyList())
```

- [ ] **Step 2: API-Endpunkte ergänzen**

In `KomgaApi.kt` Importe für `Body`/`POST`/`PUT` ergänzen (`retrofit2.http.POST`, `retrofit2.http.PATCH` ist vorhanden) und Methoden hinzufügen:

```kotlin
    @GET("users/me")
    suspend fun getMe(): KomgaUserDto

    @GET("collections")
    suspend fun listCollections(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 500,
    ): KomgaPage<CollectionDto>

    @POST("collections")
    suspend fun createCollection(@Body body: CollectionCreationDto): CollectionDto

    @PATCH("collections/{id}")
    suspend fun updateCollection(@Path("id") id: String, @Body body: CollectionUpdateDto)

    @DELETE("collections/{id}")
    suspend fun deleteCollection(@Path("id") id: String)

    @GET("readlists")
    suspend fun listReadLists(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 500,
    ): KomgaPage<ReadListDto>

    @POST("readlists")
    suspend fun createReadList(@Body body: ReadListCreationDto): ReadListDto

    @PATCH("readlists/{id}")
    suspend fun updateReadList(@Path("id") id: String, @Body body: ReadListUpdateDto)

    @DELETE("readlists/{id}")
    suspend fun deleteReadList(@Path("id") id: String)
```

Importe oben in `KomgaApi.kt` ergänzen:
```kotlin
import com.komgareader.source.komga.dto.CollectionCreationDto
import com.komgareader.source.komga.dto.CollectionDto
import com.komgareader.source.komga.dto.CollectionUpdateDto
import com.komgareader.source.komga.dto.KomgaUserDto
import com.komgareader.source.komga.dto.ReadListCreationDto
import com.komgareader.source.komga.dto.ReadListDto
import com.komgareader.source.komga.dto.ReadListUpdateDto
import retrofit2.http.POST
```

- [ ] **Step 3: Build**

Run: `./gradlew :source-komga:compileKotlin -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add source-komga/src/main/kotlin/com/komgareader/source/komga/dto/CollectionDtos.kt source-komga/src/main/kotlin/com/komgareader/source/komga/KomgaApi.kt
git commit -m "feat(source-komga): collections/readlists/me DTOs + Endpunkte"
```

---

### Task 9: KomgaSource implementiert CollectionSyncSource (Vertrag-Test)

**Files:**
- Modify: `source-komga/src/main/kotlin/com/komgareader/source/komga/KomgaSource.kt`
- Test: `source-komga/src/test/kotlin/com/komgareader/source/komga/KomgaCollectionSyncTest.kt`

- [ ] **Step 1: Failing contract test (MockWebServer-Muster wie vorhandene source-komga-Tests)**

> Prüfe ein vorhandenes `source-komga`-Test, um Retrofit/Json/MockWebServer-Setup exakt zu kopieren (`find source-komga/src/test -name '*.kt'`).

```kotlin
package com.komgareader.source.komga

import com.komgareader.domain.model.CollectionKind
import com.komgareader.domain.source.CollectionSyncSource
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class KomgaCollectionSyncTest {
    private lateinit var server: MockWebServer
    private lateinit var source: CollectionSyncSource

    @Before fun setup() {
        server = MockWebServer(); server.start()
        // Baue KomgaSource gegen server.url("/api/v1/") — nutze das vorhandene
        // KomgaSource-Konstruktionsmuster (KomgaApi via Retrofit + KomgaMapper).
        source = buildKomgaSource(server.url("/api/v1/").toString(), id = 7, name = "K") as CollectionSyncSource
    }
    @After fun tear() = server.shutdown()

    @Test fun `canWriteCollections true when role ADMIN`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"roles":["ADMIN","USER"]}"""))
        assertTrue(source.canWriteCollections())
    }

    @Test fun `createCollection posts ordered seriesIds and returns remote id`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"id":"col1","name":"X","ordered":true,"seriesIds":["a","b"]}"""))
        val remote = source.createCollection(CollectionKind.SERIES, "X", listOf("a", "b"))
        assertEquals("col1", remote.remoteId)
        val req = server.takeRequest()
        assertEquals("POST /api/v1/collections", "${req.method} ${req.path}")
        assertTrue(req.body.readUtf8().contains("\"seriesIds\":[\"a\",\"b\"]"))
    }

    @Test fun `createCollection BOOK kind hits readlists`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"id":"rl1","name":"X","ordered":true,"bookIds":["a"]}"""))
        val remote = source.createCollection(CollectionKind.BOOK, "X", listOf("a"))
        assertEquals("rl1", remote.remoteId)
        assertEquals("POST /api/v1/readlists", server.takeRequest().let { "${it.method} ${it.path}" })
    }
}
```

> `buildKomgaSource(...)` ist ein Test-Helfer, den du nach dem vorhandenen `source-komga`-Konstruktionsmuster schreibst (oder den bestehenden Helfer wiederverwendest, falls einer existiert).

- [ ] **Step 2: Run — fails**

Run: `./gradlew :source-komga:test --tests '*KomgaCollectionSyncTest*' -q`
Expected: FAIL (KomgaSource implementiert CollectionSyncSource noch nicht)

- [ ] **Step 3: Implement — `KomgaSource` erweitern**

`class KomgaSource ... : BrowsableSource, SyncingSource, ContainerSource, CollectionSyncSource {` und Methoden + Import (`com.komgareader.domain.model.CollectionKind`, `com.komgareader.domain.source.CollectionSyncSource`, `com.komgareader.domain.source.RemoteCollection`, DTOs):

```kotlin
    override suspend fun canWriteCollections(): Boolean =
        runCatching { api.getMe().roles.contains("ADMIN") }.getOrDefault(false)

    override suspend fun listCollections(kind: CollectionKind): List<RemoteCollection> = when (kind) {
        CollectionKind.SERIES -> api.listCollections().content.map(mapper::toRemoteCollection)
        CollectionKind.BOOK -> api.listReadLists().content.map(mapper::toRemoteCollection)
    }

    override suspend fun createCollection(kind: CollectionKind, name: String, memberRemoteIds: List<String>): RemoteCollection =
        when (kind) {
            CollectionKind.SERIES ->
                mapper.toRemoteCollection(api.createCollection(CollectionCreationDto(name = name, seriesIds = memberRemoteIds)))
            CollectionKind.BOOK ->
                mapper.toRemoteCollection(api.createReadList(ReadListCreationDto(name = name, bookIds = memberRemoteIds)))
        }

    override suspend fun updateCollection(kind: CollectionKind, remoteId: String, name: String, memberRemoteIds: List<String>) {
        when (kind) {
            CollectionKind.SERIES -> api.updateCollection(remoteId, CollectionUpdateDto(name = name, seriesIds = memberRemoteIds))
            CollectionKind.BOOK -> api.updateReadList(remoteId, ReadListUpdateDto(name = name, bookIds = memberRemoteIds))
        }
    }

    override suspend fun deleteCollection(kind: CollectionKind, remoteId: String) {
        when (kind) {
            CollectionKind.SERIES -> api.deleteCollection(remoteId)
            CollectionKind.BOOK -> api.deleteReadList(remoteId)
        }
    }
```

- [ ] **Step 4: Mapper-Methoden (`KomgaMapper.kt`)**

```kotlin
    fun toRemoteCollection(dto: com.komgareader.source.komga.dto.CollectionDto) =
        com.komgareader.domain.source.RemoteCollection(dto.id, dto.name, dto.seriesIds)

    fun toRemoteCollection(dto: com.komgareader.source.komga.dto.ReadListDto) =
        com.komgareader.domain.source.RemoteCollection(dto.id, dto.name, dto.bookIds)
```

- [ ] **Step 5: Run — passes**

Run: `./gradlew :source-komga:test --tests '*KomgaCollectionSyncTest*' -q`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add source-komga/src/main/kotlin/com/komgareader/source/komga/KomgaSource.kt source-komga/src/main/kotlin/com/komgareader/source/komga/KomgaMapper.kt source-komga/src/test/kotlin/com/komgareader/source/komga/KomgaCollectionSyncTest.kt
git commit -m "feat(source-komga): KomgaSource implementiert CollectionSyncSource (+Vertrag-Test)"
```

---

## Phase D — Sync-Engine (app, imperative Shell)

### Task 10: `ActiveSource.collectionSource(sourceId)`-Grenze

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/data/ActiveSource.kt`

- [ ] **Step 1: Methode ergänzen** (agnostische Grenze — kein Komga-Typ nach außen)

```kotlin
import com.komgareader.domain.source.CollectionSyncSource
// ...
    /** Quelle als Schreib-Capability, oder null wenn sie keine Collections schreiben kann. */
    open suspend fun collectionSource(sourceId: Long): CollectionSyncSource? {
        syncAll()
        return sources.get(sourceId) as? CollectionSyncSource
    }
```

- [ ] **Step 2: Build**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/data/ActiveSource.kt
git commit -m "feat(app): ActiveSource.collectionSource — agnostische Schreib-Grenze"
```

---

### Task 11: `CollectionSyncManager` (Fan-out + Merge + Namens-Adoption) — TDD mit Fake-Quelle

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/data/CollectionSyncManager.kt`
- Test: `app/src/test/kotlin/com/komgareader/app/data/CollectionSyncManagerTest.kt`

- [ ] **Step 1: Failing test (Fake `ActiveSource` + Fake `CollectionRepository`)**

```kotlin
package com.komgareader.app.data

import com.komgareader.domain.model.CollectionKind
import com.komgareader.domain.model.CollectionMember
import com.komgareader.domain.model.SyncStatus
import com.komgareader.domain.model.UserCollection
import com.komgareader.domain.source.CollectionSyncSource
import com.komgareader.domain.source.RemoteCollection
import com.komgareader.domain.source.SourceKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class CollectionSyncManagerTest {

    private class FakeSource(
        override val id: Long, private val canWrite: Boolean,
        val existing: MutableList<RemoteCollection> = mutableListOf(),
    ) : CollectionSyncSource {
        override val name = "fake$id"; override val kind = SourceKind.KOMGA
        var lastCreate: Pair<String, List<String>>? = null
        var lastUpdate: Triple<String, String, List<String>>? = null
        override suspend fun canWriteCollections() = canWrite
        override suspend fun listCollections(kind: CollectionKind) = existing.toList()
        override suspend fun createCollection(kind: CollectionKind, name: String, memberRemoteIds: List<String>): RemoteCollection {
            lastCreate = name to memberRemoteIds
            val rc = RemoteCollection("remote-$name-$id", name, memberRemoteIds); existing += rc; return rc
        }
        override suspend fun updateCollection(kind: CollectionKind, remoteId: String, name: String, memberRemoteIds: List<String>) {
            lastUpdate = Triple(remoteId, name, memberRemoteIds)
        }
        override suspend fun deleteCollection(kind: CollectionKind, remoteId: String) { existing.removeAll { it.remoteId == remoteId } }
    }

    @Test fun `push creates per-source collection with that source subset`() = runBlocking {
        val s1 = FakeSource(1, canWrite = true)
        val s2 = FakeSource(2, canWrite = true)
        val repo = FakeCollectionRepo()
        val mgr = CollectionSyncManager(repo, resolver = { id -> mapOf(1L to s1, 2L to s2)[id] })
        val col = UserCollection(
            id = 10, name = "Mix", kind = CollectionKind.SERIES,
            members = listOf(CollectionMember(1, "a", "A"), CollectionMember(2, "b", "B"), CollectionMember(1, "c", "C")),
        )
        mgr.push(col)
        assertEquals("a,c" to true, (s1.lastCreate!!.second.joinToString(",")) to (s1.lastCreate!!.first == "Mix"))
        assertEquals(listOf("b"), s2.lastCreate!!.second)
        assertEquals(SyncStatus.SYNCED, repo.links.getValue(1).status)
        assertEquals(SyncStatus.SYNCED, repo.links.getValue(2).status)
    }

    @Test fun `push adopts existing remote collection by name instead of creating`() = runBlocking {
        val s1 = FakeSource(1, canWrite = true, existing = mutableListOf(RemoteCollection("pre1", "Mix", listOf("x"))))
        val repo = FakeCollectionRepo()
        val mgr = CollectionSyncManager(repo, resolver = { s1 })
        val col = UserCollection(10, "Mix", CollectionKind.SERIES, listOf(CollectionMember(1, "a", "A")))
        mgr.push(col)
        // adoptiert pre1 → update statt create
        assertEquals(null, s1.lastCreate)
        assertEquals("pre1", s1.lastUpdate!!.first)
        assertEquals(listOf("a"), s1.lastUpdate!!.third)
    }

    @Test fun `push marks non-writable source FORBIDDEN, keeps local`() = runBlocking {
        val s1 = FakeSource(1, canWrite = false)
        val repo = FakeCollectionRepo()
        val mgr = CollectionSyncManager(repo, resolver = { s1 })
        mgr.push(UserCollection(10, "X", CollectionKind.SERIES, listOf(CollectionMember(1, "a", "A"))))
        assertEquals(SyncStatus.FORBIDDEN, repo.links.getValue(1).status)
        assertEquals(null, s1.lastCreate)
    }

    @Test fun `push marks unsupported source (no CollectionSyncSource) UNSUPPORTED`() = runBlocking {
        val repo = FakeCollectionRepo()
        val mgr = CollectionSyncManager(repo, resolver = { null })  // z.B. OPDS
        mgr.push(UserCollection(10, "X", CollectionKind.SERIES, listOf(CollectionMember(5, "o", "O"))))
        assertEquals(SyncStatus.UNSUPPORTED, repo.links.getValue(5).status)
    }
}
```

> `FakeCollectionRepo` ist eine minimale In-Memory-Implementierung von `CollectionRepository`, die `updateSyncLink` in eine `val links: MutableMap<Long, CollectionSyncLink>` schreibt (key = sourceId) und `setMembers`/`get` trivial hält. Schreibe sie im Test-File.

- [ ] **Step 2: Run — fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*CollectionSyncManagerTest*' -q`
Expected: FAIL (CollectionSyncManager fehlt)

- [ ] **Step 3: Implement**

```kotlin
package com.komgareader.app.data

import com.komgareader.domain.model.SyncStatus
import com.komgareader.domain.model.UserCollection
import com.komgareader.domain.repository.CollectionRepository
import com.komgareader.domain.repository.CollectionSyncLink
import com.komgareader.domain.source.CollectionSyncSource
import com.komgareader.domain.usecase.groupBySource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestriert den server-agnostischen Teil-Sync einer Collection: pro Quelle das Subset
 * (Replace-Semantik) upserten, Status pro Quelle in den Repository-Link schreiben. Hält
 * KEINE HTTP-Details (die liegen im Quellen-Impl); Quellen-Auflösung über [resolver]
 * (in Prod = ActiveSource::collectionSource).
 */
@Singleton
class CollectionSyncManager(
    private val repo: CollectionRepository,
    private val resolver: suspend (sourceId: Long) -> CollectionSyncSource?,
) {
    @Inject constructor(repo: CollectionRepository, active: com.komgareader.app.data.ActiveSource) :
        this(repo, { id -> active.collectionSource(id) })

    /** Pusht die kanonische Collection in alle betroffenen Quellen (best-effort, pro Quelle). */
    suspend fun push(collection: UserCollection) {
        val perSource = groupBySource(collection.members)
        for ((sourceId, members) in perSource) {
            val source = resolver(sourceId)
            if (source == null) {
                writeLink(collection.id, sourceId, null, SyncStatus.UNSUPPORTED, dirty = true)
                continue
            }
            if (!source.canWriteCollections()) {
                writeLink(collection.id, sourceId, null, SyncStatus.FORBIDDEN, dirty = true)
                continue
            }
            val remoteIds = members.map { it.remoteId }
            val result = runCatching {
                val adopt = source.listCollections(collection.kind).firstOrNull { it.name == collection.name }
                if (adopt != null) {
                    source.updateCollection(collection.kind, adopt.remoteId, collection.name, remoteIds)
                    adopt.remoteId
                } else {
                    source.createCollection(collection.kind, collection.name, remoteIds).remoteId
                }
            }
            result.fold(
                onSuccess = { remoteId -> writeLink(collection.id, sourceId, remoteId, SyncStatus.SYNCED, dirty = false) },
                onFailure = { writeLink(collection.id, sourceId, null, SyncStatus.FORBIDDEN, dirty = true) },
            )
        }
    }

    private suspend fun writeLink(collectionId: Long, sourceId: Long, remoteId: String?, status: SyncStatus, dirty: Boolean) {
        repo.updateSyncLink(CollectionSyncLink(collectionId, sourceId, remoteId, status, dirty))
    }
}
```

> Falls Hilt mit dem zweiten Konstruktor zickt: stattdessen einen `@Provides` in einem app-DI-Modul, der `ActiveSource` injiziert und `CollectionSyncManager(repo) { active.collectionSource(it) }` baut. Wähle den Weg, der zur vorhandenen DI-Struktur passt.

- [ ] **Step 4: Run — passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*CollectionSyncManagerTest*' -q`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/data/CollectionSyncManager.kt app/src/test/kotlin/com/komgareader/app/data/CollectionSyncManagerTest.kt
git commit -m "feat(app): CollectionSyncManager — Fan-out/Adopt/Status (TDD)"
```

---

### Task 12: Pull/Merge in den Manager

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/data/CollectionSyncManager.kt`
- Modify: `app/src/test/kotlin/com/komgareader/app/data/CollectionSyncManagerTest.kt`

- [ ] **Step 1: Failing test ergänzen**

```kotlin
    @Test fun `refresh merges server subsets back into canonical list`() = runBlocking {
        val s1 = FakeSource(1, canWrite = true, existing = mutableListOf(RemoteCollection("r1", "Mix", listOf("a", "d"))))
        val repo = FakeCollectionRepo()
        val mgr = CollectionSyncManager(repo, resolver = { s1 })
        val col = UserCollection(10, "Mix", CollectionKind.SERIES,
            listOf(CollectionMember(1, "a", "A"), CollectionMember(1, "b", "B")))  // b lokal, am Server weg; d am Server neu
        val merged = mgr.refresh(col)
        assertEquals(listOf("a", "d"), merged.members.map { it.remoteId })  // b weg, d neu
    }
```

- [ ] **Step 2: Run — fails** (`refresh` fehlt)

Run: `./gradlew :app:testDebugUnitTest --tests '*CollectionSyncManagerTest*refresh*' -q`
Expected: FAIL

- [ ] **Step 3: Implement `refresh`**

```kotlin
import com.komgareader.domain.usecase.mergeSubsets
// ...
    /** Lädt pro sync-fähiger Quelle das Subset, merged in die kanonische Liste, persistiert. */
    suspend fun refresh(collection: UserCollection): UserCollection {
        val perSourceRemote = mutableMapOf<Long, List<String>>()
        val titles = mutableMapOf<Pair<Long, String>, String>()
        for (sourceId in collection.members.map { it.sourceId }.toSet()) {
            val source = resolver(sourceId) ?: continue   // nicht sync-fähig → Mitglieder bleiben
            val remote = runCatching {
                source.listCollections(collection.kind).firstOrNull { it.name == collection.name }
            }.getOrNull() ?: continue
            perSourceRemote[sourceId] = remote.memberRemoteIds
        }
        val merged = mergeSubsets(collection.members, perSourceRemote) { sid, rid ->
            titles[sid to rid] ?: collection.members.firstOrNull { it.sourceId == sid && it.remoteId == rid }?.title ?: rid
        }
        repo.setMembers(collection.id, merged)
        return collection.copy(members = merged)
    }
```

- [ ] **Step 4: Run — passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*CollectionSyncManagerTest*' -q`
Expected: PASS (alle)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/data/CollectionSyncManager.kt app/src/test/kotlin/com/komgareader/app/data/CollectionSyncManagerTest.kt
git commit -m "feat(app): CollectionSyncManager.refresh — Merge aus N Quellen"
```

---

## Phase E — UI (E-Ink-Designsprache)

### Task 13: i18n-Keys

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/i18n/Strings.kt`

- [ ] **Step 1: Keys ergänzen** (Interface + `StringsDe` + `StringsEn`, echte Umlaute)

Interface:
```kotlin
    val collections: String
    val collectionsEmpty: String
    val newCollection: String
    val collectionName: String
    val collectionKindSeries: String
    val collectionKindBook: String
    val addToCollection: String
    val removeFromCollection: String
    val collectionSyncNow: String
    val collectionLocalOnly: String
    val collectionSyncInfoTitle: String
    val collectionSyncUnsupported: String
    val collectionSyncForbidden: String
    val deleteCollection: String
    val deleteCollectionServerToo: String
```
`StringsDe`:
```kotlin
    override val collections = "Collections"
    override val collectionsEmpty = "Noch keine Collections. Sammle Werke in einer eigenen Liste."
    override val newCollection = "Neue Collection"
    override val collectionName = "Name"
    override val collectionKindSeries = "Serien"
    override val collectionKindBook = "Bücher"
    override val addToCollection = "Zu Collection hinzufügen"
    override val removeFromCollection = "Aus Collection entfernen"
    override val collectionSyncNow = "Jetzt synchronisieren"
    override val collectionLocalOnly = "Nur lokal"
    override val collectionSyncInfoTitle = "Sync-Status"
    override val collectionSyncUnsupported = "Die hinterlegte Quelle unterstützt keinen Sync — diese Werke bleiben nur lokal, kein Abgleich zwischen Geräten."
    override val collectionSyncForbidden = "Dein Konto darf am Server keine Collections anlegen (nur Admins) — diese Werke bleiben nur lokal."
    override val deleteCollection = "Collection löschen"
    override val deleteCollectionServerToo = "Auch auf dem Server löschen"
```
`StringsEn`:
```kotlin
    override val collections = "Collections"
    override val collectionsEmpty = "No collections yet. Gather works into your own list."
    override val newCollection = "New collection"
    override val collectionName = "Name"
    override val collectionKindSeries = "Series"
    override val collectionKindBook = "Books"
    override val addToCollection = "Add to collection"
    override val removeFromCollection = "Remove from collection"
    override val collectionSyncNow = "Sync now"
    override val collectionLocalOnly = "Local only"
    override val collectionSyncInfoTitle = "Sync status"
    override val collectionSyncUnsupported = "The configured source does not support syncing — these works stay local only, no cross-device sync."
    override val collectionSyncForbidden = "Your account cannot create collections on the server (admins only) — these works stay local only."
    override val deleteCollection = "Delete collection"
    override val deleteCollectionServerToo = "Also delete on server"
```

- [ ] **Step 2: Build (Compile-Zeit-Parität DE/EN)**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/i18n/Strings.kt
git commit -m "feat(i18n): Collections-Strings (DE+EN)"
```

---

### Task 14: `CollectionsViewModel`

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/ui/collections/CollectionsViewModel.kt`

- [ ] **Step 1: VM** (hängt nur an agnostischen Abstraktionen)

```kotlin
package com.komgareader.app.ui.collections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komgareader.app.data.CollectionSyncManager
import com.komgareader.domain.model.CollectionKind
import com.komgareader.domain.model.CollectionMember
import com.komgareader.domain.model.UserCollection
import com.komgareader.domain.repository.CollectionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CollectionsViewModel @Inject constructor(
    private val repo: CollectionRepository,
    private val sync: CollectionSyncManager,
) : ViewModel() {

    val collections: StateFlow<List<UserCollection>> =
        repo.collections.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun create(name: String, kind: CollectionKind) = viewModelScope.launch {
        repo.create(name, kind)
    }

    fun rename(id: Long, name: String) = viewModelScope.launch { repo.rename(id, name) }

    fun delete(id: Long, serverToo: Boolean) = viewModelScope.launch {
        if (serverToo) repo.get(id)?.let { sync.deleteEverywhere(it) }
        repo.delete(id)
    }

    fun addMember(id: Long, member: CollectionMember) = viewModelScope.launch {
        repo.addMember(id, member)
        repo.get(id)?.let { sync.push(it) }
    }

    fun removeMember(id: Long, sourceId: Long, remoteId: String) = viewModelScope.launch {
        repo.removeMember(id, sourceId, remoteId)
        repo.get(id)?.let { sync.push(it) }
    }

    fun syncNow(id: Long) = viewModelScope.launch {
        repo.get(id)?.let { sync.push(it); sync.refresh(it) }
    }
}
```

- [ ] **Step 2: `deleteEverywhere` im Manager ergänzen**

In `CollectionSyncManager.kt`:
```kotlin
    /** Löscht die Server-Collections in allen sync-fähigen Quellen (best-effort). */
    suspend fun deleteEverywhere(collection: UserCollection) {
        for (sourceId in collection.members.map { it.sourceId }.toSet()) {
            val source = resolver(sourceId) ?: continue
            if (!source.canWriteCollections()) continue
            runCatching {
                source.listCollections(collection.kind).firstOrNull { it.name == collection.name }
                    ?.let { source.deleteCollection(collection.kind, it.remoteId) }
            }
        }
    }
```

- [ ] **Step 3: Build**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/collections/CollectionsViewModel.kt app/src/main/kotlin/com/komgareader/app/data/CollectionSyncManager.kt
git commit -m "feat(app): CollectionsViewModel + deleteEverywhere"
```

---

### Task 15: Collections-Übersicht + Sync-Badge + Info-Dialog

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/ui/collections/CollectionsScreen.kt`

- [ ] **Step 1: Screen** (nutzt vorhandene Bausteine: `SettingsTile`/`SeriesTile`-Stil, `EinkInfoDialog`, `EinkModal`, `ChoiceRow`, `AppIcons`, Hairline-Token)

Inhalt:
- `LazyColumn` mit einer bordered Card pro Collection: Name (titleSmall) + „N Werke" + **Badge** (kleiner umrandeter Chip mit `AppIcons` + Text `collectionLocalOnly`, wenn irgendein Link-Status ∈ {LOCAL_ONLY, UNSUPPORTED, FORBIDDEN}). Tap aufs Badge → `EinkInfoDialog(title = collectionSyncInfoTitle, closeLabel = close)` mit den passenden Erklärtexten (`collectionSyncUnsupported`/`collectionSyncForbidden`). Tap auf die Zeile → Detail (Task 17).
- Empty-State: `collectionsEmpty` + full-width `EinkOutlinedButton` „Neue Collection".
- TopBar-Aktion `AppIcons.Plus` → öffnet Create-Modal (Task 16).
- Badge-Status: aus `repo.syncLinks(id)` pro Collection beobachten (oder ableiten — für die Übersicht reicht: Collection hat mind. eine Quelle ohne SYNCED → Badge).

> Halte dich strikt an die E-Ink-Checkliste des Skills `komga-eink-ui-polish`. Keine Animation außer über `LocalEinkMode`/`LocalDisplayBehavior` gegatet. Badge-Chip = Hairline-Rahmen, kein Schatten.

- [ ] **Step 2: Build + visuell verifizieren**

Run: `./gradlew :app:installDebug -q` und auf `emulator-5554` öffnen; Screenshot der (leeren) Collections-Übersicht.
Expected: Empty-State sichtbar, E-Ink-konform.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/collections/CollectionsScreen.kt
git commit -m "feat(app): Collections-Übersicht + Sync-Badge + Erklär-Dialog"
```

---

### Task 16: Create-Collection-Modal

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/collections/CollectionsScreen.kt`

- [ ] **Step 1: `EinkModal`** mit `OutlinedTextField` (Name) + zwei `ChoiceRow` (Serien/Bücher → `CollectionKind`), Bestätigen ruft `vm.create(name, kind)`. Button-Reihenfolge: Abbrechen links, Anlegen rechts. Bestätigen disabled bei leerem Namen.

- [ ] **Step 2: Build + verifizieren** (Modal öffnen, Collection „Test/Serien" anlegen → erscheint in der Liste)

Run: `./gradlew :app:installDebug -q`; am Emulator anlegen, Screenshot.
Expected: neue Collection erscheint ohne Voll-Reload.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/collections/CollectionsScreen.kt
git commit -m "feat(app): Create-Collection-Modal (Name + kind)"
```

---

### Task 17: Collection-Detail (Liste, entfernen, Sync-Status, jetzt syncen)

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/ui/collections/CollectionDetailScreen.kt`

- [ ] **Step 1: Screen** — `SubPageScaffold(title = collection.name, onBack)`:
  - geordnete Mitglieder-Liste (Cover via vorhandenen Coil-Fetcher über `coverBytes`, Titel, `AppIcons` „entfernen" → `vm.removeMember`).
  - pro Quelle Sync-Status-Zeile (aus `repo.syncLinks(id)`), Badge + Tap → Info-Dialog (gleiche Texte wie Task 15).
  - TopBar-Aktion `collectionSyncNow` → `vm.syncNow(id)`.
  - Löschen-Aktion → Bestätigungs-`EinkModal` mit Checkbox/`ChoiceRow` `deleteCollectionServerToo` → `vm.delete(id, serverToo)`.
  - Reorder: optional (YAGNI) — falls jetzt zu groß, nur entfernen/hinzufügen; Reihenfolge = Einfüge-Reihenfolge. (Reorder in einem späteren Task nachziehbar.)

- [ ] **Step 2: Build + verifizieren** (Detail öffnen, leeres Mitglieder-Layout korrekt)

Run: `./gradlew :app:installDebug -q`; Screenshot.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/collections/CollectionDetailScreen.kt
git commit -m "feat(app): Collection-Detail (Mitglieder, Status, Sync, Löschen)"
```

---

### Task 18: „Zu Collection hinzufügen" aus SeriesDetail (SERIES)

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/ui/collections/AddToCollectionSheet.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/series/SeriesDetailScreen.kt`

- [ ] **Step 1: Auswahl-Sheet** (`EinkModal`): Liste bestehender SERIES-Collections als `ChoiceRow` (Häkchen wenn die Serie drin ist) + „Neue Collection". Auswahl ruft `vm.addMember(collectionId, CollectionMember(series.sourceId, series.remoteId, series.title))` bzw. `removeMember`.

- [ ] **Step 2: Einstieg in SeriesDetail** — Aktion `AppIcons` + Label `addToCollection` in der Detail-Aktionsleiste; öffnet das Sheet mit `series.sourceId`/`remoteId`/`title`. Keine quellen-spezifischen Typen — nur das Domain-`Series`.

- [ ] **Step 3: Build + E2E gegen Test-Komga (Admin)**

Run: `./gradlew :app:installDebug -q`. Mit verbundener Test-Komga (Admin-Key, siehe `memory/local-test-komga.md`): eine Serie zu einer SERIES-Collection hinzufügen → in der App sichtbar; am Server prüfen:
`curl -H "X-API-Key: <key>" <baseUrl>/api/v1/collections` → die Collection enthält die `seriesId`.
Expected: Server hält die Collection mit dem Werk.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/collections/AddToCollectionSheet.kt app/src/main/kotlin/com/komgareader/app/ui/series/SeriesDetailScreen.kt
git commit -m "feat(app): zu SERIES-Collection hinzufügen + E2E gegen Test-Komga"
```

---

### Task 19: Navigation — Einstieg „Collections" in der Gruppen-Region

**Files:**
- Modify: die App-Navigation der „Gruppen"-Region (`app/.../ui/groups/…` bzw. `MainActivity`/`AppNavigation` — `grep -rn "settingsRoute\|Groups\|navGroups" app/src/main` zur Verortung)

- [ ] **Step 1:** Einen Umschalter/zweiten Einstieg „Collections" neben den Shelf-Gruppen einhängen (eigene Fläche, Default laut Spec). Route auf `CollectionsScreen`; von dort Navigation ins `CollectionDetailScreen`. i18n-Label `collections`. Bottom-Nav-Mechanik unverändert (Icon über Label, Akzent-Balken-Logik wie bestehend).

- [ ] **Step 2: Build + verifizieren** (Einstieg sichtbar, Navigation Übersicht↔Detail funktioniert)

Run: `./gradlew :app:installDebug -q`; Screenshot des Einstiegs.

- [ ] **Step 3: Commit**

```bash
git add -A app/src/main/kotlin/com/komgareader/app
git commit -m "feat(app): Navigations-Einstieg Collections (Gruppen-Region)"
```

---

## Phase F — BOOK-Granularität schließen

### Task 20: „Zu Collection hinzufügen" aus der Buch-/Kapitel-Liste (BOOK) + E2E

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/collections/AddToCollectionSheet.kt` (BOOK-Variante)
- Modify: die Buch-/Kapitel-Listen-UI (`grep -rn "chapters\|BookTile\|chapterView" app/src/main` zur Verortung — `SeriesDetailScreen`/Kapitel-Liste)

- [ ] **Step 1:** Sheet auch für `CollectionKind.BOOK` (Liste der BOOK-Collections); Long-Press/Aktion auf einem Buch/Kapitel → `vm.addMember(collectionId, CollectionMember(book.sourceId, book.remoteId, book.title))`. Filter im Sheet nach `kind`, damit Serien nur in SERIES- und Bücher nur in BOOK-Collections landen.

- [ ] **Step 2: Build + E2E** — Buch zu einer BOOK-Collection hinzufügen → am Server prüfen:
`curl -H "X-API-Key: <key>" <baseUrl>/api/v1/readlists` → die Read-List enthält die `bookId`.
Expected: Server hält die Read-List mit dem Buch.

- [ ] **Step 3: Commit**

```bash
git add -A app/src/main/kotlin/com/komgareader/app
git commit -m "feat(app): zu BOOK-Collection (Read-List) hinzufügen + E2E"
```

---

## Abschluss-Verifikation

- [ ] Voller Build + alle Unit-Tests: `./gradlew :domain:test :data:test :source-komga:test :app:testDebugUnitTest -q` → grün.
- [ ] Migration-Test grün (echte DB).
- [ ] Emulator-Smoke: Collection (SERIES) anlegen → Serie hinzufügen → Badge/Status korrekt → am Server verifiziert; dann eine BOOK-Collection analog.
- [ ] OPDS-Fallback: Bei einer OPDS-Quelle bleibt das Mitglied lokal, Badge + Erklär-Popup erscheinen (kein Crash).
- [ ] Regeln nachziehen (`docs-match-code`): `architecture-seams.md` um den **Schreib**-Pfad (Naht A: `CollectionSyncSource`) ergänzen, `source-extensibility.md` referenzieren. Im selben Commit.

---

## Self-Review-Notiz (Plan ↔ Spec)

- Spec §1 (lokal-first/best-effort) → Tasks 11/12 (Status pro Quelle, FORBIDDEN/UNSUPPORTED lokal).
- Spec §2 (zwei Granularitäten, ein Konzept) → `CollectionKind` (Task 1), `when(kind)` in Komga (Task 9), BOOK-Abschluss (Task 20).
- Spec §3 (eigenes Konzept) → eigene Tabellen/Screens, Shelf unangetastet.
- Spec §4 (kein Typ) → Modell ohne Typ (Task 1).
- Spec §5 (Fan-out + Merge) → Tasks 11/12.
- Spec „Namens-Adoption" → Task 11 Test 2. „Löschen mit Server" → Tasks 14/17. „Badge + Popup" → Tasks 15/17.
- Offene bewusste Vereinfachung: Reorder der Mitglieder ist YAGNI-zurückgestellt (Task 17 Hinweis) — Reihenfolge = Einfüge-Reihenfolge, später nachziehbar.
