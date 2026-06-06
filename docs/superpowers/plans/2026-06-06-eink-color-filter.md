# E-Ink-Farbfilter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Alle in der App angezeigten Bilder (Cover + Reader-Seiten) zentral über ein wählbares
ColorMatrix-Profil filtern, damit Farben auf dem Kaleido-3-E-Ink-Display originalgetreuer wirken.

**Architecture:** Eine Compose-`colorFilter`-Naht: ein `CompositionLocal<ColorFilter?>` aus dem
aktiven `ColorProfile`, gelesen von zwei dünnen Wrappern (`FilteredAsyncImage`/`FilteredImage`),
durch die alle Bild-Aufrufstellen fließen. Profile in einer neuen Room-Tabelle (CRUD), Aktiv-Pointer
in der bestehenden Settings-KV-Tabelle. Pure Matrix-Mathematik im `domain`-Modul (TDD).

**Tech Stack:** Kotlin, Jetpack Compose, Coil, Room, Hilt, kotlin.test (JUnit5) für Domain-Unit-Tests,
androidTest (instrumentiert) für Room/Migration.

**Spec:** `docs/superpowers/specs/2026-06-06-eink-color-filter-design.md`

**Konventionen (verbindlich):**
- Echte Umlaute/ß überall (Code-Kommentare, Strings, Commits). Nie ae/oe/ue/ss.
- E-Ink-Designsprache: flach, Hairline/strongBorder statt Schatten, keine Animation, monochrom.
- Bash-Befehle als Oneliner ohne Newlines.
- Commit-Footer: `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`.
- Sichtbarer Text nur über `i18n` (DE+EN, beide Objekte + Interface pflegen).

---

### Task 1: Baseline verifizieren

**Files:** keine Änderung.

- [ ] **Step 1: Build + Domain-Tests grün als Ausgangsbasis**

Run: `cd ~/.config/superpowers/worktrees/komga-reader/feat-eink-color-filter && ./gradlew :domain:test :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. Falls nicht: STOPP, Baseline-Bruch melden, nicht weiterarbeiten.

---

### Task 2: Domain — `ColorProfile` + `buildColorMatrix` (pure, TDD)

**Files:**
- Create: `domain/src/main/kotlin/com/komgareader/domain/model/ColorProfile.kt`
- Create: `domain/src/main/kotlin/com/komgareader/domain/color/ColorFilterMatrix.kt`
- Test: `domain/src/test/kotlin/com/komgareader/domain/color/ColorFilterMatrixTest.kt`

- [ ] **Step 1: Failing test schreiben**

`domain/src/test/kotlin/com/komgareader/domain/color/ColorFilterMatrixTest.kt`:
```kotlin
package com.komgareader.domain.color

import kotlin.test.Test
import kotlin.test.assertEquals

class ColorFilterMatrixTest {

    private val tol = 1e-4f

    @Test
    fun `neutrale Werte ergeben die Identitaetsmatrix`() {
        val m = buildColorMatrix(saturation = 1f, contrast = 1f, brightness = 0f)
        val expected = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
        expected.forEachIndexed { i, v -> assertEquals(v, m[i], tol, "Index $i") }
    }

    @Test
    fun `Saettigung 0 mappt jeden Kanal auf die Rec709-Luminanz`() {
        val m = buildColorMatrix(saturation = 0f, contrast = 1f, brightness = 0f)
        // R-Zeile == G-Zeile == B-Zeile == Luminanzgewichte
        listOf(0, 5, 10).forEach { row ->
            assertEquals(0.213f, m[row + 0], tol)
            assertEquals(0.715f, m[row + 1], tol)
            assertEquals(0.072f, m[row + 2], tol)
        }
    }

    @Test
    fun `Kontrast 0_5 halbiert die Diagonale und versetzt um den Pivot`() {
        val m = buildColorMatrix(saturation = 1f, contrast = 0.5f, brightness = 0f)
        assertEquals(0.5f, m[0], tol)                 // R-Diagonale = c
        assertEquals(0.5f * 127.5f, m[4], tol)        // Offset = (1-c)*127.5 = 63.75
    }

    @Test
    fun `Helligkeit addiert einen linearen Offset`() {
        val m = buildColorMatrix(saturation = 1f, contrast = 1f, brightness = 0.5f)
        assertEquals(0.5f * 255f, m[4], tol)          // Offset = brightness*255 = 127.5
        assertEquals(0.5f * 255f, m[9], tol)
        assertEquals(0.5f * 255f, m[14], tol)
    }

    @Test
    fun `Alpha-Zeile bleibt unveraendert`() {
        val m = buildColorMatrix(saturation = 1.4f, contrast = 1.2f, brightness = 0.1f)
        assertEquals(0f, m[15], tol); assertEquals(0f, m[16], tol)
        assertEquals(0f, m[17], tol); assertEquals(1f, m[18], tol); assertEquals(0f, m[19], tol)
    }
}
```

- [ ] **Step 2: Test ausführen, Fehlschlag verifizieren**

Run: `cd ~/.config/superpowers/worktrees/komga-reader/feat-eink-color-filter && ./gradlew :domain:test --tests "com.komgareader.domain.color.ColorFilterMatrixTest"`
Expected: FAIL — `buildColorMatrix` ungelöst (Compile-Fehler).

- [ ] **Step 3: `ColorProfile`-Modell anlegen**

`domain/src/main/kotlin/com/komgareader/domain/model/ColorProfile.kt`:
```kotlin
package com.komgareader.domain.model

/**
 * Ein benanntes E-Ink-Farbfilter-Profil. Quellen-/geräteneutral: nur Zahlen, die zu einer
 * ColorMatrix werden (siehe [com.komgareader.domain.color.buildColorMatrix]).
 *
 * @param saturation 1.0 = neutral, 0.0 = Graustufen, >1 = kräftiger (Kaleido-Ausgleich).
 * @param contrast   1.0 = neutral; skaliert um den Mittelwert.
 * @param brightness 0.0 = neutral; linearer Offset.
 * @param builtIn    mitgeliefert → nicht editier-/löschbar (nur duplizierbar).
 */
