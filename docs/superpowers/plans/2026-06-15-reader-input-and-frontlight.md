# Reader Input & Frontlight Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add device-native reader input on Onyx Boox — long-press volume buttons for Home and a manual anti-ghosting page refresh, plus an edge-swipe frontlight brightness bar — all behind the existing `EinkController` device seam so non-Onyx hardware degrades cleanly.

**Architecture:** Three additive changes on existing seams. (1) `ButtonEvent` gains a `PressKind`; `MainActivity` classifies short vs long volume presses; long presses route to Home/Refresh via a small shared `ReaderShortcutsViewModel`, short presses keep turning pages. (2) `EinkController` gains a real `refresh` and a `setBrightness`/`brightness` frontlight capability with an `EinkCapabilities.brightnessRange`; NoOp stays inert. (3) `DefaultReaderScaffold` renders two thin edge strips (host-owned, capability-gated) that open a discrete, no-animation brightness bar — kept out of `ReaderTapZones` so it never steals `HorizontalPager` swipes.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, kotlinx.coroutines Flow, Onyx `onyxsdk-device:1.3.5` (`EpdController`, FrontLight API), JUnit.

**Spec:** `docs/superpowers/specs/2026-06-14-reader-input-and-frontlight-design.md`

---

## Notes for the implementer

- **Onyx APIs need on-device verification.** The exact `EpdController` full-refresh call and the FrontLight setter/range in SDK 1.3.5 are given as concrete candidates wrapped in `runCatching`, matching the existing `OnyxEinkController` style. Mark them verified only after a Boox run. Emulator/NoOp paths are fully testable and must stay green.
- **`PressKind` default = `SHORT`** keeps every existing `ButtonEvent(...)` call source-compatible. Do not make it required.
- The shared long-press handling lives in ONE place (`ReaderShortcutsViewModel` + `ReaderRoute`), not per reader VM — see `shared-structure-before-variants.md`. Each reader VM only needs to *ignore* long presses so they don't turn pages.
- Brightness is host-enforced and capability-gated, exactly like the E-Ink scrim — it is NOT a per-screen `ReaderTapZones` action.

---

## Task 1: Add `PressKind` to `ButtonEvent`

**Files:**
- Modify: `domain/src/main/kotlin/com/komgareader/domain/eink/EinkController.kt:12-14`
- Test: `domain/src/test/kotlin/com/komgareader/domain/eink/ButtonEventTest.kt` (create)

- [ ] **Step 1: Write the failing test**

```kotlin
package com.komgareader.domain.eink

import org.junit.Assert.assertEquals
import org.junit.Test

class ButtonEventTest {
    @Test
    fun `press defaults to SHORT for source compatibility`() {
        val e = ButtonEvent(HardwareButton.PAGE_NEXT)
        assertEquals(PressKind.SHORT, e.press)
    }

    @Test
    fun `long press is carried explicitly`() {
        val e = ButtonEvent(HardwareButton.VOLUME_UP, PressKind.LONG)
        assertEquals(PressKind.LONG, e.press)
        assertEquals(HardwareButton.VOLUME_UP, e.button)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :domain:testDebugUnitTest --tests "com.komgareader.domain.eink.ButtonEventTest"`
Expected: FAIL — `PressKind` unresolved / `press` not a member.

- [ ] **Step 3: Write minimal implementation**

In `EinkController.kt`, replace the `ButtonEvent` line (currently `data class ButtonEvent(val button: HardwareButton)`) and add the enum above it:

```kotlin
/** Physische Geräte-Taste. */
enum class HardwareButton { PAGE_NEXT, PAGE_PREV, VOLUME_UP, VOLUME_DOWN }

/** Press duration class, so a held button can mean a different action than a tap. */
enum class PressKind { SHORT, LONG }

data class ButtonEvent(val button: HardwareButton, val press: PressKind = PressKind.SHORT)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :domain:testDebugUnitTest --tests "com.komgareader.domain.eink.ButtonEventTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add domain/src/main/kotlin/com/komgareader/domain/eink/EinkController.kt domain/src/test/kotlin/com/komgareader/domain/eink/ButtonEventTest.kt
git commit -m "feat(eink): add PressKind to ButtonEvent (default SHORT)"
```

---

## Task 2: Pure volume-key → `ButtonEvent` mapper

A pure function isolates the mapping so it is unit-testable; `MainActivity` only handles the Android key lifecycle.

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/eink/VolumeKeyMapper.kt`
- Test: `app/src/test/kotlin/com/komgareader/app/eink/VolumeKeyMapperTest.kt` (create)

- [ ] **Step 1: Write the failing test**

```kotlin
package com.komgareader.app.eink

