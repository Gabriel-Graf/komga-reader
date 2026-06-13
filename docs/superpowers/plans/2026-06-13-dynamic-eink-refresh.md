# Dynamischer E-Ink-Refresh- & Farbmodus pro Lese-Kontext — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Pro Lese-Kontext (Home/Manga/Webtoon/Comic/Novel) automatisch einen Onyx-EinkWise-Refresh-Modus + System-Farbmodus schalten; den alten App-getriebenen GC-Pfad (`RefreshScheduler`/`OnyxRefresher`/`deviceManagedRefresh`) ersetzen.

**Architecture:** Naht B (Geräteseite). Domain bleibt Onyx-frei: der `EinkController` meldet eine offene Liste von Modi (`EinkModeOption`) + geräte-spezifische Per-Kontext-Defaults; `eink-onyx` ist die einzige Stelle, die auf `UpdateOption`/`EpdController` mappt. Ein `EinkContextController` (app/data) wendet das aufgelöste Profil an, sobald ein Screen seinen Kontext meldet. User-Overrides liegen als JSON-Blob in den Room-Settings.

**Tech Stack:** Kotlin, Hilt, Jetpack Compose, Room (Key-Value-Settings), kotlinx.serialization, Onyx `onyxsdk-device:1.3.5` (`EpdController.setAppScopeRefreshMode`/`UpdateOption`, `enableColorAdjust`/`disableColorAdjust`), JUnit.

> **Code, KDoc, Kommentare = ENGLISCH** (Projektkonvention). Commit-Messages/Specs deutsch.
> **Spec:** `docs/superpowers/specs/2026-06-13-dynamic-eink-refresh-design.md`.
> **Reihenfolge:** Neuer Pfad zuerst (Tasks 1–6), App bleibt durchgehend baubar; alter Pfad erst danach raus (Tasks 7–9); Doku zuletzt (Task 10).

---

## File Structure

**Neu:**
- `domain/src/main/kotlin/com/komgareader/domain/eink/EinkProfiles.kt` — `EinkContext`, `EinkModeOption`, `EinkContextProfile`, `resolveEinkProfile`.
- `domain/src/test/kotlin/com/komgareader/domain/eink/EinkProfilesTest.kt`
- `data/src/main/kotlin/com/komgareader/data/eink/EinkContextProfilesCodec.kt` — pure JSON ↔ `Map<EinkContext,EinkContextProfile>`.
- `data/src/test/kotlin/com/komgareader/data/eink/EinkContextProfilesCodecTest.kt`
- `app/src/main/kotlin/com/komgareader/app/data/EinkContextController.kt` — `@Singleton`, wendet Profile an.
- `app/src/main/kotlin/com/komgareader/app/ui/reader/EinkContextEffect.kt` — Composable + `EinkContextHolder` ViewModel; ersetzt `EinkReaderEffect`.
- `app/src/main/kotlin/com/komgareader/app/ui/settings/EinkDynamicsSettingsContent.kt` — Matrix-UI.

**Geändert (Interface/Verträge):**
- `domain/.../eink/EinkController.kt` (Capabilities + Controller-Methoden)
- `app/.../eink/NoOpEinkController.kt`, `eink-onyx/.../OnyxEinkController.kt` (implementieren neue Methoden)
- `domain/.../repository/SettingsRepository.kt`, `data/.../repository/RoomSettingsRepository.kt` (+ Fakes)
- `app/.../settings/SettingsViewModel.kt`, `SettingsSections.kt`, i18n `Strings.kt`/`MapBackedStrings.kt`
- `app/.../ui/home/HomeScreen.kt`, `app/.../ui/reader/ReaderRoute.kt`

**Entfernt (alter Pfad, Tasks 7–9):**
- `domain/.../eink/RefreshScheduler.kt` (+ Test), `eink-onyx/.../OnyxRefresher.kt`, `app/.../reader/ReaderEinkHolder.kt`, `app/.../reader/EinkReaderEffect.kt`
- `Viewer.refreshScheduler` + alle Reader-VM/Screen-Trigger; `deviceManagedRefresh` end-to-end; `ReaderPresetOverrides.deviceManagedRefresh`; tote Onyx-Methoden.

---

## Phase 1 — Domain (pure, TDD)

### Task 1: Domain-Modelle + Resolver

**Files:**
- Create: `domain/src/main/kotlin/com/komgareader/domain/eink/EinkProfiles.kt`
- Test: `domain/src/test/kotlin/com/komgareader/domain/eink/EinkProfilesTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.komgareader.domain.eink

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EinkProfilesTest {

    @Test
    fun `user override wins per axis over device default`() {
        val user = EinkContextProfile(refreshModeId = "speed", colorModeId = null)
        val device = EinkContextProfile(refreshModeId = "hd", colorModeId = "system")
        val resolved = resolveEinkProfile(user, device)
        assertEquals("speed", resolved.refreshModeId) // user wins
        assertEquals("system", resolved.colorModeId)  // falls back to device (user null)
    }

    @Test
    fun `null user falls back to device default`() {
        val device = EinkContextProfile(refreshModeId = "hd", colorModeId = "system")
        assertEquals(device, resolveEinkProfile(null, device))
    }

    @Test
    fun `both null yields untouched profile`() {
        val resolved = resolveEinkProfile(null, EinkContextProfile())
        assertNull(resolved.refreshModeId)
        assertNull(resolved.colorModeId)
    }

    @Test
    fun `all five contexts exist`() {
        assertEquals(5, EinkContext.entries.size)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :domain:test --tests "com.komgareader.domain.eink.EinkProfilesTest"`
