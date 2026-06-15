# Animated Icons Implementation Plan

> **As-shipped reconciliation (2026-06-15):** `main` independently gained update download
> progress (`feat/update-flow-feedback`) during this work. Only the non-duplicated part shipped —
> **Task 4 (`AnimatedAppIcon`)** plus wiring the existing About update icons to it (refresh spins
> on `Checking`, download bobs on `installing`). Tasks 1–3 and the percent-display of Task 5 were
> **dropped as redundant** (main already has Float progress + a `"Lädt… NN %"` label). See the
> design doc's reconciliation note.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a central, E-Ink-gated `AnimatedAppIcon` building block and use it for the About-screen update flow — the refresh icon spins while checking, the download icon bobs while installing, and the install button shows the real download percentage left of the icon.

**Architecture:** One `AnimatedAppIcon(imageVector, animation, running, …)` component owns all motion and the `LocalEinkMode` gating (LCD = continuous; E-Ink = one bounded cycle then static), so every present/future icon animation reuses it (DRY). Real download progress is plumbed via an `onProgress` callback through `GithubReleaseClient.download` → `AppUpdateInstaller` → `AppUpdateViewModel.downloadProgress`. Pure helpers (`iconAnimationPlan`, `downloadPercent`) carry the testable policy/math.

**Tech Stack:** Kotlin, Jetpack Compose (`graphicsLayer`, `rememberInfiniteTransition`, `Animatable`), Hilt, OkHttp, JUnit (Jupiter in app/data).

**Spec:** `docs/superpowers/specs/2026-06-15-animated-icons-design.md`

---

## Notes for the implementer

- Work on branch `feat/animated-icons`. If `git status` shows unrelated uncommitted changes (a parallel session works in this repo), STOP and report — do not stage or revert them; only ever `git add` the exact files listed per task.
- Test tasks: `:data:testDebugUnitTest`, `:app:testDebugUnitTest`. Compile: `:app:compileDebugKotlin`.
- E-Ink design language: all motion must be gated; no `animate*` runs when `running` is false; no elevation/shadow. The central component is the only place motion lives.

---

## Task 1: Pure `downloadPercent`

**Files:**
- Create: `data/src/main/kotlin/com/komgareader/data/update/DownloadProgress.kt`
- Test: `data/src/test/kotlin/com/komgareader/data/update/DownloadProgressTest.kt`

- [ ] **Step 1: Write the failing test** (JUnit Jupiter — confirm via a neighboring data test):

```kotlin
package com.komgareader.data.update

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DownloadProgressTest {
    @Test fun `zero of known total is 0`() = assertEquals(0, downloadPercent(0, 1000))
    @Test fun `half is 50`() = assertEquals(50, downloadPercent(500, 1000))
    @Test fun `full is 100`() = assertEquals(100, downloadPercent(1000, 1000))
    @Test fun `unknown total is null`() { assertNull(downloadPercent(123, 0)); assertNull(downloadPercent(123, -1)) }
    @Test fun `clamps over 100`() = assertEquals(100, downloadPercent(1500, 1000))
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :data:testDebugUnitTest --tests "com.komgareader.data.update.DownloadProgressTest"`
Expected: FAIL — `downloadPercent` unresolved.

- [ ] **Step 3: Implement**

```kotlin
package com.komgareader.data.update

/** Percent (0..100) of [bytesRead] over [total], or null when [total] is unknown (<= 0). */
fun downloadPercent(bytesRead: Long, total: Long): Int? {
    if (total <= 0L) return null
    return ((bytesRead * 100) / total).toInt().coerceIn(0, 100)
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :data:testDebugUnitTest --tests "com.komgareader.data.update.DownloadProgressTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add data/src/main/kotlin/com/komgareader/data/update/DownloadProgress.kt data/src/test/kotlin/com/komgareader/data/update/DownloadProgressTest.kt
git commit -m "feat(update): pure downloadPercent helper"
```

---

## Task 2: Stream download with progress

**Files:**
- Modify: `data/src/main/kotlin/com/komgareader/data/update/GithubReleaseClient.kt` (`download`)
- Modify: `app/src/main/kotlin/com/komgareader/app/data/AppUpdateInstaller.kt` (`downloadAndInstall`)

- [ ] **Step 1: Add `onProgress` to `download`.** Replace the current method:

```kotlin
    /** Downloads [url] to [dest]; true on success. On failure [dest] is deleted. */
    suspend fun download(url: String, dest: File): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url(url).header("User-Agent", "komga-reader-app").build())
                .execute().use { resp ->
                    if (!resp.isSuccessful) return@use false
                    val body = resp.body ?: return@use false
                    dest.outputStream().use { out -> body.byteStream().copyTo(out) }
                    true
                }
        }.getOrElse { dest.delete(); false }
    }
```

with a manual streaming loop that reports percent:

```kotlin
    /**
     * Downloads [url] to [dest]; true on success. On failure [dest] is deleted.
     * [onProgress] is called with 0..100 as bytes stream in (only when the server reports a
     * content length; for unknown-length responses it is not called).
     */
    suspend fun download(url: String, dest: File, onProgress: (Int) -> Unit = {}): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                http.newCall(Request.Builder().url(url).header("User-Agent", "komga-reader-app").build())
                    .execute().use { resp ->
                        if (!resp.isSuccessful) return@use false
                        val body = resp.body ?: return@use false
                        val total = body.contentLength()
                        var read = 0L
                        val buffer = ByteArray(64 * 1024)
                        body.byteStream().use { input ->
                            dest.outputStream().use { out ->
                                while (true) {
                                    val n = input.read(buffer)
                                    if (n < 0) break
                                    out.write(buffer, 0, n)
                                    read += n
                                    downloadPercent(read, total)?.let(onProgress)
                                }
                            }
                        }
                        true
                    }
            }.getOrElse { dest.delete(); false }
        }
```

(`downloadPercent` is in the same package — no import needed.)

- [ ] **Step 2: Thread it through the installer.** In `AppUpdateInstaller.downloadAndInstall`, add the param and pass it:

```kotlin
    suspend fun downloadAndInstall(
        release: ReleaseInfo,
        onProgress: (Int) -> Unit = {},
    ): UpdateInstall = withContext(Dispatchers.IO) {
        val url = release.apkUrl ?: return@withContext UpdateInstall.NO_APK
        val dest = File(context.cacheDir, "update-${release.versionName}.apk")
        if (!client.download(url, dest, onProgress)) return@withContext UpdateInstall.FAILED
        when (apkSession.commit(dest)) {
            ApkSessionResult.STARTED -> UpdateInstall.STARTED
            ApkSessionResult.FAILED -> { dest.delete(); UpdateInstall.FAILED }
        }
    }
```

- [ ] **Step 3: Verify compile + existing data tests**

Run: `./gradlew :data:compileDebugKotlin :app:compileDebugKotlin :data:testDebugUnitTest`
Expected: BUILD SUCCESSFUL (existing callers use the default `onProgress = {}`, so unchanged).
Optional: if `:data` already has a MockWebServer test dependency (check `data/build.gradle.kts` / neighboring tests like the source-komga pattern), add a test serving a fixed-length body and asserting `onProgress` ends at 100. If MockWebServer is not already available in `:data`, do NOT add it — the pure `downloadPercent` test + E2E cover this; note the choice in your report.

- [ ] **Step 4: Commit**

```bash
git add data/src/main/kotlin/com/komgareader/data/update/GithubReleaseClient.kt app/src/main/kotlin/com/komgareader/app/data/AppUpdateInstaller.kt
git commit -m "feat(update): stream download with progress callback"
```

---

## Task 3: `AppUpdateViewModel.downloadProgress`

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/settings/AppUpdateViewModel.kt`

- [ ] **Step 1: Add the progress flow + wire it in `install`.** Add after the `_installing` flow:

```kotlin
    private val _downloadProgress = MutableStateFlow<Int?>(null)
    /** 0..100 while an install download runs; null when idle/unknown-length. */
    val downloadProgress: StateFlow<Int?> = _downloadProgress.asStateFlow()
```

Change `install` to reset to 0, feed the callback, and clear on completion:

```kotlin
    fun install(release: ReleaseInfo) = viewModelScope.launch {
        _installing.value = true
        _downloadProgress.value = 0
        try {
            installer.downloadAndInstall(release) { percent -> _downloadProgress.value = percent }
        } finally {
            _installing.value = false
            _downloadProgress.value = null
        }
    }
```

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

> No dedicated VM unit test: `AppUpdateViewModel` depends on the concrete `AppUpdateController` (StateFlow state, releaseNotes, version) and `AppUpdateInstaller`, which are not cheaply fakeable without an interface extraction that this small change does not warrant (YAGNI). The progress math is covered by `DownloadProgressTest` (Task 1) and the behavior by the E2E (Task 6). If you find an existing `AppUpdateViewModel` test with ready fakes, add a progress assertion there.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/settings/AppUpdateViewModel.kt
git commit -m "feat(update): expose download progress from the view model"
```

---