import android.view.KeyEvent
import com.komgareader.domain.eink.ButtonEvent
import com.komgareader.domain.eink.HardwareButton
import com.komgareader.domain.eink.PressKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VolumeKeyMapperTest {
    @Test fun `volume up short turns page back`() =
        assertEquals(ButtonEvent(HardwareButton.PAGE_PREV, PressKind.SHORT),
            volumeButtonEvent(KeyEvent.KEYCODE_VOLUME_UP, longPress = false))

    @Test fun `volume down short turns page forward`() =
        assertEquals(ButtonEvent(HardwareButton.PAGE_NEXT, PressKind.SHORT),
            volumeButtonEvent(KeyEvent.KEYCODE_VOLUME_DOWN, longPress = false))

    @Test fun `volume up long is a long VOLUME_UP event`() =
        assertEquals(ButtonEvent(HardwareButton.VOLUME_UP, PressKind.LONG),
            volumeButtonEvent(KeyEvent.KEYCODE_VOLUME_UP, longPress = true))

    @Test fun `volume down long is a long VOLUME_DOWN event`() =
        assertEquals(ButtonEvent(HardwareButton.VOLUME_DOWN, PressKind.LONG),
            volumeButtonEvent(KeyEvent.KEYCODE_VOLUME_DOWN, longPress = true))

    @Test fun `other keys are ignored`() =
        assertNull(volumeButtonEvent(KeyEvent.KEYCODE_A, longPress = false))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.komgareader.app.eink.VolumeKeyMapperTest"`
Expected: FAIL — `volumeButtonEvent` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.komgareader.app.eink

import android.view.KeyEvent
import com.komgareader.domain.eink.ButtonEvent
import com.komgareader.domain.eink.HardwareButton
import com.komgareader.domain.eink.PressKind

/**
 * Pure mapping of a volume key + press duration to a [ButtonEvent].
 * Short presses keep the existing page-turn semantics (emitted as PAGE_PREV/PAGE_NEXT so the
 * reader's page-turn collector is unchanged). Long presses are emitted as the raw VOLUME_* button
 * with [PressKind.LONG] for the shortcut layer (Home / refresh). Returns null for unrelated keys.
 */
fun volumeButtonEvent(keyCode: Int, longPress: Boolean): ButtonEvent? = when (keyCode) {
    KeyEvent.KEYCODE_VOLUME_UP ->
        if (longPress) ButtonEvent(HardwareButton.VOLUME_UP, PressKind.LONG)
        else ButtonEvent(HardwareButton.PAGE_PREV, PressKind.SHORT)
    KeyEvent.KEYCODE_VOLUME_DOWN ->
        if (longPress) ButtonEvent(HardwareButton.VOLUME_DOWN, PressKind.LONG)
        else ButtonEvent(HardwareButton.PAGE_NEXT, PressKind.SHORT)
    else -> null
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.komgareader.app.eink.VolumeKeyMapperTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/eink/VolumeKeyMapper.kt app/src/test/kotlin/com/komgareader/app/eink/VolumeKeyMapperTest.kt
git commit -m "feat(eink): pure volume-key to ButtonEvent mapper (short/long)"
```

---

## Task 3: Long-press detection in `MainActivity`

Use the standard Android long-press lifecycle: `startTracking()` on the first down, `onKeyLongPress` fires the LONG event, `onKeyUp` fires the SHORT event unless a long press already consumed the gesture.

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/MainActivity.kt` (the `onKeyDown` override near lines 93-105; add `onKeyLongPress` + `onKeyUp`)

- [ ] **Step 1: Replace the `onKeyDown` override and add the two new overrides**

Current code (verbatim) to replace:

```kotlin
override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
    return when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP -> {
            buttonBus.emit(ButtonEvent(HardwareButton.PAGE_PREV))
            true
        }
        KeyEvent.KEYCODE_VOLUME_DOWN -> {
            buttonBus.emit(ButtonEvent(HardwareButton.PAGE_NEXT))
            true
        }
        else -> super.onKeyDown(keyCode, event)
    }
}
```

New code:

```kotlin
// Tracks volume keys whose gesture was already consumed by a long press, so the following
// key-up does not also emit a short (page-turn) event.
private val longPressConsumed = mutableSetOf<Int>()

override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
    return when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> {
            // First down of the gesture: enable long-press tracking so onKeyLongPress fires.
            if (event != null && event.repeatCount == 0) event.startTracking()
            true // consume; the actual emit happens on long-press or key-up
        }
        else -> super.onKeyDown(keyCode, event)
    }
}

override fun onKeyLongPress(keyCode: Int, event: KeyEvent?): Boolean {
    val emitted = com.komgareader.app.eink.volumeButtonEvent(keyCode, longPress = true)
    return if (emitted != null) {
        buttonBus.emit(emitted)
        longPressConsumed.add(keyCode)
        true
    } else {
        super.onKeyLongPress(keyCode, event)
    }
}

override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
    return when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> {
            if (longPressConsumed.remove(keyCode)) {
                true // long press already handled this gesture
            } else {
                com.komgareader.app.eink.volumeButtonEvent(keyCode, longPress = false)
                    ?.let { buttonBus.emit(it) }
                true
            }
        }
        else -> super.onKeyUp(keyCode, event)
    }
}
```

Ensure `mutableSetOf` needs no new import (Kotlin stdlib). Keep the existing `ButtonEvent`/`HardwareButton` imports (still referenced elsewhere in the file; if the build flags them unused after this change, remove them).

- [ ] **Step 2: Build to verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/MainActivity.kt
git commit -m "feat(input): classify short/long volume presses in MainActivity"
```

> Behavior note: page turns now fire on key-UP (release) rather than key-down. This is required to distinguish short from long. Verify on the Boox that paging still feels responsive (Task 12).

---

## Task 4: Reader VMs ignore long presses

Long-press events must not turn pages. Guard every reader VM that collects `bus.events`.

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/reader/ReaderViewModel.kt:236-268` (the `collectButtonEvents` function)
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/reader/NovelReaderViewModel.kt` (if it collects `bus.events` — grep first)
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/reader/ComicReaderViewModel.kt` (if it collects `bus.events` — grep first)
- Test: `app/src/test/kotlin/com/komgareader/app/ui/reader/ReaderViewModelTest.kt` (add one test)

- [ ] **Step 1: Locate all button collectors**

Run: `grep -rn "bus.events\|buttonEvents" app/src/main/kotlin/com/komgareader/app/ui/reader/`
For each file that collects the flow, apply the guard in Step 3.

- [ ] **Step 2: Write the failing test** (in `ReaderViewModelTest.kt`)

Mirror the existing test setup in that file (it already constructs a `ReaderViewModel` with a fake `HardwareButtonBus`/sources — reuse that harness). Add:

```kotlin
@Test
fun `long press events do not turn the page`() = runTest {
    val vm = newViewModelOnStreamedContent(pageCount = 5, startPage = 2) // use the file's existing helper
    bus.emit(ButtonEvent(HardwareButton.VOLUME_DOWN, PressKind.LONG))
    advanceUntilIdle()
    assertEquals(2, vm.currentPage.value) // unchanged
}
```

If the file lacks a `newViewModelOnStreamedContent` helper, construct the VM the same way the nearest existing page-turn test does and assert the page index is unchanged after a LONG emit.

- [ ] **Step 3: Add the guard** at the top of each collector's lambda. In `ReaderViewModel.collectButtonEvents`, immediately after `bus.events.collect { event ->`:

```kotlin
// Long presses are reader shortcuts (Home / refresh), handled by ReaderShortcutsViewModel.
if (event.press == PressKind.LONG) return@collect
```

Add the same line to the Novel/Comic collectors found in Step 1. Add the import `com.komgareader.domain.eink.PressKind` where missing.

- [ ] **Step 4: Run tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.komgareader.app.ui.reader.ReaderViewModelTest"`
Expected: PASS (new test green, existing page-turn tests still green).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/reader/*ReaderViewModel.kt app/src/test/kotlin/com/komgareader/app/ui/reader/ReaderViewModelTest.kt
git commit -m "feat(reader): ignore long-press button events for page turns"
```

---

## Task 5: Real manual full refresh on the device seam

`EinkController.refresh` becomes a real GC full refresh on Onyx; add a no-arg `manualFullRefresh()` on the existing `EinkContextController` shell so the reader can trigger it agnostically.

**Files:**
- Modify: `eink-onyx/src/main/kotlin/com/komgareader/eink/onyx/OnyxEinkController.kt:59-61` (`refresh` body)
- Modify: `app/src/main/kotlin/com/komgareader/app/data/EinkContextController.kt` (add `manualFullRefresh`)
- Test: `app/src/test/kotlin/com/komgareader/app/data/EinkContextControllerTest.kt` (create or extend)

- [ ] **Step 1: Write the failing test** — `manualFullRefresh` calls the controller with `RefreshMode.FULL`.

Use a fake `EinkController` recording calls. If `EinkContextControllerTest` exists, add to it; otherwise create it mirroring the constructor of `EinkContextController` (inject a fake `EinkController` + the settings it needs — copy the wiring from an existing test that builds it, or from `AppModule`).

```kotlin
@Test
fun `manualFullRefresh issues a FULL refresh`() = runTest {
    val fake = RecordingEinkController()
    val controller = EinkContextController(/* deps */, einkController = fake /* match real ctor */)
    controller.manualFullRefresh()
    assertEquals(RefreshMode.FULL, fake.lastRefreshMode)
}
```

`RecordingEinkController` implements `EinkController` with `var lastRefreshMode: RefreshMode? = null` set in `refresh(region, mode)`; all other members no-op (copy the NoOp shape).

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.komgareader.app.data.EinkContextControllerTest"`
Expected: FAIL — `manualFullRefresh` unresolved.