data class ColorProfile(
    val id: Long,
    val name: String,
    val saturation: Float,
    val contrast: Float,
    val brightness: Float,
    val builtIn: Boolean,
) {
    /** True, wenn das Profil nichts verändert (kein Filter nötig). */
    val isNeutral: Boolean get() = saturation == 1f && contrast == 1f && brightness == 0f

    companion object {
        /** Fallback, wenn kein aktives Profil existiert: kein Filter. */
        val OFF = ColorProfile(id = 1L, name = "Aus", saturation = 1f, contrast = 1f, brightness = 0f, builtIn = true)
    }
}
```

- [ ] **Step 4: `buildColorMatrix` implementieren**

`domain/src/main/kotlin/com/komgareader/domain/color/ColorFilterMatrix.kt`:
```kotlin
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
```

- [ ] **Step 5: Test ausführen, grün verifizieren**

Run: `cd ~/.config/superpowers/worktrees/komga-reader/feat-eink-color-filter && ./gradlew :domain:test --tests "com.komgareader.domain.color.ColorFilterMatrixTest"`
Expected: PASS (5 Tests grün).

- [ ] **Step 6: Commit**

```bash
git add domain/src/main/kotlin/com/komgareader/domain/model/ColorProfile.kt domain/src/main/kotlin/com/komgareader/domain/color/ColorFilterMatrix.kt domain/src/test/kotlin/com/komgareader/domain/color/ColorFilterMatrixTest.kt
git commit -m "feat(domain): ColorProfile + buildColorMatrix (Sättigung/Kontrast/Helligkeit)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: Domain — `ColorProfileRepository`-Interface + Settings-Aktiv-Pointer

**Files:**
- Create: `domain/src/main/kotlin/com/komgareader/domain/repository/ColorProfileRepository.kt`
- Modify: `domain/src/main/kotlin/com/komgareader/domain/repository/SettingsRepository.kt`

- [ ] **Step 1: `ColorProfileRepository`-Interface anlegen**

`domain/src/main/kotlin/com/komgareader/domain/repository/ColorProfileRepository.kt`:
```kotlin
package com.komgareader.domain.repository

import com.komgareader.domain.model.ColorProfile
import kotlinx.coroutines.flow.Flow

/** Verwaltung der E-Ink-Farbfilter-Profile. Quellen-/geräteneutral. */
interface ColorProfileRepository {
    /** Alle Profile (Built-ins zuerst), reaktiv. */
    fun observeAll(): Flow<List<ColorProfile>>

    /** Das aktive Profil; fällt auf [ColorProfile.OFF] zurück, nie leer. */
    fun observeActive(): Flow<ColorProfile>

    /** Legt an oder aktualisiert; gibt die id zurück. Built-ins dürfen nicht aktualisiert werden. */
    suspend fun upsert(profile: ColorProfile): Long

    /** Löscht ein Custom-Profil. Built-ins werden ignoriert. */
    suspend fun delete(id: Long)

    /** Markiert das Profil [id] als aktiv. */
    suspend fun setActive(id: Long)
}
```

- [ ] **Step 2: `SettingsRepository` um Aktiv-Pointer erweitern**

In `domain/src/main/kotlin/com/komgareader/domain/repository/SettingsRepository.kt` innerhalb des
`interface Strings`-… nein, des `interface SettingsRepository`-Blocks ergänzen — neue Zeilen
nach `val downloadDir`:
```kotlin
    val activeColorProfileId: Flow<Long?>  // id des aktiven Farbfilter-Profils, null = noch keines gesetzt
```
und nach `suspend fun setDownloadDir(uri: String?)`:
```kotlin
    suspend fun setActiveColorProfileId(id: Long)
```

- [ ] **Step 3: Compile verifizieren**

Run: `cd ~/.config/superpowers/worktrees/komga-reader/feat-eink-color-filter && ./gradlew :domain:compileKotlin`
Expected: BUILD SUCCESSFUL (RoomSettingsRepository wird in Task 5 angepasst; `:domain` allein hat keine Impl).

- [ ] **Step 4: Commit**

```bash
git add domain/src/main/kotlin/com/komgareader/domain/repository/ColorProfileRepository.kt domain/src/main/kotlin/com/komgareader/domain/repository/SettingsRepository.kt
git commit -m "feat(domain): ColorProfileRepository-Interface + Settings-Aktiv-Pointer

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: Data — `ColorProfileEntity` + DAO

**Files:**
- Modify: `data/src/main/kotlin/com/komgareader/data/db/Entities.kt`
- Create: `data/src/main/kotlin/com/komgareader/data/db/ColorProfileDao.kt`

- [ ] **Step 1: Entity ergänzen**

Ans Ende von `data/src/main/kotlin/com/komgareader/data/db/Entities.kt` anhängen:
```kotlin
/** Persistiertes E-Ink-Farbfilter-Profil. */
@Entity(tableName = "color_profiles")
data class ColorProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val saturation: Float,
    val contrast: Float,
    val brightness: Float,
    val builtIn: Boolean,
)
```

- [ ] **Step 2: DAO anlegen**

`data/src/main/kotlin/com/komgareader/data/db/ColorProfileDao.kt`:
```kotlin
package com.komgareader.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ColorProfileDao {
    /** Built-ins zuerst, dann alphabetisch. */
    @Query("SELECT * FROM color_profiles ORDER BY builtIn DESC, name ASC")
    fun observeAll(): Flow<List<ColorProfileEntity>>

    @Query("SELECT * FROM color_profiles WHERE id = :id")
    fun observeById(id: Long): Flow<ColorProfileEntity?>

    @Upsert
    suspend fun upsert(entity: ColorProfileEntity): Long

    @Query("DELETE FROM color_profiles WHERE id = :id AND builtIn = 0")
    suspend fun deleteCustom(id: Long)
}
```

- [ ] **Step 3: Compile verifizieren (Entity noch nicht in @Database — kommt Task 6)**

Run: `cd ~/.config/superpowers/worktrees/komga-reader/feat-eink-color-filter && ./gradlew :data:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add data/src/main/kotlin/com/komgareader/data/db/Entities.kt data/src/main/kotlin/com/komgareader/data/db/ColorProfileDao.kt
git commit -m "feat(data): ColorProfileEntity + ColorProfileDao

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: Data — `RoomColorProfileRepository` + Settings-Impl (TDD: Fallback-Logik)

**Files:**
- Create: `data/src/main/kotlin/com/komgareader/data/repository/RoomColorProfileRepository.kt`
- Modify: `data/src/main/kotlin/com/komgareader/data/repository/RoomSettingsRepository.kt`
- Test: `data/src/test/kotlin/com/komgareader/data/repository/RoomColorProfileRepositoryTest.kt`

- [ ] **Step 1: Failing test (Fake-DAO, JVM) schreiben**

