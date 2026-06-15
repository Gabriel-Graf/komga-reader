# Animated Icons (central, E-Ink-gated) — Design

Date: 2026-06-15
Status: Shipped (reconciled — see note below)
Scope: one implementation plan.

> **As-shipped reconciliation (2026-06-15).** While this was being built, a parallel
> branch (`feat/update-flow-feedback`) independently landed update download progress on
> `main`: `AppUpdateViewModel.progress: StateFlow<Float?>`, `GithubReleaseClient.download(…,
> onProgress: (Float) -> Unit)`, a result/permission-gate, and a `"Lädt… NN %"` label in the
> install button. To avoid duplication, **only the non-overlapping part of this spec shipped**:
> the central `AnimatedAppIcon` + `IconAnimation` + pure `iconAnimationPlan`, wired to animate
> the existing About update icons (refresh spins while `Checking`, download bobs while
> `installing`). **Dropped as redundant:** the Int `downloadPercent`, an Int `onProgress` on the
> client, a VM `downloadProgress: StateFlow<Int?>`, and the "percent left of the icon" placement
> (the existing Float progress + `"Lädt… NN %"` label is kept). Sections 1 + the icon-usage parts
> of 2/3 describe what shipped; the progress-plumbing parts of section 3 did not.

## Goal

Give the About-screen update flow animated feedback, and do it through **one
central, reusable, device-class-gated** building block so future icon
animations elsewhere reuse it (DRY, agnostic):

- **Update / Refresh icon** ("Nach Updates suchen" button): the clockwise arrows
  rotate while a check runs (`AppUpdateState.Checking`).
- **Download icon** ("Update installieren" button): the cloud-download icon bobs
  gently down/up while the APK downloads, and the button shows the real
  **download percentage to the left of the icon**.

The E-Ink animation invariant (`animation-gating.md`) is **enforced once, in the
central component** — no caller decides motion vs. no-motion.

## 1. Central mechanism — `AnimatedAppIcon`

New file `app/src/main/kotlin/com/komgareader/app/ui/components/AnimatedAppIcon.kt`
(lives beside `LoadingIndicator.kt`, which already owns `LocalEinkMode`).

```kotlin
sealed interface IconAnimation {
    data object SpinClockwise : IconAnimation   // clockwise rotation
    data object BobVertical : IconAnimation     // gentle down/up translation
}

@Composable
fun AnimatedAppIcon(
    imageVector: ImageVector,
    animation: IconAnimation,
    running: Boolean,                 // animate while true (e.g. Checking / installing)
    contentDescription: String?,
    modifier: Modifier = Modifier,
)
```

**The one gating rule (host-enforced), uniform across animation types:**

- **LCD / Smartphone (`LocalEinkMode.current == false`, i.e. `allowsMotion`):**
  while `running`, animate **continuously** (`rememberInfiniteTransition`):
  SpinClockwise rotates 0→360 (~800 ms/turn, "a bit faster" than a default
  spinner); BobVertical oscillates Y 0→+2.dp→0 slowly (~1200 ms).
- **E-Ink (`LocalEinkMode.current == true`):** while `running`, play **one
  bounded cycle** on the rising edge (one ~400 ms clockwise turn; one ~600 ms
  down/up bob), then hold **static** for the rest of the run — no continuous
  motion, so no ghosting/partial-refresh churn. When `running` goes false, the
  icon is static at rest.

Implementation notes: rotation/translation applied via `Modifier.graphicsLayer`
(`rotationZ` / `translationY`). The E-Ink one-shot uses an `Animatable` launched
from a `LaunchedEffect(running)` that runs a single `animateTo` then stops. No
`animate*` runs when `running` is false on either device class.

**Pure, testable core:** a descriptor function isolates the policy from Compose:

```kotlin
data class IconAnimationPlan(val continuous: Boolean, val cycleMillis: Int)

fun iconAnimationPlan(einkMode: Boolean, animation: IconAnimation): IconAnimationPlan
// eink=false -> continuous=true with the per-type duration
// eink=true  -> continuous=false (one-shot) with the per-type duration
```

Future animations = a new `IconAnimation` variant + its plan; the gating logic
and all call sites stay unchanged.

## 2. Update / Refresh icon usage

In `AboutContent`'s update area (`SettingsContent.kt`, the
`EinkOutlinedButton(onClick = onCheck …)` branch), replace the static
`Icon(AppIcons.Refresh, …)` with:

```kotlin
AnimatedAppIcon(
    imageVector = AppIcons.Refresh,
    animation = IconAnimation.SpinClockwise,
    running = state == AppUpdateState.Checking,
    contentDescription = null,
    modifier = Modifier.size(18.dp),
)
```

The existing "Wird geprüft…" status line stays.

## 3. Download icon usage + percentage

### Plumb real download progress

`GithubReleaseClient.download` currently returns `Boolean` with no progress. Add
an optional progress callback (mirrors the existing `BrowsableSource.downloadFile(…, onProgress)` pattern):

```kotlin
suspend fun download(url: String, dest: File, onProgress: (Int) -> Unit = {}): Boolean
```

Compute percent from the HTTP content-length and bytes streamed. If
content-length is unknown (≤ 0), do not emit a percent (caller treats absent
percent as indeterminate). A pure helper guards the math:

```kotlin
fun downloadPercent(bytesRead: Long, total: Long): Int?   // null if total <= 0; else (0..100)
```

Thread it through `AppUpdateInstaller.downloadAndInstall(release, onProgress)` →
`AppUpdateViewModel`:

```kotlin
private val _downloadProgress = MutableStateFlow<Int?>(null)
val downloadProgress: StateFlow<Int?> = _downloadProgress.asStateFlow()
// install(): set _downloadProgress = 0 at start, update from onProgress, reset to null on completion/failure
```

### Button UI

In the `AppUpdateState.Available` branch's install `EinkOutlinedButton`:

```kotlin
val progress by viewModel.downloadProgress.collectAsState()
EinkOutlinedButton(onClick = { onInstall(state.release) }, enabled = !installing) {
    if (installing && progress != null) {
        Text(strings.percent(progress!!))   // e.g. "42 %", LEFT of the icon
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
    Text(if (installing) strings.aboutDownloading else strings.aboutInstallUpdate)
}
```

The bob applies to the **whole** download icon (cloud + arrow) — no custom
two-layer icon. i18n: a small `percent(value: Int): String` helper (de+en, both
`"$value %"`) keeps the visible number rule-compliant.

## E-Ink invariants (host-enforced)

All motion lives in `AnimatedAppIcon` and is gated by `LocalEinkMode`. No caller
introduces an ungated `animate*`. On E-Ink: bounded one-shot only, then static.
This is the `animation-gating.md` rule satisfied at one point for every present
and future icon animation.

## Testing

- **Pure unit:** `iconAnimationPlan(eink, animation)` (LCD→continuous, E-Ink→one-shot,
  correct per-type durations); `downloadPercent(bytesRead, total)` (0/50/100,
  total ≤ 0 → null, clamp).
- **VM unit:** `AppUpdateViewModel.downloadProgress` emits a rising sequence then
  resets to null after install completes (fake installer invoking onProgress).
- **E2E (emulator):** tap "Nach Updates suchen" → the refresh icon plays its
  (NoOp emulator = E-Ink default on the test AVD) one-shot turn; if an update is
  available, the install button shows a percent left of the bobbing download
  icon. (Continuous-motion path verified on an LCD/smartphone-mode build or by
  toggling display mode.)

## File structure

- `app/.../ui/components/AnimatedAppIcon.kt` (new) — `IconAnimation`,
  `AnimatedAppIcon`, pure `iconAnimationPlan`.
- `data/.../update/GithubReleaseClient.kt` — `download(…, onProgress)` + pure
  `downloadPercent`.
- `app/.../data/AppUpdateInstaller.kt` — thread `onProgress`.
- `app/.../ui/settings/AppUpdateViewModel.kt` — `downloadProgress` StateFlow.
- `app/.../ui/settings/SettingsContent.kt` — both buttons use `AnimatedAppIcon`;
  install button shows the percent.
- `app/.../i18n/Strings.kt` (+ `StringsDe`, `StringsEn`, `MapBackedStrings`) —
  `percent(value)`.

## Docs to update in the same commit (docs-match-code)

`.claude/rules/animation-gating.md` — add `AnimatedAppIcon` as the central,
gated home for icon animations (the place new icon animations go), alongside
`LoadingIndicator`.

## Out of scope (deferred)

- Animating only the arrow inside the cloud (would need a custom two-layer icon;
  whole-icon bob chosen instead).
- Moving `AnimatedAppIcon` into `:ui-api` (it depends on `LocalEinkMode`, which
  lives in `:app`); revisit if/when the eink-mode signal is lifted to `:ui-api`.