- [ ] **Step 3: Implement**

In `EinkContextController`, add (the class already holds the `EinkController` — reuse that field; name it as in the file):

```kotlin
/** User-triggered GC full refresh to clear E-Ink ghosting. No-op on non-E-Ink devices. */
fun manualFullRefresh() {
    controller.refresh(Region(0, 0, 0, 0), RefreshMode.FULL)
}
```

Add imports `com.komgareader.domain.eink.Region`, `com.komgareader.domain.eink.RefreshMode` if missing.

In `OnyxEinkController.refresh`, replace the log-only body with a real GC full refresh (candidate API — verify on device):

```kotlin
override fun refresh(region: Region, mode: RefreshMode) {
    if (mode != RefreshMode.FULL) {
        Log.d(TAG, "refresh($region, $mode) — non-full delegated to EinkWise context control")
        return
    }
    // Manual GC full update to clear ghosting. EpdController repaints the whole screen.
    runCatching { EpdController.repaintEveryThing(UpdateMode.GC) }
        .onFailure { Log.e(TAG, "repaintEveryThing(GC) failed", it) }
}
```

Add import `com.onyx.android.sdk.api.device.epd.UpdateMode`. If `repaintEveryThing`/`UpdateMode.GC` does not exist in SDK 1.3.5, use the documented equivalent (e.g. `EpdController.invalidate(window.decorView, UpdateMode.GC)`); keep it inside `runCatching`. NoOp controller already has an empty `refresh` — leave it.