`data/src/test/kotlin/com/komgareader/data/repository/RoomColorProfileRepositoryTest.kt`:
```kotlin
package com.komgareader.data.repository

import com.komgareader.data.db.ColorProfileDao
import com.komgareader.data.db.ColorProfileEntity
import com.komgareader.domain.model.ColorProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** In-Memory-Fake des DAO — testet die Mapping-/Fallback-Logik ohne echtes Room. */
private class FakeColorProfileDao : ColorProfileDao {
    val rows = MutableStateFlow<List<ColorProfileEntity>>(emptyList())
    override fun observeAll(): Flow<List<ColorProfileEntity>> = rows
    override fun observeById(id: Long): Flow<ColorProfileEntity?> =
        rows.map { list -> list.firstOrNull { it.id == id } }
    override suspend fun upsert(entity: ColorProfileEntity): Long {
        val id = if (entity.id == 0L) (rows.value.maxOfOrNull { it.id } ?: 0L) + 1L else entity.id
        rows.value = rows.value.filterNot { it.id == id } + entity.copy(id = id)
        return id
    }
    override suspend fun deleteCustom(id: Long) {
        rows.value = rows.value.filterNot { it.id == id && !it.builtIn }
    }
}

/** Fake der Aktiv-Pointer-Quelle. */
private class FakeActivePointer {
    val flow = MutableStateFlow<Long?>(null)
}

class RoomColorProfileRepositoryTest {

    private fun repo(dao: ColorProfileDao, pointer: FakeActivePointer) =
        RoomColorProfileRepository(dao, pointer.flow) { pointer.flow.value = it }

    @Test
    fun `observeActive faellt auf OFF zurueck wenn kein Pointer gesetzt`() = runTest {
        val dao = FakeColorProfileDao()
        dao.rows.value = listOf(ColorProfileEntity(1, "Aus", 1f, 1f, 0f, true))
        val active = repo(dao, FakeActivePointer()).observeActive().first()
        assertEquals(ColorProfile.OFF, active)
    }

    @Test
    fun `observeActive liefert das Profil zum Pointer`() = runTest {
        val dao = FakeColorProfileDao()
        dao.rows.value = listOf(
            ColorProfileEntity(1, "Aus", 1f, 1f, 0f, true),
            ColorProfileEntity(2, "Boox Go Color 7 Gen2", 1.4f, 1.15f, 0.05f, true),
        )
        val pointer = FakeActivePointer().also { it.flow.value = 2L }
        val active = repo(dao, pointer).observeActive().first()
        assertEquals(2L, active.id)
        assertEquals(1.4f, active.saturation)
    }

    @Test
    fun `observeAll mappt Entities zu Domain-Profilen`() = runTest {
        val dao = FakeColorProfileDao()
        dao.rows.value = listOf(ColorProfileEntity(2, "Custom", 1.2f, 1.1f, 0f, false))
        val all = repo(dao, FakeActivePointer()).observeAll().first()
        assertEquals(1, all.size)
        assertEquals("Custom", all[0].name)
        assertEquals(false, all[0].builtIn)
    }

    @Test
    fun `upsert eines Built-ins wird abgelehnt`() = runTest {
        val dao = FakeColorProfileDao()
        val r = repo(dao, FakeActivePointer())
        try {
            r.upsert(ColorProfile(id = 1, name = "x", saturation = 1f, contrast = 1f, brightness = 0f, builtIn = true))
            kotlin.test.fail("erwartete IllegalArgumentException")
        } catch (_: IllegalArgumentException) { /* erwartet */ }
    }
}
```

- [ ] **Step 2: Test ausführen, Fehlschlag verifizieren**

Run: `cd ~/.config/superpowers/worktrees/komga-reader/feat-eink-color-filter && ./gradlew :data:testDebugUnitTest --tests "com.komgareader.data.repository.RoomColorProfileRepositoryTest"`
Expected: FAIL — `RoomColorProfileRepository` ungelöst.

- [ ] **Step 3: `RoomColorProfileRepository` implementieren**

`data/src/main/kotlin/com/komgareader/data/repository/RoomColorProfileRepository.kt`:
```kotlin
package com.komgareader.data.repository

import com.komgareader.data.db.ColorProfileDao
import com.komgareader.data.db.ColorProfileEntity
import com.komgareader.domain.model.ColorProfile
import com.komgareader.domain.repository.ColorProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Room-gestützte Profilverwaltung. Der Aktiv-Pointer lebt in der Settings-KV-Tabelle und
 * wird hier als Flow + Setter hereingereicht (von [RoomSettingsRepository] verdrahtet via DI),
 * damit dieses Repo nur eine Verantwortung hat.
 */
class RoomColorProfileRepository(
    private val dao: ColorProfileDao,
    private val activePointer: Flow<Long?>,
    private val setActivePointer: suspend (Long) -> Unit,
) : ColorProfileRepository {

    override fun observeAll(): Flow<List<ColorProfile>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeActive(): Flow<ColorProfile> =
        combine(dao.observeAll(), activePointer) { list, activeId ->
            list.firstOrNull { it.id == activeId }?.toDomain() ?: ColorProfile.OFF
        }

    override suspend fun upsert(profile: ColorProfile): Long {
        require(!profile.builtIn) { "Built-in-Profile dürfen nicht verändert werden" }
        return dao.upsert(profile.toEntity())
    }

    override suspend fun delete(id: Long) = dao.deleteCustom(id)

    override suspend fun setActive(id: Long) = setActivePointer(id)
}

private fun ColorProfileEntity.toDomain() =
    ColorProfile(id, name, saturation, contrast, brightness, builtIn)

private fun ColorProfile.toEntity() =
    ColorProfileEntity(id, name, saturation, contrast, brightness, builtIn)
```

- [ ] **Step 4: `RoomSettingsRepository` um Aktiv-Pointer erweitern**

In `data/src/main/kotlin/com/komgareader/data/repository/RoomSettingsRepository.kt`:

Import ergänzen (oben):
```kotlin
import kotlinx.coroutines.flow.Flow
```
(bereits vorhanden — sicherstellen.)

Nach `override val downloadDir` ergänzen:
```kotlin
    override val activeColorProfileId: Flow<Long?> =
        dao.observe(KEY_ACTIVE_COLOR_PROFILE).map { it?.toLongOrNull() }
```
Nach `setDownloadDir` ergänzen:
```kotlin
    override suspend fun setActiveColorProfileId(id: Long) =
        dao.put(SettingEntity(KEY_ACTIVE_COLOR_PROFILE, id.toString()))
```
Im `companion object` ergänzen:
```kotlin
        const val KEY_ACTIVE_COLOR_PROFILE = "active_color_profile_id"
```
**Wichtig:** das `private companion object` auf `companion object` lockern, damit DI den Key-Default
nicht braucht — nein, bleibt `private`. Der Key wird nur intern genutzt. Belassen als `private companion object`.

