# Reader Input & Frontlight — Design

Date: 2026-06-14
Status: Approved (brainstorming)
Scope: one implementation plan.

## Goal

Give the reader a fast, device-native input layer on the Onyx Boox: hardware
volume buttons drive page turns plus two long-press shortcuts (Home, page
refresh against ghosting), and an edge-swipe from the left/right screen edge
opens a frontlight brightness bar. Everything is built **behind the existing
device seam (`EinkController`)** and the **declarative reader-chrome surface
(`ReaderTapZones`)** so it degrades cleanly on non-Onyx hardware.

This merges the originally separate ideas "A" (reader gestures) and "D"
(frontlight brightness) because they share one concern: reader input routed
agnostically through the device seam.

## Input map

| Input | Action | Status |
|---|---|---|
| Volume short ↑/↓ | Page forward / back | existing |
| Volume button A, **long** | Home | new |
| Volume button B, **long** | Page refresh (full GC, anti-ghosting) | new |
| Swipe from **left/right** edge toward center | Open brightness bar | new |
| Tap thirds (left/center/right) | prev / toggle chrome / next | existing |

Reserved: vertical swipes (top/bottom) and screen long-press stay **unused** —
explicitly free for later features. The edge-swipe applies only to the left and
right side edges, never top/bottom.

Which physical volume button is "A" (Home) vs "B" (Refresh) is decided in the
plan; default: Volume-Up long = Home, Volume-Down long = Refresh.

## Architecture: three additive seam homes

The whole feature splits across three existing seams. **No new aggregate
"gesture class", and nothing lives in the `overlay` slot** (the overlay is only
the toggled menu bar). Mixing hardware input, touch geometry, and a device
capability into one class would blur three seams — exactly what the architecture
forbids.

### 1. Hardware buttons → device seam (`EinkController.buttonEvents`)

The seam already carries buttons agnostically:

```kotlin
enum class HardwareButton { PAGE_NEXT, PAGE_PREV, VOLUME_UP, VOLUME_DOWN }
data class ButtonEvent(val button: HardwareButton)            // existing
val buttonEvents: Flow<ButtonEvent>                            // existing
```

Additive change: a press kind.

```kotlin
enum class PressKind { SHORT, LONG }
data class ButtonEvent(val button: HardwareButton, val press: PressKind = PressKind.SHORT)
```

- `MainActivity` already intercepts volume `KeyEvent`s and emits `ButtonEvent`
  on a `buttonBus`. Extend it to measure press duration: on `ACTION_DOWN` record
  the time (or watch `getRepeatCount()`); on `ACTION_UP` classify SHORT vs LONG
  against a threshold (~500 ms) and emit the right `PressKind`. A LONG press
  must **not** also fire a SHORT page turn.
- The reader collects `buttonEvents` and maps `(button, press)` → action:
  SHORT → page turn (existing), `(VOLUME_UP, LONG)` → Home,
  `(VOLUME_DOWN, LONG)` → refresh.
- Default field value keeps existing emitters/tests source-compatible. A NoOp
  device never emits LONG, so the shortcuts simply don't exist there — graceful
  degradation, no branching on device type.

### 2. Touch gesture → declarative reader-chrome (`ReaderTapZones`)

`ReaderTapZones` is the host-owned, declarative tap surface (geometry = host,
action = data; sub-project A1b). Extend the sealed vocabulary additively:

```kotlin
sealed interface ReaderTapZones {
    data class HorizontalThirds(...) : ReaderTapZones        // existing
    data class EdgeSwipe(                                     // new
        val onLeftEdge: () -> Unit,
        val onRightEdge: () -> Unit,
        val thirds: HorizontalThirds,   // taps still dispatch to thirds
    ) : ReaderTapZones
}
```

- The host (`DefaultReaderScaffold`) owns recognition: a drag that **starts
  within an edge margin (~8% of width)** and moves horizontally toward center is
  an edge swipe → `onLeftEdge`/`onRightEdge`; anything else falls through to the
  tap-thirds dispatch (prev/chrome/next). Pointer handling stays keyed on `Unit`
  with `rememberUpdatedState`, as today, so page turns don't restart the
  recognizer.
- The pure dispatch (edge vs third from a normalized x-fraction + drag flag) is
  unit-testable without Compose, like the existing `ReaderTapZonesTest`.
- Comic keeps `tapZones = null` (own hit-test) — unaffected.

### 3. Frontlight → device-seam capability (`EinkController`)

New capability, additive:

```kotlin
data class EinkCapabilities(
    ...,
    val brightnessRange: IntRange? = null,   // null = no frontlight; UI hides bar + gesture
)

interface EinkController {
    ...
    fun setBrightness(level: Int)            // clamped to brightnessRange; no-op if null
    fun brightness(): Int                    // current level (for restoring the bar)
}
```

- Onyx impl drives the SDK FrontLight controller (the existing `setContrast`
  stub already notes "FrontLightController wäre der richtige Ort"). Warm/cold
  split is optional and can be deferred; v1 = single brightness level.
- NoOp impl: `brightnessRange = null`, `setBrightness` no-op. The brightness bar
  and edge-swipe are then hidden — agnostic by construction.

## Brightness bar (host-rendered)

- A vertical bar anchored to the swiped edge, host-rendered (not a slot in v1),
  shown only when `brightnessRange != null`.
- **E-Ink behavior:** discrete steps, not a continuous drag — each step is a
  partial refresh; no animation (host-gated via `LocalEinkMode`/`allowsMotion`).
  Dragging the finger moves between discrete steps.
- Calls `EinkController.setBrightness(level)`; the chosen level is persisted in
  settings so it survives restart. A tap outside the bar dismisses it.

## E-Ink invariants

- The refresh action is a real GC full refresh. `OnyxEinkController.refresh()` is
  currently a no-op ("delegated to EinkWise context control") — implement the
  manual full-refresh path so the user can actively clear ghosting.
- No animations anywhere (bar, dismiss) — all host-gated per `animation-gating`.

## Agnostic / fallback summary

| Device | Long-press shortcuts | Edge-swipe + bar | Refresh |
|---|---|---|---|
| Onyx (E-Ink) | yes | yes | real GC refresh |
| Non-Onyx (NoOp) | no LONG emitted → none | hidden (no `brightnessRange`) | n/a (no ghosting) |

Home always remains reachable via the existing overlay Home button; the
hardware long-press is only a shortcut.

## Testing

- **Pure unit:** press-kind threshold classification; `EdgeSwipe` dispatch
  (edge vs third); brightness clamp to range.
- **E2E:** emulator — edge-swipe opens the bar, steps change a fake brightness;
  Boox device — volume long-press Home/Refresh and real frontlight verified by
  hand (hardware-gated, like other Onyx behaviors).

## Out of scope (deferred)

- Warm/cold light split (v1 = single level).
- Making the brightness bar a swappable UI slot.
- Screen long-press and vertical swipes (kept free on purpose).
- Bookmark system (separate "C" spec).

## Docs to update in the same commit (docs-match-code)

`CLAUDE.md` + `.claude/rules/architecture-seams.md` (Naht B: new
`ButtonEvent.press`, frontlight capability, real `refresh()`), and the
`ReaderTapZones` note for the new `EdgeSwipe` case.