- [ ] **Step 4: Run test + compile onyx module**

Run: `./gradlew :app:testDebugUnitTest --tests "com.komgareader.app.data.EinkContextControllerTest" :eink-onyx:compileDebugKotlin`
Expected: PASS + BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add eink-onyx/src/main/kotlin/com/komgareader/eink/onyx/OnyxEinkController.kt app/src/main/kotlin/com/komgareader/app/data/EinkContextController.kt app/src/test/kotlin/com/komgareader/app/data/EinkContextControllerTest.kt
git commit -m "feat(eink): real manual GC full refresh via EinkContextController"
```

---

## Task 6: Shared long-press shortcut layer (Home + Refresh)

One place handles long-press shortcuts for all reader types.

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/ui/reader/ReaderShortcutsViewModel.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/reader/ReaderRoute.kt` (collect home requests; pass `onHome`)
- Test: `app/src/test/kotlin/com/komgareader/app/ui/reader/ReaderShortcutsViewModelTest.kt` (create)

- [ ] **Step 1: Write the failing test**

```kotlin
package com.komgareader.app.ui.reader

import app.cash.turbine.test
import com.komgareader.app.eink.HardwareButtonBus
import com.komgareader.domain.eink.ButtonEvent
import com.komgareader.domain.eink.HardwareButton
import com.komgareader.domain.eink.PressKind
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderShortcutsViewModelTest {
    @Test fun `long volume up emits a home request`() = runTest {
        val bus = HardwareButtonBus()
        val eink = RecordingContextController() // fake exposing manualFullRefresh count
        val vm = ReaderShortcutsViewModel(bus, eink)
        vm.homeRequests.test {
            bus.emit(ButtonEvent(HardwareButton.VOLUME_UP, PressKind.LONG))
            awaitItem() // a Unit home request
        }
    }

    @Test fun `long volume down triggers a full refresh`() = runTest {
        val bus = HardwareButtonBus()
        val eink = RecordingContextController()
        val vm = ReaderShortcutsViewModel(bus, eink)
        bus.emit(ButtonEvent(HardwareButton.VOLUME_DOWN, PressKind.LONG))
        // allow the collector to run
        kotlinx.coroutines.test.advanceUntilIdle()
        assertEquals(1, eink.refreshCount)
    }
}
```

If `EinkContextController` is hard to fake (concrete class), introduce a minimal interface seam OR have `ReaderShortcutsViewModel` depend on a small functional type. Simplest: give `ReaderShortcutsViewModel` constructor param `private val onFullRefresh: () -> Unit` is NOT DI-friendly; instead inject `EinkContextController` and, for the test, subclass it if it is `open`, else extract an interface `ManualRefresher { fun manualFullRefresh() }` implemented by `EinkContextController`. Choose the interface route if the class is `final`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.komgareader.app.ui.reader.ReaderShortcutsViewModelTest"`
Expected: FAIL — class does not exist.

- [ ] **Step 3: Implement the ViewModel**