Expected: FAIL — `EinkProfiles`/`resolveEinkProfile` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.komgareader.domain.eink

/** What is currently on screen — drives which E-Ink profile is applied. */
enum class EinkContext { HOME, PAGED, WEBTOON, COMIC, NOVEL }

/**
 * A device-advertised E-Ink mode option (refresh or colour). [id] is stable and persisted;
 * [label] is the device's default display name (the app may override it via i18n for known
 * built-in ids).
 */
data class EinkModeOption(val id: String, val label: String)

/** A per-context profile. `null` on an axis means "leave the device/system default untouched". */
data class EinkContextProfile(
    val refreshModeId: String? = null,
    val colorModeId: String? = null,
)

/**
 * Merges a user override over a device default, per axis: a set override wins, an unset
 * (null) axis falls back to the device default. Pure — no device/Onyx knowledge.
 */
fun resolveEinkProfile(
    userOverride: EinkContextProfile?,
    deviceDefault: EinkContextProfile,
): EinkContextProfile = EinkContextProfile(
    refreshModeId = userOverride?.refreshModeId ?: deviceDefault.refreshModeId,
    colorModeId = userOverride?.colorModeId ?: deviceDefault.colorModeId,
)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :domain:test --tests "com.komgareader.domain.eink.EinkProfilesTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add domain/src/main/kotlin/com/komgareader/domain/eink/EinkProfiles.kt domain/src/test/kotlin/com/komgareader/domain/eink/EinkProfilesTest.kt
git commit -m "feat(eink): Domain-Modelle + Resolver für kontext-basierte E-Ink-Profile"
```

---

### Task 2: `EinkController`/`EinkCapabilities` erweitern + alle Impls anpassen

Interface-Erweiterung bricht beide Impls — in EINEM Task halten, damit alles kompiliert. Onyx-Mapping ist Gerätecode (nicht unit-testbar) → per Build + späterer manueller Boox-Verifikation abgesichert.

**Files:**
- Modify: `domain/src/main/kotlin/com/komgareader/domain/eink/EinkController.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/eink/NoOpEinkController.kt`
- Modify: `eink-onyx/src/main/kotlin/com/komgareader/eink/onyx/OnyxEinkController.kt`

- [ ] **Step 1: Erweitere `EinkCapabilities` + `EinkController`**

In `EinkController.kt` `EinkCapabilities` um zwei Felder **mit Default `emptyList()`** ergänzen (bestehende 3-arg-Aufrufe bleiben gültig):

```kotlin
data class EinkCapabilities(
    val hasEink: Boolean,
    val canColor: Boolean,
    val canInvert: Boolean,
    /** Refresh modes this device can switch app-wide; empty = axis unsupported (UI hides it). */
    val refreshModes: List<EinkModeOption> = emptyList(),
    /** System colour modes this device can switch; empty = axis unsupported. */
    val colorModes: List<EinkModeOption> = emptyList(),
)
```

Dem `interface EinkController` hinzufügen:

```kotlin
    /** Applies an advertised refresh mode by id; null/unknown id = graceful no-op. */
    fun applyRefreshMode(id: String?)

    /** Applies an advertised system colour mode by id; null/unknown id = graceful no-op. */
    fun applyColorMode(id: String?)

    /** Device-specific sane default profile for the given context (app stores only overrides). */
    fun defaultProfile(context: EinkContext): EinkContextProfile
```

Import `EinkContext`, `EinkContextProfile`, `EinkModeOption` sind im selben Package — kein Import nötig.

- [ ] **Step 2: `NoOpEinkController` implementiert die neuen Methoden (alles no-op/leer)**

```kotlin
    override fun applyRefreshMode(id: String?) { /* No-Op */ }
    override fun applyColorMode(id: String?) { /* No-Op */ }
    override fun defaultProfile(context: EinkContext) = EinkContextProfile()
```

Imports ergänzen: `com.komgareader.domain.eink.EinkContext`, `com.komgareader.domain.eink.EinkContextProfile`. `capabilities` bleibt unverändert (leere Modus-Listen per Default → Achse versteckt).

- [ ] **Step 3: `OnyxEinkController` — echtes Mapping auf `UpdateOption` + Farbe**

Imports ergänzen:
```kotlin
import com.komgareader.domain.eink.EinkContext
import com.komgareader.domain.eink.EinkContextProfile
import com.komgareader.domain.eink.EinkModeOption
import com.onyx.android.sdk.api.device.epd.UpdateOption
```

`capabilities` um die gemeldeten Modus-Listen erweitern (stabile IDs, englische Labels als Default — i18n-Override später in der UI):
```kotlin
    override val capabilities = EinkCapabilities(
        hasEink = true,
        canColor = true,
        canInvert = true,
        refreshModes = listOf(
            EinkModeOption(REFRESH_HD, "HD"),
            EinkModeOption(REFRESH_BALANCED, "Balanced"),
            EinkModeOption(REFRESH_REGAL, "Regal"),
            EinkModeOption(REFRESH_SPEED, "Speed"),
            EinkModeOption(REFRESH_ULTRA, "Ultra"),
        ),
        colorModes = listOf(
            EinkModeOption(COLOR_SYSTEM, "System"),
            EinkModeOption(COLOR_ON, "Colour"),
            EinkModeOption(COLOR_MONO, "Mono"),
        ),
    )