## Task 4: Central `AnimatedAppIcon`

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/ui/components/AnimatedAppIcon.kt`
- Test: `app/src/test/kotlin/com/komgareader/app/ui/components/IconAnimationPlanTest.kt`

- [ ] **Step 1: Write the failing test** for the pure policy:

```kotlin
package com.komgareader.app.ui.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IconAnimationPlanTest {
    @Test fun `lcd spin is continuous`() {
        val p = iconAnimationPlan(einkMode = false, IconAnimation.SpinClockwise)
        assertTrue(p.continuous); assertEquals(800, p.cycleMillis)
    }
    @Test fun `eink spin is a single fast turn`() {
        val p = iconAnimationPlan(einkMode = true, IconAnimation.SpinClockwise)
        assertFalse(p.continuous); assertEquals(400, p.cycleMillis)
    }
    @Test fun `lcd bob is continuous and slow`() {
        val p = iconAnimationPlan(einkMode = false, IconAnimation.BobVertical)
        assertTrue(p.continuous); assertEquals(1200, p.cycleMillis)
    }
    @Test fun `eink bob is a single bounded cycle`() {
        val p = iconAnimationPlan(einkMode = true, IconAnimation.BobVertical)
        assertFalse(p.continuous); assertEquals(600, p.cycleMillis)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.komgareader.app.ui.components.IconAnimationPlanTest"`
Expected: FAIL — unresolved.

- [ ] **Step 3: Implement `AnimatedAppIcon.kt`:**

```kotlin
package com.komgareader.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector

/** A reusable icon animation. New animations = a new variant + a branch in [iconAnimationPlan]. */
sealed interface IconAnimation {
    data object SpinClockwise : IconAnimation
    data object BobVertical : IconAnimation
}

/** Pure gating policy: LCD animates continuously; E-Ink plays one bounded cycle then holds. */
data class IconAnimationPlan(val continuous: Boolean, val cycleMillis: Int)

fun iconAnimationPlan(einkMode: Boolean, animation: IconAnimation): IconAnimationPlan {
    val cycle = when (animation) {
        IconAnimation.SpinClockwise -> if (einkMode) 400 else 800
        IconAnimation.BobVertical -> if (einkMode) 600 else 1200
    }
    return IconAnimationPlan(continuous = !einkMode, cycleMillis = cycle)
}

/**
 * Icon with an [animation] that runs while [running] is true. All motion is gated here by
 * [LocalEinkMode] — callers never decide motion. On LCD the animation loops; on E-Ink it plays a
 * single bounded cycle (one turn / one bob) then holds static. Nothing animates when [running] is
 * false. Flat (no elevation), per eink-design-language.
 */
@Composable
fun AnimatedAppIcon(
    imageVector: ImageVector,
    animation: IconAnimation,
    running: Boolean,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val eink = LocalEinkMode.current
    val plan = remember(eink, animation) { iconAnimationPlan(eink, animation) }

    // Continuous (LCD): loop, applying the value only while running. One-shot (E-Ink): a single
    // cycle on the rising edge. IMPORTANT: never call rememberInfiniteTransition conditionally on
    // `running` (Compose forbids conditional composable calls) — gate the VALUE, not the call.
    // `plan.continuous` is stable (eink mode is process-stable), so this branch is structurally fixed.
    val phase: Float = if (plan.continuous) {
        val transition = rememberInfiniteTransition(label = "iconAnim")
        val v by transition.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(plan.cycleMillis, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "iconPhase",
        )
        if (running) v else 0f
    } else {
        val anim = remember { Animatable(0f) }
        LaunchedEffect(running) {
            if (running) { anim.snapTo(0f); anim.animateTo(1f, tween(plan.cycleMillis, easing = LinearEasing)) }
            else anim.snapTo(0f)
        }
        anim.value
    }

    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier = modifier.graphicsLayer {
            when (animation) {
                IconAnimation.SpinClockwise -> rotationZ = phase * 360f
                IconAnimation.BobVertical -> {
                    // 0 -> down -> up -> 0 over one cycle; amplitude ~2.dp worth of px.
                    val amp = 2.dp.toPx()
                    translationY = kotlin.math.sin(phase * 2f * Math.PI).toFloat() * amp
                }
            }
        },
    )
}
```

Add the import `androidx.compose.ui.unit.dp`. `LocalEinkMode` is in this same package (`com.komgareader.app.ui.components`, defined in `LoadingIndicator.kt`) — no import needed.

- [ ] **Step 4: Run the test + compile**

Run: `./gradlew :app:testDebugUnitTest --tests "com.komgareader.app.ui.components.IconAnimationPlanTest" :app:compileDebugKotlin`
Expected: PASS + BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/components/AnimatedAppIcon.kt app/src/test/kotlin/com/komgareader/app/ui/components/IconAnimationPlanTest.kt
git commit -m "feat(ui): central E-Ink-gated AnimatedAppIcon"
```

---

## Task 5: i18n `percent` + wire both buttons

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/i18n/Strings.kt` (interface + StringsDe + StringsEn)
- Modify: `app/src/main/kotlin/com/komgareader/app/i18n/MapBackedStrings.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/settings/SettingsContent.kt` (the `UpdateArea`/`AboutContent` update buttons, ~lines 985-1012)

- [ ] **Step 1: Add the i18n helper.** In `Strings.kt` interface (near other formatted strings like `aboutUpdateAvailable`):

```kotlin
    fun percent(value: Int): String
```

`StringsDe` and `StringsEn` (identical, locale-neutral numeral + sign):

```kotlin
    override fun percent(value: Int) = "$value %"
```

`MapBackedStrings.kt`: delegate to the fallback (it's a formatted function, not a static string) — mirror how other `fun`-style i18n entries delegate there (e.g. `aboutUpdateAvailable`). If the map-backed class delegates functions straight to the EN fallback, add `override fun percent(value: Int) = fallback.percent(value)`.

- [ ] **Step 2: Wire the refresh button.** In `SettingsContent.kt`, in the `else`/check branch, replace `Icon(AppIcons.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))` with:

```kotlin
                AnimatedAppIcon(
                    imageVector = AppIcons.Refresh,
                    animation = IconAnimation.SpinClockwise,
                    running = state == AppUpdateState.Checking,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
```

- [ ] **Step 3: Wire the install button + percent.** Replace the `AppUpdateState.Available` install button block:

```kotlin
            EinkOutlinedButton(onClick = { onInstall(state.release) }, enabled = !installing) {
                Icon(AppIcons.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (installing) s.aboutDownloading else s.aboutInstallUpdate)
            }
```

with (percent to the LEFT of the bobbing icon):

```kotlin
            val progress by viewModel.downloadProgress.collectAsState()
            EinkOutlinedButton(onClick = { onInstall(state.release) }, enabled = !installing) {
                if (installing && progress != null) {
                    Text(s.percent(progress!!))
                    Spacer(Modifier.width(8.dp))
                }
                AnimatedAppIcon(
                    imageVector = AppIcons.Download,
                    animation = IconAnimation.BobVertical,
                    running = installing,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(if (installing) s.aboutDownloading else s.aboutInstallUpdate)
            }
```

Confirm the composable that holds this block has `viewModel: AppUpdateViewModel` in scope (the `UpdateArea`/`AboutContent` function — it already uses `installing`/`state`; if `installing` is passed in but the `viewModel` is not, collect `downloadProgress` where `installing` is collected and thread it in as a parameter the same way `installing` is). Add imports: `com.komgareader.app.ui.components.AnimatedAppIcon`, `com.komgareader.app.ui.components.IconAnimation`. Keep the `Icon` import (still used elsewhere).

- [ ] **Step 4: Compile** (enforces i18n parity)

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/i18n/Strings.kt app/src/main/kotlin/com/komgareader/app/i18n/MapBackedStrings.kt app/src/main/kotlin/com/komgareader/app/ui/settings/SettingsContent.kt
git commit -m "feat(update): animated refresh/download icons + download percent in About"
```

---

## Task 6: Docs + verification

**Files:**
- Modify: `.claude/rules/animation-gating.md`

- [ ] **Step 1: Document the central component.** Add a short bullet under the "Muster" or "Lade-Anzeige" section of `animation-gating.md`: icon animations go through `AnimatedAppIcon(imageVector, animation, running)` (`app/ui/components/AnimatedAppIcon.kt`) — the single gated home (LCD = continuous, E-Ink = one bounded cycle then static); `IconAnimation` is the additive vocabulary (`SpinClockwise`, `BobVertical`). Never animate an icon with a raw ungated `animate*`.

- [ ] **Step 2: Full unit suite**

Run: `./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all green (incl. `DownloadProgressTest`, `IconAnimationPlanTest`).

- [ ] **Step 3: Commit**

```bash
git add .claude/rules/animation-gating.md
git commit -m "docs: AnimatedAppIcon as the central gated icon-animation home"
```

- [ ] **Step 4: Emulator E2E.** Install (`./gradlew :app:installDebug`), open Settings → Über (About):
  1. Tap "Nach Updates suchen" → the refresh icon plays its rotation (on the E-Ink test AVD: one bounded fast turn; verify it does not spin forever).
  2. If an update is available, tap install → the download icon bobs and a percentage appears to the LEFT of the icon, rising toward 100 %.
  Report observations + a screenshot. (The check may report "up to date"; the icon one-shot is still observable on tap. The continuous-motion LCD path is verified by switching display mode to Smartphone in settings, if practical.)

---

## Self-Review Coverage

- Central DRY gated `AnimatedAppIcon` + `IconAnimation`: Task 4 ✓
- Gating policy pure + tested (LCD continuous / E-Ink one-shot, per-type durations): Task 4 ✓
- Refresh icon spins while Checking: Task 5 ✓
- Download icon bobs (whole icon) while installing: Task 5 ✓
- Real download percent, left of icon: Tasks 1-3, 5 ✓
- i18n percent (de+en+MapBacked): Task 5 ✓
- animation-gating.md updated: Task 6 ✓
- E-Ink invariant host-enforced at one point: Task 4 ✓