```kotlin
package com.komgareader.app.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komgareader.app.data.EinkContextController
import com.komgareader.app.eink.HardwareButtonBus
import com.komgareader.domain.eink.HardwareButton
import com.komgareader.domain.eink.PressKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Shared hardware long-press shortcut handler for every reader type (paged/webtoon/comic/novel):
 * a long VOLUME_UP goes Home, a long VOLUME_DOWN triggers a manual anti-ghosting full refresh.
 * Lives in one place (not per reader VM) per shared-structure-before-variants.
 */
@HiltViewModel
class ReaderShortcutsViewModel @Inject constructor(
    bus: HardwareButtonBus,
    private val eink: EinkContextController,
) : ViewModel() {
    private val _homeRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val homeRequests: SharedFlow<Unit> = _homeRequests

    init {
        viewModelScope.launch {
            bus.events.collect { event ->
                if (event.press != PressKind.LONG) return@collect
                when (event.button) {
                    HardwareButton.VOLUME_UP -> _homeRequests.tryEmit(Unit)
                    HardwareButton.VOLUME_DOWN -> eink.manualFullRefresh()
                    else -> {}
                }
            }
        }
    }
}
```

If you extracted a `ManualRefresher` interface in Step 1, depend on that here instead of the concrete class.

- [ ] **Step 4: Wire into `ReaderRoute`**

In `ReaderRoute.kt`, near the existing `EinkContextEffect(...)` call, add:

```kotlin
val shortcuts: ReaderShortcutsViewModel = hiltViewModel()
LaunchedEffect(Unit) {
    shortcuts.homeRequests.collect { onHome() }
}
```

`onHome` is already a parameter of the reader route (passed to the reader screens). Ensure `hiltViewModel` and `LaunchedEffect` are imported (they are used elsewhere in the file).

- [ ] **Step 5: Run tests + compile**