```

Neue Methoden implementieren:
```kotlin
    override fun applyRefreshMode(id: String?) {
        val option = when (id) {
            REFRESH_HD -> UpdateOption.NORMAL
            REFRESH_BALANCED -> UpdateOption.FAST_QUALITY
            REFRESH_REGAL -> UpdateOption.REGAL
            REFRESH_SPEED -> UpdateOption.FAST
            REFRESH_ULTRA -> UpdateOption.FAST_X
            else -> return // null/unknown = leave untouched
        }
        runCatching { EpdController.setAppScopeRefreshMode(option) }
            .onFailure { Log.e(TAG, "setAppScopeRefreshMode($option) failed", it) }
    }

    override fun applyColorMode(id: String?) {
        runCatching {
            when (id) {
                COLOR_ON -> EpdController.enableColorAdjust()
                COLOR_MONO -> EpdController.disableColorAdjust()
                else -> Unit // COLOR_SYSTEM / null / unknown = leave untouched
            }
        }.onFailure { Log.e(TAG, "applyColorMode($id) failed", it) }
    }

    override fun defaultProfile(context: EinkContext): EinkContextProfile {
        val refresh = if (context == EinkContext.WEBTOON) REFRESH_SPEED else REFRESH_HD
        return EinkContextProfile(refreshModeId = refresh, colorModeId = COLOR_SYSTEM)
    }
```

In `companion object` die ID-Konstanten ergänzen:
```kotlin
        const val REFRESH_HD = "hd"
        const val REFRESH_BALANCED = "balanced"
        const val REFRESH_REGAL = "regal"
        const val REFRESH_SPEED = "speed"
        const val REFRESH_ULTRA = "ultra"
        const val COLOR_SYSTEM = "system"
        const val COLOR_ON = "color"
        const val COLOR_MONO = "mono"
```

> Die alten Methoden (`enterFastMode`/`exitFastMode`/`fullRefresh`/…) bleiben **vorerst stehen** (werden in Task 9 entfernt), damit der alte Pfad bis dahin kompiliert.

- [ ] **Step 4: Build (kompiliert alle drei Module)**

Run: `./gradlew :domain:compileDebugKotlin :eink-onyx:compileDebugKotlin :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add domain/src/main/kotlin/com/komgareader/domain/eink/EinkController.kt app/src/main/kotlin/com/komgareader/app/eink/NoOpEinkController.kt eink-onyx/src/main/kotlin/com/komgareader/eink/onyx/OnyxEinkController.kt
git commit -m "feat(eink): EinkController meldet Modi + wendet Onyx-EinkWise-Refresh/Farbe an"
```

---

## Phase 2 — Persistenz (data, TDD)

### Task 3: JSON-Codec für User-Overrides

**Files:**
- Create: `data/src/main/kotlin/com/komgareader/data/eink/EinkContextProfilesCodec.kt`
- Test: `data/src/test/kotlin/com/komgareader/data/eink/EinkContextProfilesCodecTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.komgareader.data.eink

import com.komgareader.domain.eink.EinkContext
import com.komgareader.domain.eink.EinkContextProfile
import kotlin.test.Test
import kotlin.test.assertEquals

class EinkContextProfilesCodecTest {

    @Test
    fun `round-trips a full map`() {
        val map = mapOf(
            EinkContext.PAGED to EinkContextProfile("hd", "system"),
            EinkContext.WEBTOON to EinkContextProfile("speed", null),
        )
        assertEquals(map, decodeEinkContextProfiles(encodeEinkContextProfiles(map)))
    }

    @Test
    fun `blank or invalid json decodes to empty map`() {
        assertEquals(emptyMap(), decodeEinkContextProfiles(null))
        assertEquals(emptyMap(), decodeEinkContextProfiles(""))
        assertEquals(emptyMap(), decodeEinkContextProfiles("not json"))
    }