- [ ] **Step 5: Test ausführen, grün verifizieren**

Run: `cd ~/.config/superpowers/worktrees/komga-reader/feat-eink-color-filter && ./gradlew :data:testDebugUnitTest --tests "com.komgareader.data.repository.RoomColorProfileRepositoryTest"`
Expected: PASS (4 Tests grün).

- [ ] **Step 6: Commit**

```bash
git add data/src/main/kotlin/com/komgareader/data/repository/RoomColorProfileRepository.kt data/src/main/kotlin/com/komgareader/data/repository/RoomSettingsRepository.kt data/src/test/kotlin/com/komgareader/data/repository/RoomColorProfileRepositoryTest.kt
git commit -m "feat(data): RoomColorProfileRepository + Settings-Aktiv-Pointer (Fallback getestet)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 6: Data — DB v6→v7 Migration + Seeds + AppDatabase-Verdrahtung

**Files:**
- Modify: `data/src/main/kotlin/com/komgareader/data/db/AppDatabase.kt`
- Modify: `data/src/main/kotlin/com/komgareader/data/di/DataModule.kt`
- Test: `data/src/androidTest/kotlin/com/komgareader/data/ColorProfileMigrationTest.kt`

- [ ] **Step 1: `@Database` + Migration ergänzen**

In `AppDatabase.kt`: `entities`-Liste um `ColorProfileEntity::class` erweitern, `version = 6` → `version = 7`,
DAO-Getter ergänzen:
```kotlin
@Database(
    entities = [
        SettingEntity::class, ServerEntity::class, DownloadEntity::class,
        ShelfEntity::class, ColorProfileEntity::class,
    ],
    version = 7,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun settingsDao(): SettingsDao
    abstract fun serverDao(): ServerDao
    abstract fun downloadDao(): DownloadDao
    abstract fun shelfDao(): ShelfDao
    abstract fun colorProfileDao(): ColorProfileDao
}
```

Ans Ende von `AppDatabase.kt` anhängen:
```kotlin
/**
 * v6 → v7: color_profiles-Tabelle für E-Ink-Farbfilter-Profile. Seedet zwei Built-ins
 * (Aus = neutral, Boox Go Color 7 Gen2 = Kaleido-getunt) und setzt das Go-7-Profil aktiv.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `color_profiles` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `saturation` REAL NOT NULL,
                `contrast` REAL NOT NULL,
                `brightness` REAL NOT NULL,
                `builtIn` INTEGER NOT NULL
            )""",
        )
        db.execSQL(
            "INSERT INTO `color_profiles` (`id`,`name`,`saturation`,`contrast`,`brightness`,`builtIn`) " +
                "VALUES (1,'Aus',1.0,1.0,0.0,1)",
        )
        db.execSQL(
            "INSERT INTO `color_profiles` (`id`,`name`,`saturation`,`contrast`,`brightness`,`builtIn`) " +
                "VALUES (2,'Boox Go Color 7 Gen2',1.4,1.15,0.05,1)",
        )
        // Aktiv-Pointer = Go-7-Profil (Zielgerät). Settings-KV-Tabelle.
        db.execSQL(
            "INSERT OR REPLACE INTO `settings` (`key`,`value`) VALUES ('active_color_profile_id','2')",
        )
    }
}
```

- [ ] **Step 2: Migration registrieren + DAO/Repo in DI bereitstellen**

In `DataModule.kt`: Import `MIGRATION_6_7` ergänzen und in `addMigrations(...)` anhängen:
```kotlin
import com.komgareader.data.db.MIGRATION_6_7
```
```kotlin
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
```

Provider ergänzen (im `object DataModule`), Imports `ColorProfileRepository`, `RoomColorProfileRepository`,
`SettingsRepository`-Impl-Zugriff für den Pointer:
```kotlin
    @Provides @Singleton
    fun colorProfileRepository(
        db: AppDatabase,
        settings: SettingsRepository,
    ): ColorProfileRepository =
        RoomColorProfileRepository(
            dao = db.colorProfileDao(),
            activePointer = settings.activeColorProfileId,
            setActivePointer = { settings.setActiveColorProfileId(it) },
        )
```
Imports oben in `DataModule.kt` ergänzen:
```kotlin
import com.komgareader.data.repository.RoomColorProfileRepository
import com.komgareader.domain.repository.ColorProfileRepository
```

- [ ] **Step 3: Instrumentierten Migrationstest schreiben**

`data/src/androidTest/kotlin/com/komgareader/data/ColorProfileMigrationTest.kt`:
```kotlin
package com.komgareader.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.komgareader.data.db.AppDatabase
import com.komgareader.data.db.MIGRATION_6_7
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class ColorProfileMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migration6To7_seedsBuiltInsAndSetsActive() {
        val name = "migration-test.db"
        // v6 anlegen (leer reicht; Schema kommt aus den Bundled-Schemas nicht — daher manuell minimal).
        helper.createDatabase(name, 6).apply {
            execSQL("CREATE TABLE IF NOT EXISTS `settings` (`key` TEXT NOT NULL PRIMARY KEY, `value` TEXT NOT NULL)")
            close()
        }
        val db = helper.runMigrationsAndValidate(name, 7, true, MIGRATION_6_7)
        db.query("SELECT COUNT(*) FROM color_profiles").use {
            it.moveToFirst(); assertEquals(2, it.getInt(0))
        }
        db.query("SELECT value FROM settings WHERE key='active_color_profile_id'").use {
            assertTrue(it.moveToFirst()); assertEquals("2", it.getString(0))
        }
        db.close()
    }
}
```
> Hinweis Executor: `exportSchema = false` → kein Bundled-Schema. Der Test legt nur die für die
> Migration nötigen v6-Tabellen (`settings`) manuell an und validiert die v7-Migration isoliert.
> Falls `runMigrationsAndValidate` wegen fehlendem Gesamt-Schema fehlschlägt, stattdessen
> `validateDroppedTables=false` bzw. nur die Migration-Effekte prüfen (Tabelle + Seeds), ohne
> Voll-Schema-Validierung. Sollte `androidx.room:room-testing` in `data/build.gradle.kts`
> (`androidTestImplementation`) fehlen, ergänzen.

- [ ] **Step 4: Compile + (falls Emulator verfügbar) Migrationstest ausführen**

Run (Compile): `cd ~/.config/superpowers/worktrees/komga-reader/feat-eink-color-filter && ./gradlew :data:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.
Run (instrumentiert, Emulator `eink_test` läuft): `./gradlew :data:connectedDebugAndroidTest --tests "com.komgareader.data.ColorProfileMigrationTest"`
Expected: PASS. Falls kein Gerät: überspringen und in Task 14 zusammen verifizieren.

- [ ] **Step 5: Commit**

```bash
git add data/src/main/kotlin/com/komgareader/data/db/AppDatabase.kt data/src/main/kotlin/com/komgareader/data/di/DataModule.kt data/src/androidTest/kotlin/com/komgareader/data/ColorProfileMigrationTest.kt
git commit -m "feat(data): DB v6→v7 Migration + Built-in-Profile geseedet, ColorProfileRepository in DI

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 7: App — `LocalImageFilter` + `FilteredAsyncImage`/`FilteredImage` Wrapper

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/ui/components/FilteredImage.kt`

- [ ] **Step 1: CompositionLocal + Wrapper + Profil→ColorFilter-Mapping anlegen**

`app/src/main/kotlin/com/komgareader/app/ui/components/FilteredImage.kt`:
```kotlin
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

/** Wandelt ein Profil in einen ColorFilter — oder `null`, wenn es nichts verändert. */
fun ColorProfile.toColorFilterOrNull(): ColorFilter? =
    if (isNeutral) null
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
```

- [ ] **Step 2: Compile verifizieren**

Run: `cd ~/.config/superpowers/worktrees/komga-reader/feat-eink-color-filter && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/components/FilteredImage.kt
git commit -m "feat(app): LocalImageFilter + FilteredAsyncImage/FilteredImage Wrapper

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 8: App — Bild-Aufrufstellen auf Wrapper umstellen + Filter am Root bereitstellen

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/library/LibraryScreen.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/groups/GroupBrowseRoute.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/series/SeriesDetailScreen.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/reader/PagedReaderScreen.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/reader/WebtoonReaderScreen.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/reader/EpubReaderScreen.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/MainActivity.kt`

- [ ] **Step 1: `SettingsViewModel` um aktives Profil erweitern**

In `SettingsViewModel.kt`: Konstruktor-Parameter ergänzen und Flow exponieren.
Import ergänzen:
```kotlin
import com.komgareader.domain.model.ColorProfile
import com.komgareader.domain.repository.ColorProfileRepository
```
Konstruktor:
```kotlin
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val servers: ServerRepository,
    private val colorProfiles: ColorProfileRepository,
) : ViewModel() {
```
Property ergänzen (nach `val server`):
```kotlin
    val activeColorProfile = colorProfiles.observeActive()
        .stateIn(viewModelScope, SharingStarted.Eagerly, ColorProfile.OFF)
```

- [ ] **Step 2: Filter am NavHost-Root bereitstellen**

In `MainActivity.kt`: Imports ergänzen:
```kotlin
import com.komgareader.app.ui.components.LocalImageFilter
import com.komgareader.app.ui.components.toColorFilterOrNull
```
Im `setContent`, nach den bestehenden `collectAsState()`-Zeilen:
```kotlin
            val activeColorProfile by settingsViewModel.activeColorProfile.collectAsState()
```
Den `CompositionLocalProvider` um den Filter erweitern:
```kotlin
            CompositionLocalProvider(
                LocalStrings provides stringsFor(language),
                LocalEinkMode provides isEink,
                LocalImageFilter provides activeColorProfile.toColorFilterOrNull(),
            ) {
```

- [ ] **Step 3: `LibraryScreen` `SeriesCover` umstellen**

In `LibraryScreen.kt`: Import `coil.compose.AsyncImage` durch Wrapper-Import ersetzen:
```kotlin
import com.komgareader.app.ui.components.FilteredAsyncImage
```
(`import coil.compose.AsyncImage` entfernen.)
Den `AsyncImage(...)`-Block (Zeilen ~173-178) ersetzen durch:
```kotlin
        FilteredAsyncImage(
            model = request,
            contentDescription = series.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
```

- [ ] **Step 4: `GroupBrowseRoute` umstellen**

In `GroupBrowseRoute.kt`: `import coil.compose.AsyncImage` durch
`import com.komgareader.app.ui.components.FilteredAsyncImage` ersetzen. Den `AsyncImage(...)`-Aufruf
(ab Zeile ~147) zu `FilteredAsyncImage(...)` umbenennen — Parameter `model`, `contentDescription`,
`contentScale`, `modifier` bleiben identisch. Falls weitere Parameter (z. B. `crossfade` ist im
Request, nicht am Composable) gesetzt sind: nur die im Wrapper vorhandenen übernehmen.

- [ ] **Step 5: `SeriesDetailScreen` umstellen**

In `SeriesDetailScreen.kt`: `import coil.compose.AsyncImage` durch
`import com.komgareader.app.ui.components.FilteredAsyncImage` ersetzen. Den `AsyncImage(...)`-Aufruf
(ab Zeile ~269) zu `FilteredAsyncImage(...)`. Parameter beibehalten (`model`, `contentDescription`,
`contentScale`, `modifier`).

- [ ] **Step 6: `PagedReaderScreen` + `WebtoonReaderScreen` umstellen**

In beiden: `import coil.compose.AsyncImage` durch
`import com.komgareader.app.ui.components.FilteredAsyncImage` ersetzen, `AsyncImage(...)` →
`FilteredAsyncImage(...)`. PagedReader: `contentScale = ContentScale.Fit` bleibt. Webtoon:
`contentScale = ContentScale.FillWidth` bleibt.

- [ ] **Step 7: `EpubReaderScreen` umstellen (`Image` → `FilteredImage`)**

In `EpubReaderScreen.kt`: `import androidx.compose.foundation.Image` entfernen,
`import com.komgareader.app.ui.components.FilteredImage` ergänzen. Den `Image(bitmap = ...)`-Block
(Zeilen ~70-75) ersetzen:
```kotlin
                    FilteredImage(
                        bitmap = bmp!!.asImageBitmap(),
                        contentDescription = "Seite ${pageIndex + 1}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
```

- [ ] **Step 8: Compile verifizieren**

Run: `cd ~/.config/superpowers/worktrees/komga-reader/feat-eink-color-filter && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. Bei „unused import coil.compose.AsyncImage": entfernen.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/
git commit -m "feat(app): alle Bild-Stellen über Filter-Wrapper, aktiver Filter am Root bereitgestellt

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 9: App — `StepperRow`-Komponente (E-Ink-Regler)

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/components/EinkComponents.kt`

- [ ] **Step 1: `StepperRow` anlegen**

Ans Ende von `EinkComponents.kt` anhängen (Imports `IconButton`, `Icons.Outlined.Add`/`Remove` ergänzen):
```kotlin
/**
 * Diskrete ±-Regelzeile (kein kontinuierlicher Slider — ruckelt auf E-Ink). Label links,
 * aktueller Wert mittig, − / + Buttons rechts. [enabled] sperrt z. B. bei Built-in-Profilen.
 */
@Composable
fun StepperRow(
    label: String,
    valueText: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        IconButton(onClick = onDecrement, enabled = enabled) {
            Icon(Icons.Outlined.Remove, contentDescription = "−", modifier = Modifier.size(22.dp))
        }
        Text(
            valueText,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.width(56.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        IconButton(onClick = onIncrement, enabled = enabled) {
            Icon(Icons.Outlined.Add, contentDescription = "+", modifier = Modifier.size(22.dp))
        }
    }
}
```
Imports oben in `EinkComponents.kt` ergänzen:
```kotlin
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
```

- [ ] **Step 2: Compile verifizieren**

Run: `cd ~/.config/superpowers/worktrees/komga-reader/feat-eink-color-filter && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/components/EinkComponents.kt
git commit -m "feat(app): StepperRow (diskrete ±-Regelzeile für E-Ink)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 10: App — `ColorFilterViewModel` (CRUD-State + Vorschau-Cover)

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/ui/settings/ColorFilterViewModel.kt`

- [ ] **Step 1: ViewModel anlegen**

`app/src/main/kotlin/com/komgareader/app/ui/settings/ColorFilterViewModel.kt`:
```kotlin
package com.komgareader.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komgareader.app.data.AuthHeaders
import com.komgareader.app.data.KomgaSourceProvider
import com.komgareader.domain.model.ColorProfile
import com.komgareader.domain.repository.ColorProfileRepository
import com.komgareader.domain.repository.ServerRepository
import com.komgareader.domain.source.SourceFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Vorschau-Bildquelle: ein zufälliges Cover + die zugehörigen Auth-Header. */
data class PreviewCover(val url: String, val headers: Map<String, String>)

/** Editor-Werte (live, noch nicht persistiert). */
data class EditState(
    val baseProfileId: Long,
    val name: String,
    val saturation: Float,
    val contrast: Float,
    val brightness: Float,
    val builtIn: Boolean,
)

@HiltViewModel
class ColorFilterViewModel @Inject constructor(
    private val colorProfiles: ColorProfileRepository,
    private val servers: ServerRepository,
    private val sourceProvider: KomgaSourceProvider,
) : ViewModel() {

    val profiles = colorProfiles.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val active = colorProfiles.observeActive()
        .stateIn(viewModelScope, SharingStarted.Eagerly, ColorProfile.OFF)

    private val _edit = MutableStateFlow<EditState?>(null)
    val edit: StateFlow<EditState?> = _edit

    private val _preview = MutableStateFlow<PreviewCover?>(null)
    val preview: StateFlow<PreviewCover?> = _preview

    init { loadPreviewCover() }

    private fun loadPreviewCover() = viewModelScope.launch {
        val config = servers.config.first() ?: return@launch
        val source = sourceProvider.from(config) ?: return@launch
        runCatching { source.browse(0, SourceFilter()).items }
            .getOrNull()
            ?.mapNotNull { it.coverUrl }
            ?.randomOrNull()
            ?.let { url -> _preview.value = PreviewCover(url, AuthHeaders.forCovers(config)) }
    }

    fun setActive(id: Long) = viewModelScope.launch { colorProfiles.setActive(id) }

    /** Editor mit den Werten von [profile] öffnen. */
    fun beginEdit(profile: ColorProfile) {
        _edit.value = EditState(
            baseProfileId = profile.id, name = profile.name,
            saturation = profile.saturation, contrast = profile.contrast,
            brightness = profile.brightness, builtIn = profile.builtIn,
        )
    }

    fun cancelEdit() { _edit.value = null }

    fun updateSaturation(delta: Float) = mutate { it.copy(saturation = clamp(it.saturation + delta, 0.5f, 2f)) }
    fun updateContrast(delta: Float) = mutate { it.copy(contrast = clamp(it.contrast + delta, 0.5f, 2f)) }
    fun updateBrightness(delta: Float) = mutate { it.copy(brightness = clamp(it.brightness + delta, -0.5f, 0.5f)) }

    /** Aktuelle Editor-Werte als neues Custom-Profil speichern und aktiv setzen. */
    fun saveAsNew(name: String) = viewModelScope.launch {
        val e = _edit.value ?: return@launch
        val id = colorProfiles.upsert(
            ColorProfile(0, name.ifBlank { "Profil" }, e.saturation, e.contrast, e.brightness, builtIn = false),
        )
        colorProfiles.setActive(id)
        _edit.value = null
    }

    /** Bestehendes Custom-Profil aktualisieren. */
    fun updateExisting() = viewModelScope.launch {
        val e = _edit.value ?: return@launch
        if (e.builtIn) return@launch
        colorProfiles.upsert(ColorProfile(e.baseProfileId, e.name, e.saturation, e.contrast, e.brightness, builtIn = false))
        _edit.value = null
    }

    fun delete(id: Long) = viewModelScope.launch { colorProfiles.delete(id) }

    private fun mutate(f: (EditState) -> EditState) { _edit.value = _edit.value?.let(f) }
    private fun clamp(v: Float, lo: Float, hi: Float) = v.coerceIn(lo, hi)
}
```
> Hinweis Executor: `KomgaSourceProvider` ist dasselbe DI-Objekt, das `LibraryViewModel` injiziert
> (`com.komgareader.app.data.KomgaSourceProvider`). `Series.coverUrl` ist nullbar → `mapNotNull`.

- [ ] **Step 2: Compile verifizieren**

Run: `cd ~/.config/superpowers/worktrees/komga-reader/feat-eink-color-filter && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/settings/ColorFilterViewModel.kt
git commit -m "feat(app): ColorFilterViewModel (Profil-CRUD-State + Vorschau-Cover)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 11: App — `ColorFilterSettingsScreen` (Auswahl + Vorschau + Editor)

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/ui/settings/ColorFilterSettingsScreen.kt`

> Dieser Screen nutzt die i18n-Keys aus Task 13. Reihenfolge im Subagent-Lauf: erst Task 13
> (Strings) ODER die Keys provisorisch via `s.<key>` referenzieren und Task 13 direkt danach.
> Empfehlung: **Task 13 vor Task 11 ausführen.**

- [ ] **Step 1: Screen anlegen**

`app/src/main/kotlin/com/komgareader/app/ui/settings/ColorFilterSettingsScreen.kt`:
```kotlin
package com.komgareader.app.ui.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.request.ImageRequest
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.ui.components.ChoiceRow
import com.komgareader.app.ui.components.EinkModal
import com.komgareader.app.ui.components.FilteredAsyncImage
import com.komgareader.app.ui.components.SectionHeader
import com.komgareader.app.ui.components.StepperRow
import com.komgareader.app.ui.components.SubPageScaffold
import com.komgareader.app.ui.components.toColorFilterOrNull
import com.komgareader.domain.model.ColorProfile

private const val STEP = 0.05f

@Composable
fun ColorFilterSettingsScreen(
    onBack: () -> Unit,
    viewModel: ColorFilterViewModel = hiltViewModel(),
) {
    val s = LocalStrings.current
    val profiles by viewModel.profiles.collectAsState()
    val active by viewModel.active.collectAsState()
    val edit by viewModel.edit.collectAsState()
    val preview by viewModel.preview.collectAsState()
    val ctx = LocalContext.current

    var showSaveDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    SubPageScaffold(title = s.settingsColorFilter, onBack = onBack) {
        // Vorschau: zufälliges Cover, gefiltert mit den Editor-Werten (oder dem aktiven Profil).
        val previewProfile = edit?.let {
            ColorProfile(it.baseProfileId, it.name, it.saturation, it.contrast, it.brightness, it.builtIn)
        } ?: active
        preview?.let { p ->
            val request = remember(p.url) {
                ImageRequest.Builder(ctx).data(p.url)
                    .apply { p.headers.forEach { addHeader(it.key, it.value) } }
                    .crossfade(false).build()
            }
            Box(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                FilteredAsyncImage(
                    model = request,
                    contentDescription = s.colorFilterPreview,
                    contentScale = ContentScale.Fit,
                    colorFilterOverride = previewProfile.toColorFilterOrNull(),
                    useOverride = true,
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .aspectRatio(2f / 3f)
                        .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp)),
                )
            }
        }

        SectionHeader(s.colorFilterProfiles)
        profiles.forEach { profile ->
            ChoiceRow(label = profile.name, selected = profile.id == active.id) {
                viewModel.setActive(profile.id)
                viewModel.beginEdit(profile)
            }
        }

        // Editor erscheint, sobald ein Profil angetippt wurde.
        edit?.let { e ->
            SectionHeader(s.colorFilterAdjust)
            StepperRow(
                label = s.colorFilterSaturation,
                valueText = format(e.saturation),
                onDecrement = { viewModel.updateSaturation(-STEP) },
                onIncrement = { viewModel.updateSaturation(STEP) },
                enabled = !e.builtIn,
            )
            StepperRow(
                label = s.colorFilterContrast,
                valueText = format(e.contrast),
                onDecrement = { viewModel.updateContrast(-STEP) },
                onIncrement = { viewModel.updateContrast(STEP) },
                enabled = !e.builtIn,
            )
            StepperRow(
                label = s.colorFilterBrightness,
                valueText = format(e.brightness),
                onDecrement = { viewModel.updateBrightness(-STEP) },
                onIncrement = { viewModel.updateBrightness(STEP) },
                enabled = !e.builtIn,
            )

            Column(Modifier.padding(top = 8.dp)) {
                // Built-in: nur „Duplizieren“ (= als neues speichern). Custom: aktualisieren + löschen.
                ChoiceRow(label = s.colorFilterSaveAsNew, selected = false) {
                    newName = if (e.builtIn) "${e.name} (Kopie)" else e.name
                    showSaveDialog = true
                }
                if (!e.builtIn) {
                    ChoiceRow(label = s.colorFilterUpdate, selected = false) { viewModel.updateExisting() }
                    ChoiceRow(label = s.colorFilterDelete, selected = false) { viewModel.delete(e.baseProfileId) }
                }
            }
        }
    }

    if (showSaveDialog) {
        EinkModal(
            title = s.colorFilterSaveAsNew,
            onDismiss = { showSaveDialog = false },
            confirmLabel = s.save,
            onConfirm = { viewModel.saveAsNew(newName); showSaveDialog = false },
            dismissLabel = s.cancel,
            confirmEnabled = newName.isNotBlank(),
        ) {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text(s.colorFilterProfileName) },
                singleLine = true,
            )
        }
    }
}

private fun format(v: Float): String = ((v * 100).toInt() / 100f).toString()
```
> Hinweis Executor: `s.save` / `s.cancel` müssen in `Strings` existieren. Falls nicht vorhanden,
> in Task 13 mit ergänzen (prüfen: evtl. bereits als `s.connect`/anderer Bestätigungs-Text da —
> NICHT wiederverwenden, eigene generische `save`/`cancel`-Keys anlegen).

- [ ] **Step 2: Compile verifizieren**

Run: `cd ~/.config/superpowers/worktrees/komga-reader/feat-eink-color-filter && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (setzt Task 13 voraus).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/settings/ColorFilterSettingsScreen.kt
git commit -m "feat(app): ColorFilterSettingsScreen (Auswahl, Live-Vorschau, Editor, Speichern-Dialog)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 12: App — Settings-Seite `COLOR_FILTER` verdrahten

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/settings/SettingsLandingScreen.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/MainActivity.kt`

- [ ] **Step 1: Enum + Tile ergänzen**

In `SettingsLandingScreen.kt`:
- `enum class SettingsPage { CONNECTION, APPEARANCE, READER, DOWNLOADS, LANGUAGE, ABOUT }` →
  `enum class SettingsPage { CONNECTION, APPEARANCE, COLOR_FILTER, READER, DOWNLOADS, LANGUAGE, ABOUT }`
- Import-Icon ergänzen: `import androidx.compose.material.icons.outlined.Palette`
- In `buildTiles(...)` nach dem APPEARANCE-Tile ergänzen:
```kotlin
    SettingsTileModel(
        SettingsPage.COLOR_FILTER, Icons.Outlined.Palette, s.settingsColorFilter, s.colorFilterSummary,
        "${s.settingsColorFilter} ${s.colorFilterProfiles} ${s.colorFilterSaturation} ${s.colorFilterContrast} ${s.colorFilterBrightness}",
    ),
```

- [ ] **Step 2: Nav-Route + Routing ergänzen**

In `MainActivity.kt`:
- Import: `import com.komgareader.app.ui.settings.ColorFilterSettingsScreen`
- Im `NavHost` nach dem `settings/appearance`-Block:
```kotlin
                        composable("settings/colorfilter") {
                            ColorFilterSettingsScreen(onBack = { nav.popBackStack() })
                        }
```
- In `settingsRoute(page)` den Zweig ergänzen:
```kotlin
    SettingsPage.COLOR_FILTER -> "settings/colorfilter"
```

- [ ] **Step 3: Compile verifizieren**

Run: `cd ~/.config/superpowers/worktrees/komga-reader/feat-eink-color-filter && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/settings/SettingsLandingScreen.kt app/src/main/kotlin/com/komgareader/app/MainActivity.kt
git commit -m "feat(app): Farbfilter-Kachel + Navigation in den Einstellungen

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 13: i18n — Strings (DE + EN)

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/i18n/Strings.kt`

> **Vor Task 11 + 12 ausführen** (beide referenzieren diese Keys).

- [ ] **Step 1: Keys im `interface Strings` ergänzen**

Im `interface Strings`-Block (nahe den anderen `settings*`-Keys) ergänzen:
```kotlin
    val settingsColorFilter: String
    val colorFilterSummary: String
    val colorFilterProfiles: String
    val colorFilterAdjust: String
    val colorFilterSaturation: String
    val colorFilterContrast: String
    val colorFilterBrightness: String
    val colorFilterSaveAsNew: String
    val colorFilterUpdate: String
    val colorFilterDelete: String
    val colorFilterProfileName: String
    val colorFilterPreview: String
    val save: String
    val cancel: String
```

- [ ] **Step 2: Deutsche Werte in `object StringsDe` ergänzen**

```kotlin
    override val settingsColorFilter = "Farbfilter"
    override val colorFilterSummary = "Bilder fürs E-Ink-Display anpassen"
    override val colorFilterProfiles = "Profile"
    override val colorFilterAdjust = "Anpassen"
    override val colorFilterSaturation = "Sättigung"
    override val colorFilterContrast = "Kontrast"
    override val colorFilterBrightness = "Helligkeit"
    override val colorFilterSaveAsNew = "Als neues Profil speichern"
    override val colorFilterUpdate = "Profil aktualisieren"
    override val colorFilterDelete = "Profil löschen"
    override val colorFilterProfileName = "Profilname"
    override val colorFilterPreview = "Vorschau"
    override val save = "Speichern"
    override val cancel = "Abbrechen"
```

- [ ] **Step 3: Englische Werte in `object StringsEn` ergänzen**

```kotlin
    override val settingsColorFilter = "Color Filter"
    override val colorFilterSummary = "Tune images for the e-ink display"
    override val colorFilterProfiles = "Profiles"
    override val colorFilterAdjust = "Adjust"
    override val colorFilterSaturation = "Saturation"
    override val colorFilterContrast = "Contrast"
    override val colorFilterBrightness = "Brightness"
    override val colorFilterSaveAsNew = "Save as new profile"
    override val colorFilterUpdate = "Update profile"
    override val colorFilterDelete = "Delete profile"
    override val colorFilterProfileName = "Profile name"
    override val colorFilterPreview = "Preview"
    override val save = "Save"
    override val cancel = "Cancel"
```

- [ ] **Step 4: Compile verifizieren (Parität DE/EN)**

Run: `cd ~/.config/superpowers/worktrees/komga-reader/feat-eink-color-filter && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. Fehlt ein Override in einem der Objekte → Compile-Fehler (Parität erzwungen).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/i18n/Strings.kt
git commit -m "feat(i18n): Farbfilter-Strings (DE + EN)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 14: Verifikation — Build, Tests, sichtbarer Beweis

**Files:** keine Änderung (reine Verifikation).

- [ ] **Step 1: Voller Build + alle Unit-Tests**

Run: `cd ~/.config/superpowers/worktrees/komga-reader/feat-eink-color-filter && ./gradlew :domain:test :data:testDebugUnitTest :app:assembleDebug`
Expected: BUILD SUCCESSFUL, alle Tests grün.

- [ ] **Step 2: Migrationstest instrumentiert (Emulator `eink_test`)**

Emulator starten (siehe [[local-test-komga]]), dann:
Run: `cd ~/.config/superpowers/worktrees/komga-reader/feat-eink-color-filter && ./gradlew :data:connectedDebugAndroidTest --tests "com.komgareader.data.ColorProfileMigrationTest"`
Expected: PASS.

- [ ] **Step 3: Sichtbarer E2E-Beweis**

APK auf Emulator/Boox installieren, lokale Test-Komga verbinden. Verifizieren:
1. Bibliothek zeigt Cover — Profil „Boox Go Color 7 Gen2" ist Default-aktiv (Cover wirken kräftiger).
2. Einstellungen → Farbfilter: Vorschau-Cover sichtbar; „Aus" wählen → Vorschau + Bibliothek werden neutral.
3. Built-in duplizieren → Sättigung per ± ändern → Vorschau ändert sich live → speichern → aktiv.
4. Reader öffnen (Paged + EPUB) → Seiten sind gefiltert.
5. Dark-Mode zusätzlich an → Filter + Invert kollidieren nicht (bekannte Interaktion, Spec).

Screenshot je Schritt 1–4 als Beweis ablegen.

- [ ] **Step 4: Abschluss-Commit (falls noch offen) + Branch-Status**

Run: `cd ~/.config/superpowers/worktrees/komga-reader/feat-eink-color-filter && git status && git log --oneline -15`
Expected: sauberer Tree, alle Tasks committet.

---

## Selbst-Review (vom Plan-Autor)

**Spec-Abdeckung:** zentraler Filter Cover+Viewer (Task 7/8) ✓ · Profil-Liste mit Built-ins +
Custom-CRUD (Task 3–6, 10–11) ✓ · Default Go-7 aktiv (Task 6 Migration) ✓ · Live-Vorschau mit
echtem Cover (Task 10–11) ✓ · StepperRow statt Slider (Task 9, 11) ✓ · Settings→Display→Farbfilter
(Task 12) ✓ · i18n DE/EN (Task 13) ✓ · pure Matrix TDD (Task 2) ✓ · Repo-/Migrationstests (Task 5–6) ✓ ·
Phase-2-Bereitschaft (ColorProfile erweiterbar, Wrapper-Naht) ✓ · Dark-Mode-Interaktion (Task 14) ✓.

**Reihenfolge-Hinweis:** Task 13 (i18n) vor Task 11 + 12 ausführen.

**Typ-Konsistenz:** `ColorProfile`-Felder, `buildColorMatrix`-Signatur, `ColorProfileRepository`-Methoden,
`toColorFilterOrNull()`, `LocalImageFilter`, `FilteredAsyncImage`/`FilteredImage`, `StepperRow`,
`PreviewCover`/`EditState` durchgängig identisch verwendet.