Run: `./gradlew :app:testDebugUnitTest --tests "com.komgareader.app.ui.reader.ReaderShortcutsViewModelTest" :app:compileDebugKotlin`
Expected: PASS + BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/reader/ReaderShortcutsViewModel.kt app/src/main/kotlin/com/komgareader/app/ui/reader/ReaderRoute.kt app/src/test/kotlin/com/komgareader/app/ui/reader/ReaderShortcutsViewModelTest.kt
git commit -m "feat(reader): shared long-press Home/Refresh shortcut layer"
```

---

## Task 7: Frontlight capability on `EinkController`

**Files:**
- Modify: `domain/src/main/kotlin/com/komgareader/domain/eink/EinkController.kt` (capability field + two methods)
- Modify: `app/src/main/kotlin/com/komgareader/app/eink/NoOpEinkController.kt`
- Modify: `eink-onyx/src/main/kotlin/com/komgareader/eink/onyx/OnyxEinkController.kt`
- Test: `domain/src/test/kotlin/com/komgareader/domain/eink/EinkCapabilitiesTest.kt` (create)

- [ ] **Step 1: Write the failing test**

```kotlin
package com.komgareader.domain.eink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EinkCapabilitiesTest {
    @Test fun `brightnessRange defaults to null (no frontlight)`() {
        val caps = EinkCapabilities(hasEink = false, canColor = true, canInvert = true)
        assertNull(caps.brightnessRange)
    }

    @Test fun `a frontlight device advertises a range`() {
        val caps = EinkCapabilities(
            hasEink = true, canColor = true, canInvert = true,
            brightnessRange = 0..255,
        )
        assertEquals(0..255, caps.brightnessRange)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :domain:testDebugUnitTest --tests "com.komgareader.domain.eink.EinkCapabilitiesTest"`
Expected: FAIL — `brightnessRange` not a member.

- [ ] **Step 3: Implement the interface changes**

In `EinkController.kt`, add the field to `EinkCapabilities` (after `colorModes`):

```kotlin
    /** Frontlight brightness range, or null if the device has no controllable frontlight. */
    val brightnessRange: IntRange? = null,
```

Add to the `EinkController` interface:

```kotlin
    /** Sets the frontlight brightness, clamped to [EinkCapabilities.brightnessRange]; no-op if null. */
    fun setBrightness(level: Int)

    /** Current frontlight brightness (0 if unsupported). */
    fun brightness(): Int
```

In `NoOpEinkController` add:

```kotlin
    override fun setBrightness(level: Int) { /* No-Op */ }
    override fun brightness(): Int = 0
```

(`capabilities` already omits `brightnessRange` → defaults to null. Good.)

In `OnyxEinkController`: set the range in `capabilities` (use a verified device value; `0..255` candidate) and implement the methods with the FrontLight API (candidate — verify on device):

```kotlin
    // inside the EinkCapabilities(...) construction, add:
    //     brightnessRange = 0..255,

    private var brightnessLevel = 0

    override fun setBrightness(level: Int) {
        val clamped = level.coerceIn(0, 255)
        runCatching {
            com.onyx.android.sdk.api.device.brightness.BrightnessController
                .setBrightness(appContext, clamped)
        }.onFailure { Log.e(TAG, "setBrightness($clamped) failed", it) }
        brightnessLevel = clamped
    }

    override fun brightness(): Int = brightnessLevel
```

> The exact Onyx frontlight class differs by SDK; SDK 1.3.5 candidates: `com.onyx.android.sdk.device.Device.currentDevice().setWarmLightDeviceValue(ctx, v)` / `setColdLightDeviceValue`, or a `BrightnessController`. The current `setContrast` stub already flags `FrontLightController` as the right home. Pick the one that resolves against the dependency and read the real max for the range; keep all calls in `runCatching`. `OnyxEinkController` currently takes `appPackageName: String` — if a `Context` is needed, add an `appContext: Context` constructor param and pass `ctx` from `AppModule.einkController` (it already has `@ApplicationContext ctx`).

- [ ] **Step 4: Run test + compile both impls**

Run: `./gradlew :domain:testDebugUnitTest --tests "com.komgareader.domain.eink.EinkCapabilitiesTest" :app:compileDebugKotlin :eink-onyx:compileDebugKotlin`
Expected: PASS + BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add domain/src/main/kotlin/com/komgareader/domain/eink/EinkController.kt app/src/main/kotlin/com/komgareader/app/eink/NoOpEinkController.kt eink-onyx/src/main/kotlin/com/komgareader/eink/onyx/OnyxEinkController.kt domain/src/test/kotlin/com/komgareader/domain/eink/EinkCapabilitiesTest.kt
git commit -m "feat(eink): frontlight brightness capability (range + setBrightness)"
```

---

## Task 8: Persist the brightness level

**Files:**
- Modify: `domain/src/main/kotlin/com/komgareader/domain/repository/SettingsRepository.kt` (add flow + setter)
- Modify: `data/src/main/kotlin/com/komgareader/data/repository/RoomSettingsRepository.kt` (impl + key)
- Test: `data/src/test/kotlin/com/komgareader/data/repository/RoomSettingsRepositoryTest.kt` (add one test, mirror an existing Int setting test)

- [ ] **Step 1: Write the failing test** — mirror the nearest existing numeric-setting round-trip test in that file:

```kotlin
@Test fun `frontlight level round-trips`() = runTest {
    repo.setFrontlightLevel(42)
    assertEquals(42, repo.frontlightLevel.first())
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :data:testDebugUnitTest --tests "com.komgareader.data.repository.RoomSettingsRepositoryTest"`
Expected: FAIL — `frontlightLevel`/`setFrontlightLevel` unresolved.

- [ ] **Step 3: Implement**

In `SettingsRepository.kt`:

```kotlin
    val frontlightLevel: Flow<Int>            // -1 = never set (use device current)
    suspend fun setFrontlightLevel(level: Int)
```

In `RoomSettingsRepository.kt` (follow the existing Int-setting pattern — observe + `toIntOrNull`):

```kotlin
    override val frontlightLevel: Flow<Int> =
        dao.observe(KEY_FRONTLIGHT_LEVEL).map { it?.toIntOrNull() ?: -1 }

    override suspend fun setFrontlightLevel(level: Int) =
        dao.put(SettingEntity(KEY_FRONTLIGHT_LEVEL, level.toString()))
```

and in the companion constants:

```kotlin
    const val KEY_FRONTLIGHT_LEVEL = "frontlight_level"
```

No Room migration needed (free-form key/value `SettingEntity`, like other settings).

- [ ] **Step 4: Run test**

Run: `./gradlew :data:testDebugUnitTest --tests "com.komgareader.data.repository.RoomSettingsRepositoryTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add domain/src/main/kotlin/com/komgareader/domain/repository/SettingsRepository.kt data/src/main/kotlin/com/komgareader/data/repository/RoomSettingsRepository.kt data/src/test/kotlin/com/komgareader/data/repository/RoomSettingsRepositoryTest.kt
git commit -m "feat(settings): persist frontlight brightness level"
```

---

## Task 9: Brightness bar UI component

A self-contained, host-rendered, discrete, no-animation vertical bar.

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/ui/reader/BrightnessBar.kt`
- Test: `app/src/test/kotlin/com/komgareader/app/ui/reader/BrightnessStepTest.kt` (create — pure step math)

- [ ] **Step 1: Write the failing test** for the pure level math (drag fraction → clamped level in N discrete steps):

```kotlin
package com.komgareader.app.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class BrightnessStepTest {
    @Test fun `top of bar is max`() =
        assertEquals(255, brightnessForFraction(yFractionFromTop = 0f, range = 0..255, steps = 16))

    @Test fun `bottom of bar is min`() =
        assertEquals(0, brightnessForFraction(yFractionFromTop = 1f, range = 0..255, steps = 16))

    @Test fun `middle snaps to a discrete step`() {
        val v = brightnessForFraction(yFractionFromTop = 0.5f, range = 0..255, steps = 16)
        assertEquals(128, v) // 8/16 of 255 rounded to the step grid
    }

    @Test fun `out of bounds clamps`() {
        assertEquals(255, brightnessForFraction(-0.2f, 0..255, 16))
        assertEquals(0, brightnessForFraction(1.4f, 0..255, 16))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.komgareader.app.ui.reader.BrightnessStepTest"`
Expected: FAIL — `brightnessForFraction` unresolved.

- [ ] **Step 3: Implement the pure function + the composable** in `BrightnessBar.kt`:

```kotlin
package com.komgareader.app.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Maps a vertical drag fraction (0 = top, 1 = bottom) to a brightness value snapped to a discrete
 * step grid — E-Ink-friendly (each step is one partial refresh, no continuous animation).
 */
fun brightnessForFraction(yFractionFromTop: Float, range: IntRange, steps: Int): Int {
    val fromBottom = (1f - yFractionFromTop).coerceIn(0f, 1f)
    val stepIndex = (fromBottom * steps).roundToInt().coerceIn(0, steps)
    val span = range.last - range.first
    return range.first + (span * stepIndex / steps)
}

/**
 * Host-rendered brightness bar anchored to a screen edge. Flat E-Ink look (border, no shadow/anim).
 * [onLevel] is called with each snapped level as the finger drags; [onDismiss] on tap outside.
 */
@Composable
fun BrightnessBar(
    level: Int,
    range: IntRange,
    alignment: Alignment,
    onLevel: (Int) -> Unit,
    onDismiss: () -> Unit,
    steps: Int = 16,
) {
    // Scrim: a tap anywhere outside the bar dismisses.
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectVerticalDragGestures(onDragStart = {}) { _, _ -> } }
    ) {
        Box(Modifier.fillMaxSize().pointerInput(Unit) {
            androidx.compose.foundation.gestures.detectTapGestures(onTap = { onDismiss() })
        })
        Box(
            Modifier
                .align(alignment)
                .width(56.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
                .border(1.5.dp, MaterialTheme.colorScheme.outline)
                .pointerInput(range) {
                    detectVerticalDragGestures { change, _ ->
                        val frac = change.position.y / size.height.toFloat()
                        onLevel(brightnessForFraction(frac, range, steps))
                    }
                },
        ) {
            // Fill indicator from bottom proportional to `level` (flat rectangle, no animation).
            val frac = if (range.last == range.first) 0f
                else (level - range.first).toFloat() / (range.last - range.first)
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(frac)
                    .background(MaterialTheme.colorScheme.onSurface),
            )
        }
    }
}
```

> Keep it flat per `eink-design-language` (1.5dp border, no elevation). No `animate*` calls — the fill is a direct height fraction, redrawn per drag step.

- [ ] **Step 4: Run test + compile**

Run: `./gradlew :app:testDebugUnitTest --tests "com.komgareader.app.ui.reader.BrightnessStepTest" :app:compileDebugKotlin`
Expected: PASS + BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/reader/BrightnessBar.kt app/src/test/kotlin/com/komgareader/app/ui/reader/BrightnessStepTest.kt
git commit -m "feat(reader): discrete host-rendered brightness bar component"
```

---

## Task 10: Edge strips + bar wiring in `DefaultReaderScaffold`

Two thin edge strips (capability-gated) detect an inward horizontal drag and open the bar. They are thin so they never steal `HorizontalPager` swipes in the center.

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/ui/reader/FrontlightHolder.kt` (Hilt holder exposing the controller to the host composable)
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/reader/ReaderScaffold.kt` (`DefaultReaderScaffold` — add the strips + bar)

- [ ] **Step 1: Create the Hilt holder** (the scaffold is a host composable; this gives it the `EinkController` + persisted level, like `EinkContextHolder`):

```kotlin
package com.komgareader.app.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.komgareader.domain.eink.EinkController
import com.komgareader.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FrontlightHolder @Inject constructor(
    private val controller: EinkController,
    private val settings: SettingsRepository,
) : ViewModel() {
    val brightnessRange: IntRange? = controller.capabilities.brightnessRange
    val level: StateFlow<Int> = settings.frontlightLevel
        .stateIn(viewModelScope, SharingStarted.Eagerly, -1)

    fun setLevel(value: Int) {
        controller.setBrightness(value)
        viewModelScope.launch { settings.setFrontlightLevel(value) }
    }
}
```

- [ ] **Step 2: Add the strips + bar to `DefaultReaderScaffold`**

Inside the outer `Box(Modifier.fillMaxSize().background(state.background))`, after the tap-zone block and before the chrome, add:

```kotlin
        // Frontlight edge strips — only on devices with a controllable frontlight. Thin so they
        // never steal central HorizontalPager swipes; an inward horizontal drag opens the bar.
        val frontlight: FrontlightHolder = hiltViewModel()
        val brightnessRange = frontlight.brightnessRange
        if (brightnessRange != null) {
            var barAlign by remember { mutableStateOf<Alignment?>(null) }
            val level by frontlight.level.collectAsState()
            val effectiveLevel = if (level < 0) brightnessRange.first else level

            // Left strip
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(24.dp)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { change, dragAmount ->
                            if (dragAmount > 0f) { barAlign = Alignment.CenterStart; change.consume() }
                        }
                    },
            )
            // Right strip
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(24.dp)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { change, dragAmount ->
                            if (dragAmount < 0f) { barAlign = Alignment.CenterEnd; change.consume() }
                        }
                    },
            )
            barAlign?.let { align ->
                BrightnessBar(
                    level = effectiveLevel,
                    range = brightnessRange,
                    alignment = align,
                    onLevel = { frontlight.setLevel(it) },
                    onDismiss = { barAlign = null },
                )
            }
        }