    @Test
    fun `unknown context key is skipped, not fatal`() {
        val json = """{"PAGED":{"refreshModeId":"hd"},"BOGUS":{"refreshModeId":"x"}}"""
        val decoded = decodeEinkContextProfiles(json)
        assertEquals(setOf(EinkContext.PAGED), decoded.keys)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :data:test --tests "com.komgareader.data.eink.EinkContextProfilesCodecTest"`
Expected: FAIL — codec functions unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.komgareader.data.eink

import com.komgareader.domain.eink.EinkContext
import com.komgareader.domain.eink.EinkContextProfile
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class ProfileDto(val refreshModeId: String? = null, val colorModeId: String? = null)

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/** Serialises user overrides to a compact JSON object keyed by EinkContext name. */
fun encodeEinkContextProfiles(map: Map<EinkContext, EinkContextProfile>): String {
    val dto = map.entries.associate { (k, v) -> k.name to ProfileDto(v.refreshModeId, v.colorModeId) }
    return json.encodeToString(dto)
}

/** Parses overrides; null/blank/invalid → empty map; unknown context keys are skipped. */
fun decodeEinkContextProfiles(raw: String?): Map<EinkContext, EinkContextProfile> {
    if (raw.isNullOrBlank()) return emptyMap()
    val parsed = runCatching { json.decodeFromString<Map<String, ProfileDto>>(raw) }.getOrNull()
        ?: return emptyMap()
    return parsed.mapNotNull { (key, dto) ->
        val ctx = runCatching { EinkContext.valueOf(key) }.getOrNull() ?: return@mapNotNull null
        ctx to EinkContextProfile(dto.refreshModeId, dto.colorModeId)
    }.toMap()
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :data:test --tests "com.komgareader.data.eink.EinkContextProfilesCodecTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add data/src/main/kotlin/com/komgareader/data/eink/EinkContextProfilesCodec.kt data/src/test/kotlin/com/komgareader/data/eink/EinkContextProfilesCodecTest.kt
git commit -m "feat(eink): JSON-Codec für E-Ink-Kontext-Profile (User-Overrides)"
```

---

### Task 4: `SettingsRepository` um `einkContextProfiles` erweitern

Interface-Erweiterung bricht alle Impls/Fakes — alle in EINEM Task anpassen.

**Files:**
- Modify: `domain/src/main/kotlin/com/komgareader/domain/repository/SettingsRepository.kt`
- Modify: `data/src/main/kotlin/com/komgareader/data/repository/RoomSettingsRepository.kt`
- Modify: `app/src/androidTest/kotlin/com/komgareader/app/ci/ui/TestDataModule.kt` (Fake)
- Prüfen/anpassen: jede weitere `SettingsRepository`-Fake in `app/src/test/...` und `data/src/test/...` (z. B. in `SettingsViewModelTest.kt`, `ReaderViewModelTest.kt`, `RoomSettingsRepositoryTest.kt`) — wenn sie das Interface direkt implementieren.

- [ ] **Step 1: Interface erweitern**

In `SettingsRepository.kt` ergänzen:
```kotlin
    /** Per-context E-Ink mode overrides (user); axes the user did not set fall back to the device default. */
    val einkContextProfiles: Flow<Map<EinkContext, EinkContextProfile>>
```
und bei den Settern:
```kotlin
    suspend fun setEinkContextProfile(context: EinkContext, profile: EinkContextProfile)
```
Imports: `com.komgareader.domain.eink.EinkContext`, `com.komgareader.domain.eink.EinkContextProfile`.

- [ ] **Step 2: `RoomSettingsRepository` implementieren**

Flow (Single-Key-JSON-Blob, Codec aus Task 3):
```kotlin
    override val einkContextProfiles: Flow<Map<EinkContext, EinkContextProfile>> =
        dao.observe(KEY_EINK_CONTEXT_PROFILES).map { decodeEinkContextProfiles(it) }
```
Setter (read-modify-write des Blobs):
```kotlin
    override suspend fun setEinkContextProfile(context: EinkContext, profile: EinkContextProfile) {
        val current = decodeEinkContextProfiles(dao.observe(KEY_EINK_CONTEXT_PROFILES).first())
        val next = current + (context to profile)
        dao.put(SettingEntity(KEY_EINK_CONTEXT_PROFILES, encodeEinkContextProfiles(next)))
    }
```
Companion-Key:
```kotlin
        const val KEY_EINK_CONTEXT_PROFILES = "eink_context_profiles"
```
Imports ergänzen: Codec-Funktionen, `EinkContext`, `EinkContextProfile`, `kotlinx.coroutines.flow.first`. **Keine Room-Migration** (neuer Key im bestehenden Key-Value-Table).

- [ ] **Step 3: Alle Fakes anpassen**

Jede Fake-/Stub-`SettingsRepository` ergänzt:
```kotlin
    override val einkContextProfiles = MutableStateFlow(emptyMap<EinkContext, EinkContextProfile>())
    override suspend fun setEinkContextProfile(context: EinkContext, profile: EinkContextProfile) {
        einkContextProfiles.value = einkContextProfiles.value + (context to profile)
    }
```
(Bei reinen Stubs ohne State: `flowOf(emptyMap())` + leerer Setter.)

- [ ] **Step 4: Build + bestehende Settings-Tests grün**

Run: `./gradlew :data:test :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL, bestehende Tests grün.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(eink): SettingsRepository hält per-Kontext-E-Ink-Overrides (JSON-Blob)"
```

---

## Phase 3 — App-Shell: dynamisches Schalten

### Task 5: `EinkContextController` (@Singleton)

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/data/EinkContextController.kt`

- [ ] **Step 1: Implementierung**

```kotlin
package com.komgareader.app.data

import com.komgareader.domain.eink.EinkContext
import com.komgareader.domain.eink.EinkController
import com.komgareader.domain.eink.resolveEinkProfile
import com.komgareader.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Imperative shell for the device-managed E-Ink seam: given the context currently on screen,
 * resolves the effective profile (user override over device default) and applies it via the
 * [EinkController]. App-scope refresh is global, so each context change is a single apply call.
 * On non-Boox devices the controller advertises no modes and every apply is a no-op.
 */
@Singleton
class EinkContextController @Inject constructor(
    private val controller: EinkController,
    private val settings: SettingsRepository,
) {
    suspend fun applyFor(context: EinkContext) {
        val override = settings.einkContextProfiles.first()[context]
        val resolved = resolveEinkProfile(override, controller.defaultProfile(context))
        controller.applyRefreshMode(resolved.refreshModeId)
        controller.applyColorMode(resolved.colorModeId)
    }
}
```

- [ ] **Step 2: Build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (EinkController + SettingsRepository sind bereits Hilt-bereitgestellt).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/data/EinkContextController.kt
git commit -m "feat(eink): EinkContextController wendet aufgelöstes Profil je Kontext an"
```

---

### Task 6: `EinkContextEffect` + Verdrahtung Home/Reader

`EinkContextEffect` re-appliziert bei **Resume** des Screens (robust gegen Back-Stack: schließt der Reader, resumed Home und setzt HOME erneut). Ersetzt funktional `EinkReaderEffect` (dessen Entfernung in Task 7).

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/ui/reader/EinkContextEffect.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/home/HomeScreen.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/reader/ReaderRoute.kt`

- [ ] **Step 1: Effect + Holder**

```kotlin
package com.komgareader.app.ui.reader

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.hilt.navigation.compose.hiltViewModel
import com.komgareader.app.data.EinkContextController
import com.komgareader.domain.eink.EinkContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/** Bridges the singleton [EinkContextController] into Composition (mirrors ReaderEinkHolder). */
@HiltViewModel
class EinkContextHolder @Inject constructor(
    val controller: EinkContextController,
) : ViewModel()

/**
 * Declares the E-Ink context of the current screen. Re-applies on every resume so the correct
 * profile is restored when returning from a screen pushed on top (e.g. reader → home).
 */
@Composable
fun EinkContextEffect(context: EinkContext) {
    val holder = hiltViewModel<EinkContextHolder>()
    LifecycleResumeEffect(context) {
        runBlocking { holder.controller.applyFor(context) }
        onPauseOrDispose { }
    }
}
```

> Hinweis: `applyFor` ist `suspend` (liest einen Flow-Wert). `runBlocking` ist hier vertretbar (ein schneller Settings-Read + ein nativer Aufruf, einmal pro Kontextwechsel). Falls der ausführende Agent das vermeiden will: `LaunchedEffect` + `rememberCoroutineScope` ist eine zulässige Alternative — dann aber sicherstellen, dass es bei Resume re-läuft (Key = context + ein Resume-Trigger). `LifecycleResumeEffect` + `runBlocking` ist der einfachere, korrekte Default.

- [ ] **Step 2: HomeScreen meldet HOME**

In `HomeScreen.kt` ganz oben im Haupt-Composable (vor dem Shell-Render) einfügen:
```kotlin
    EinkContextEffect(EinkContext.HOME)
```
Import: `com.komgareader.app.ui.reader.EinkContextEffect`, `com.komgareader.domain.eink.EinkContext`.

- [ ] **Step 3: ReaderRoute meldet den Reader-Kontext**

In `ReaderRoute.kt`: den aktiven `ViewerMode`/Content auf `EinkContext` mappen und `EinkContextEffect(ctx)` aufrufen (gekeyt auf den Modus → folgt dem In-Screen-Toggle paged⟷webtoon). Mapping:
```kotlin
private fun einkContextFor(mode: ViewerMode, isNovel: Boolean): EinkContext = when {
    isNovel -> EinkContext.NOVEL
    mode == ViewerMode.WEBTOON -> EinkContext.WEBTOON
    mode == ViewerMode.COMIC -> EinkContext.COMIC
    else -> EinkContext.PAGED
}
```
Aufruf an der Stelle, wo `ReaderRoute` den aktuellen `viewerMode`/Content kennt (dort, wo heute `EinkReaderEffect(refresher)` steht — Aufruf zunächst **zusätzlich** einfügen, Entfernung von `EinkReaderEffect` in Task 7):
```kotlin
    EinkContextEffect(einkContextFor(viewerMode, isNovel = /* content is ReaderContent.Novel */))
```
Der ausführende Agent liest in `ReaderRoute.kt`, wie der aktuelle Modus/Content-Typ dort verfügbar ist (`viewModel.viewerMode`/`ReaderContent`-`when`), und füllt `mode`/`isNovel` entsprechend.

- [ ] **Step 4: Build + Emulator-Smoke**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. (Funktionsverifikation E-Ink erst nach Task 8 manuell auf Boox; Emulator nutzt NoOp → keine sichtbare Änderung, darf nicht crashen.)

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(eink): Home/Reader melden ihren Kontext → automatischer Modus-Wechsel"
```

---

## Phase 4 — Settings-UI

### Task 7: SettingsViewModel + Matrix-UI + i18n + Sektion

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/settings/SettingsViewModel.kt`
- Create: `app/src/main/kotlin/com/komgareader/app/ui/settings/EinkDynamicsSettingsContent.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/settings/SettingsSections.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/i18n/Strings.kt`, `MapBackedStrings.kt`

- [ ] **Step 1: SettingsViewModel — Capabilities + Overrides + Setter exponieren**

`EinkController` in den `SettingsViewModel` injizieren (Hilt-bereitgestellt). Exponieren:
- `val refreshModes: List<EinkModeOption> = einkController.capabilities.refreshModes`
- `val colorModes: List<EinkModeOption> = einkController.capabilities.colorModes`
- `val einkContextProfiles: StateFlow<Map<EinkContext, EinkContextProfile>>` (aus `settings.einkContextProfiles`, `stateIn`)
- `fun setEinkRefreshMode(ctx, id: String?)` / `fun setEinkColorMode(ctx, id: String?)` → liest aktuelles Profil aus dem Flow, ruft `settings.setEinkContextProfile(ctx, updatedProfile)` in `viewModelScope`.

(Muster: existierende Setter im selben ViewModel, z. B. der bisherige `setDeviceManagedRefresh`, als Vorlage nehmen.)

- [ ] **Step 2: i18n-Strings**

In `Strings.kt` (Interface) + `MapBackedStrings.kt` (de+en Maps) ergänzen — echte Umlaute:
- `settingsEinkDynamics` → de „E-Ink Dynamik" / en „E-Ink dynamics"
- `settingsEinkDynamicsDesc` → de „Refresh- und Farbmodus je Lese-Kontext automatisch schalten." / en „Switch refresh and colour mode automatically per reading context."
- Kontext-Labels: `einkContextHome/Paged/Webtoon/Comic/Novel` (de: „Startseite/Manga/Webtoon/Comic/Roman", en: „Home/Manga/Webtoon/Comic/Novel")
- Achsen-Labels: `einkAxisRefresh` („Refresh"/„Refresh"), `einkAxisColor` („Farbe"/„Colour")
- Default-Option: `einkModeDeviceDefault` („Gerät entscheidet"/„Device default")
- Bekannte Modus-IDs i18n: `einkRefreshHd/Balanced/Regal/Speed/Ultra`, `einkColorSystem/Color/Mono`. Fallback in der UI: ist die ID unbekannt, den vom Deskriptor gemeldeten `EinkModeOption.label` zeigen.

- [ ] **Step 3: `EinkDynamicsSettingsContent` (Matrix)**

Composable, das für jeden `EinkContext` (entries-Reihenfolge) eine Zeile mit zwei Dropdowns rendert (Refresh + Farbe), Optionen aus `refreshModes`/`colorModes` + vorangestellter „Gerät entscheidet"-Eintrag (= `null`). Auswahl → `viewModel.setEinkRefreshMode/​setEinkColorMode`. **E-Ink-Designsprache**: flach, 1.5px-Border, `BaseDialog`/bestehende Dropdown-Komponenten der Settings nutzen; Animationen über `LocalEinkMode` gaten (`animation-gating.md`). Label-Auflösung: bekannte ID → i18n, sonst `option.label`.

Vorlage: ein bestehender Settings-Content mit Dropdown (z. B. der Novel-Typo- oder ShellLayout-Picker) als strukturelles Muster.

- [ ] **Step 4: Sektion registrieren (alte „E-Ink Refresh" ersetzen)**

In `SettingsSections.kt`: den bisherigen Eintrag mit `settingsEinkRefresh` (Toggle „Refresh dem Gerät überlassen") **ersetzen** durch eine Sektion `settingsEinkDynamics`, deren `content = { EinkDynamicsSettingsContent(viewModel) }`, passendes Icon (bestehendes Refresh-/Eink-Icon aus `AppIcons`), `searchTerms` (refresh, eink, farbe, modus, color, mode).
**Sichtbarkeit:** Sektion nur listen, wenn `viewModel.refreshModes.isNotEmpty()` (Boox) — sonst auf Emulator/Nicht-Boox ausblenden (Muster: bestehende konditionale Sektionen).

- [ ] **Step 5: Build + i18n-Paritätstest**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest --tests "*Strings*"`
Expected: BUILD SUCCESSFUL; i18n DE/EN-Parität grün (falls ein solcher Test existiert).

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat(eink): Settings-Sektion 'E-Ink Dynamik' (Refresh/Farbe je Kontext)"
```

---

## Phase 5 — Alten Pfad entfernen

### Task 8: `Viewer.refreshScheduler` + Reader-Trigger + `OnyxRefresher`/`EinkReaderEffect`/`ReaderEinkHolder` raus

**Files (alle in `app/.../ui/reader/` + `eink-onyx/` + `app/di/`):**
- `Viewer.kt`, `ReaderScaffold.kt`, `ReaderViewModel.kt`, `ComicReaderViewModel.kt`, `NovelReaderViewModel.kt`, `PagedReaderScreen.kt`, `WebtoonReaderScreen.kt`, `ComicReaderScreen.kt`, `NovelReaderScreen.kt`, `ReaderRoute.kt`, `EinkReaderEffect.kt` (löschen), `ReaderEinkHolder.kt` (löschen), `OnyxRefresher.kt` (löschen), `AppModule.kt`

- [ ] **Step 1: `Viewer`-Vertrag bereinigen**

In `Viewer.kt` die Property `val refreshScheduler: RefreshScheduler` + ihren KDoc + den Import entfernen.

- [ ] **Step 2: Reader-VMs**

In `ReaderViewModel.kt`/`ComicReaderViewModel.kt`/`NovelReaderViewModel.kt`: das `refreshScheduler`-Feld (override) + alle `RefreshScheduler`-Erzeugung/Imports entfernen. Etwaige `reset()`/`onContentChange`-Aufrufe entfernen.

- [ ] **Step 3: Reader-Screens**

In `PagedReaderScreen.kt`/`WebtoonReaderScreen.kt`/`ComicReaderScreen.kt`/`NovelReaderScreen.kt`: alle Blöcke entfernen, die `refreshScheduler.onContentChange(...)` bzw. `refresher.fullRefreshNow/​fullRefreshIfNeeded(...)` aufrufen, samt der `OnyxRefresher`-Parameter/-Imports. (Refresh macht jetzt das Gerät per gewähltem EinkWise-Modus.) `ReaderScaffold.kt`: `RefreshScheduler`-Import/-Bezug entfernen.

- [ ] **Step 4: `ReaderRoute.kt`**

`EinkReaderEffect(refresher)`-Aufruf + `OnyxRefresher`/`ReaderEinkHolder`-Bezug + die `deviceManagedRefresh`-Spiegelung (`LaunchedEffect { refresher.deviceManaged = ... }`) entfernen. `EinkContextEffect(...)` (Task 6) bleibt.

- [ ] **Step 5: Dateien löschen + DI**

```bash
git rm app/src/main/kotlin/com/komgareader/app/ui/reader/EinkReaderEffect.kt app/src/main/kotlin/com/komgareader/app/ui/reader/ReaderEinkHolder.kt eink-onyx/src/main/kotlin/com/komgareader/eink/onyx/OnyxRefresher.kt
```
In `AppModule.kt`: die `OnyxRefresher`-Injektion/-Verkettung (`refresher.controller = controller`) im `einkController`-Provider entfernen; Provider gibt weiterhin `EinkController` zurück.

- [ ] **Step 6: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL (keine Referenz mehr auf entfernte Symbole).

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "refactor(eink): alten App-getriebenen Refresh-Pfad (Viewer.refreshScheduler/OnyxRefresher) entfernen"
```

---

### Task 9: `deviceManagedRefresh` end-to-end + `RefreshScheduler` + tote Onyx-Methoden raus

**Files:**
- `domain/.../repository/SettingsRepository.kt`, `data/.../repository/RoomSettingsRepository.kt` (+ Fakes)
- `domain/.../model/ReaderPreset.kt`, `domain/.../usecase/ApplyReaderPreset.kt`, `data/.../plugin/ReaderPresetParser.kt` (+ Tests)
- `app/.../settings/SettingsViewModel.kt`, `SettingsContent.kt`, `app/.../reader/ReaderViewModel.kt`
- `app/.../i18n/Strings.kt`, `MapBackedStrings.kt`
- `domain/.../eink/RefreshScheduler.kt` (löschen) + `domain/src/test/.../RefreshSchedulerTest.kt` (löschen)
- `eink-onyx/.../OnyxEinkController.kt` (tote Methoden)
- Tests: `ReaderViewModelTest.kt`, `SettingsViewModelTest.kt`, `ApplyReaderPresetTest.kt`, `ReaderPresetParserTest.kt`, `RoomSettingsRepositoryTest.kt`

- [ ] **Step 1: `deviceManagedRefresh` aus dem Settings-Vertrag entfernen**

In `SettingsRepository.kt`: `val deviceManagedRefresh` + `suspend fun setDeviceManagedRefresh` + Kommentar entfernen. In `RoomSettingsRepository.kt`: die override-Flow + Setter + `KEY_DEVICE_MANAGED_REFRESH` entfernen (der Room-Key bleibt als unbenutzte Zeile im Table — **kein** destruktiver Eingriff). Alle Fakes entsprechend bereinigen.

- [ ] **Step 2: ReaderPreset-Vertrag bereinigen**

In `ReaderPreset.kt`: `deviceManagedRefresh: Boolean? = null` aus `ReaderPresetOverrides` entfernen. In `ApplyReaderPreset.kt`: `setDeviceManagedRefresh` aus `ReaderPresetSink` + die `o.deviceManagedRefresh?.let(...)`-Zeile aus `applyReaderPreset` entfernen. In `ReaderPresetParser.kt`: das Auslesen des `deviceManagedRefresh`-Keys entfernen (unbekannter Key in altem Plugin-JSON wird dadurch ignoriert — kein Crash). Tests `ApplyReaderPresetTest.kt`/`ReaderPresetParserTest.kt` um die zugehörigen Assertions bereinigen.

> **Folge (im Handoff/Doku vermerkt):** Das ausgelieferte `komga-reader-preset-eink`-Plugin verliert sein einziges E-Ink-Feld; es parst weiter, dieses Feld ist dann wirkungslos. Per-Kontext-E-Ink-Felder für Reader-Presets sind späteres additives Soll.

- [ ] **Step 3: ViewModels + UI-Toggle**

In `SettingsViewModel.kt`/`ReaderViewModel.kt`: alle `deviceManagedRefresh`-Reads/-Setter/-Mirrors entfernen. In `SettingsContent.kt`: den Toggle „Refresh dem Gerät überlassen" entfernen (die Sektion ist bereits durch „E-Ink Dynamik" ersetzt, Task 7). i18n-Keys `settingsEinkRefresh` (+ Beschreibung) aus `Strings.kt`/`MapBackedStrings.kt` entfernen.

- [ ] **Step 4: `RefreshScheduler` löschen**

```bash
git rm domain/src/main/kotlin/com/komgareader/domain/eink/RefreshScheduler.kt domain/src/test/kotlin/com/komgareader/domain/eink/RefreshSchedulerTest.kt
```

- [ ] **Step 5: Tote Onyx-Methoden entfernen**

In `OnyxEinkController.kt` die jetzt ungenutzten `enterFastMode()`, `exitFastMode()`, `fullRefresh(view)`, `setViewFastMode(view)`, `resetViewMode(view)` + ungenutzte Imports (`android.view.View`, `UpdateMode`) entfernen. `setInverted`/`refresh`/`setContrast`/`capabilities` + neue Methoden bleiben.

- [ ] **Step 6: Build + volle Test-Suite der betroffenen Module**

Run: `./gradlew :domain:test :data:test :app:testDebugUnitTest :app:assembleDebug`
Expected: BUILD SUCCESSFUL, alle Unit-Tests grün.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "refactor(eink): deviceManagedRefresh + RefreshScheduler + tote Onyx-Methoden entfernen"
```

---

## Phase 6 — Verifikation & Doku

### Task 10: Emulator-Smoke, Doku-Nachzug

**Files:**
- `.claude/rules/architecture-seams.md`, `.claude/rules/big-picture-and-goals.md`, `docs/PROJECT-STATUS.md`

- [ ] **Step 1: Emulator-Smoke (NoOp-Pfad)**

Auf `eink_test`-AVD installieren, App starten: kein Crash; Settings → „E-Ink Dynamik" ist auf dem Emulator **ausgeblendet** (leere `refreshModes`). (Falls zum Testen der UI temporär sichtbar geschaltet: Auswahl persistiert über Neustart.)
Run: `./gradlew :app:installDebug` + manueller Smoke.

- [ ] **Step 2: Manuelle Boox-Verifikation (Pflicht, nicht emulierbar)**

Auf echter Onyx Boox Go Color 7 Gen2: je Kontext den gewählten Modus sichtbar prüfen — HD beim Manga-Lesen (kein GC-„Regal"-Sprung mehr), Speed beim Webtoon-Scroll, Farb-Toggle wirkt. Override in Settings ändern → wirkt sofort beim nächsten Kontext-Eintritt. **Erst nach sichtbarem Beweis als „fertig" melden** (`roadmap-and-invariants`).

- [ ] **Step 3: Doku-Nachzug (`komga-doc-sync`-Skill nutzen)**

`architecture-seams.md` (Naht B: neuer kontext-basierter EinkWise-Pfad ersetzt `RefreshScheduler`/`deviceManagedRefresh`; `EinkController` meldet Modi + Defaults), `big-picture-and-goals.md` (Refresh/Geräteklassen-Abschnitt), `PROJECT-STATUS.md` auf Ist ziehen. `Viewer`-Vertrag-Beschreibung (kein `refreshScheduler` mehr) anpassen.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "docs(eink): Naht-B-Refresh-Modell (kontext-basiert) auf Ist-Stand ziehen"
```

---

## Self-Review (durchgeführt)

- **Spec-Abdeckung:** Refresh-Achse (Task 2 Onyx-Mapping, Task 7 UI) ✓ · Farb-Achse (Task 2, Task 7) ✓ · dynamischer Deskriptor/Capabilities (Task 2) ✓ · 5 Kontexte (Task 1) ✓ · Controller-Defaults (Task 2) ✓ · Persistenz JSON-Blob (Tasks 3–4) ✓ · auto-Schalten Home/Reader (Tasks 5–6) ✓ · Altlast raus inkl. `deviceManagedRefresh` (Tasks 8–9) ✓ · Verifikation + Doku (Task 10) ✓.
- **Nicht in Spec, hier aufgedeckt:** `deviceManagedRefresh` ist Teil des READER_PRESET-Plugin-Vertrags → Task 9 Step 2 behandelt das explizit (Feld entfernen, Parser tolerant, Folge dokumentiert).
- **Typ-Konsistenz:** `EinkContextProfile`/`EinkModeOption`/`resolveEinkProfile`/`einkContextProfiles`/`applyRefreshMode`/`applyColorMode`/`defaultProfile`/`EinkContextController.applyFor`/`EinkContextEffect` durchgängig identisch benannt. ID-Konstanten (`hd/balanced/regal/speed/ultra`, `system/color/mono`) nur in `OnyxEinkController`.
- **Placeholder-Scan:** keine TBD/TODO; alle neuen pure Bausteine mit Volltext-Code; Entfernungen symbol-genau referenziert.