```

Add imports as needed: `androidx.hilt.navigation.compose.hiltViewModel`, `androidx.compose.foundation.gestures.detectHorizontalDragGestures`, `androidx.compose.foundation.layout.fillMaxHeight`, `androidx.compose.foundation.layout.width`, `androidx.compose.ui.unit.dp`, `androidx.compose.runtime.{mutableStateOf,remember,getValue,setValue,collectAsState}`, `androidx.compose.ui.Alignment`.

- [ ] **Step 3: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/reader/FrontlightHolder.kt app/src/main/kotlin/com/komgareader/app/ui/reader/ReaderScaffold.kt
git commit -m "feat(reader): edge-swipe frontlight strips + brightness bar in scaffold"
```

> On the emulator (NoOp controller) `brightnessRange` is null → strips and bar never appear, so emulator behavior is unchanged. Real behavior is Boox-only (Task 12).

---

## Task 11: Docs + spec sync (same commit discipline)

**Files:**
- Modify: `docs/superpowers/specs/2026-06-14-reader-input-and-frontlight-design.md` (replace the ReaderTapZones `EdgeSwipe` approach with the host edge-strip approach actually built)
- Modify: `CLAUDE.md` (Naht B: `ButtonEvent.press`, real `refresh`, frontlight capability)
- Modify: `.claude/rules/architecture-seams.md` (same Naht B notes)

- [ ] **Step 1: Update the spec** — in section "2. Touch gesture", replace the `ReaderTapZones.EdgeSwipe` design with: brightness lives entirely in `DefaultReaderScaffold` as two thin capability-gated edge strips + a host-rendered bar (kept out of `ReaderTapZones` so it cannot steal `HorizontalPager` swipes). Note `ReaderTapZones` was left unchanged.

- [ ] **Step 2: Update CLAUDE.md + architecture-seams.md** — under Naht B add a dated (2026-06-15) entry: `ButtonEvent` carries `PressKind`; `MainActivity` classifies short/long volume presses; long VOLUME_UP = Home, long VOLUME_DOWN = manual GC full refresh via `EinkContextController.manualFullRefresh()` (Onyx `refresh()` now real for `RefreshMode.FULL`); frontlight is a new `EinkController` capability (`setBrightness`/`brightness` + `EinkCapabilities.brightnessRange`, NoOp inert) surfaced as host-rendered edge strips/bar in `DefaultReaderScaffold`. State it agnostic: NoOp emits no LONG and advertises no `brightnessRange`.

- [ ] **Step 3: Commit**

```bash
git add docs/superpowers/specs/2026-06-14-reader-input-and-frontlight-design.md CLAUDE.md .claude/rules/architecture-seams.md
git commit -m "docs: sync reader input + frontlight seam changes (Naht B)"
```

---

## Task 12: Full verification

- [ ] **Step 1: Whole build + all unit tests**

Run: `./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all green.

- [ ] **Step 2: Emulator regression** (NoOp device — no frontlight, no LONG)

Install on the `eink_test` emulator, open a paged book: tap-thirds page/chrome still work; volume keys still page (now on release); no edge strips/bar appear. Confirm no regression.

- [ ] **Step 3: Boox device verification** (the hardware-gated parts)

On the real Onyx Boox Go Color 7 Gen2: short volume = page turn; long VOLUME_UP = Home; long VOLUME_DOWN = visible full refresh clearing ghosting; swipe inward from the left/right edge = brightness bar appears and the frontlight changes in discrete steps; level persists across reader re-open. Fix the Onyx candidate APIs (refresh, frontlight) if any failed to resolve/behave.

- [ ] **Step 4: Final commit if device fixes were needed**

```bash
git add -A && git commit -m "fix(eink): verified Onyx refresh + frontlight APIs on device"
```

---

## Self-Review Coverage

- Input map (volume short/long, edge-swipe): Tasks 2,3,4,6,9,10 ✓
- `ButtonEvent.press` additive, default SHORT: Task 1 ✓
- Real GC refresh, Onyx no-op replaced: Task 5 ✓
- Frontlight capability + NoOp inert + persistence: Tasks 7,8 ✓
- Brightness bar discrete, no animation, flat: Tasks 9,10 ✓
- Agnostic degradation (NoOp): Tasks 7,10, verified Task 12.2 ✓
- Shared (not per-VM) long-press layer: Task 6 ✓
- docs-match-code: Task 11 ✓
