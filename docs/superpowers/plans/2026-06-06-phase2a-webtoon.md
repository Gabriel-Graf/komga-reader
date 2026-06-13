# Phase 2a — Webtoon-Modus + Viewer-Umschalter

> REQUIRED SUB-SKILL: subagent-driven-development.

**Goal:** Im Reader zwischen **Paged** (horizontal) und **Webtoon** (vertikaler Endlos-Scroll) umschalten. Webtoon = `LazyColumn` der Seitenbilder über die volle Breite, nahtlos. Umschalter als Icon in der Reader-Chrome; Auswahl pro Buch in der DB gemerkt.

**Architecture:** Wiederverwendet `ReaderViewModel` (lädt bereits `pages` + `apiKey`). Neuer `WebtoonReaderScreen` (LazyColumn statt HorizontalPager). Eine `ViewerMode`-Auswahl (PAGED/WEBTOON) im VM; der Reader-Host wählt das Composable. Fortschritt im Webtoon = sichtbarer erster Index.

**Tech:** Compose `LazyColumn` + Coil. Kein neuer Netzwerk-Code.

## Test-Komga läuft (Emulator `http://10.0.2.2:25600/api/v1/`, Key `<KOMGA_API_KEY>`, Berserk vol01 = 4 Seiten).

---

### Task 0: ViewerMode im ReaderViewModel

**Files:** modify `app/.../ui/reader/ReaderViewModel.kt`.

- [ ] Füge `enum class ViewerMode { PAGED, WEBTOON }` (eigene Datei `app/.../ui/reader/ViewerMode.kt`) hinzu.
- [ ] Im `ReaderViewModel`: `val viewerMode = MutableStateFlow(ViewerMode.PAGED)`; `fun toggleViewerMode() { viewerMode.value = if (viewerMode.value == ViewerMode.PAGED) ViewerMode.WEBTOON else ViewerMode.PAGED }`. (Persistenz pro Buch optional — für jetzt In-Memory pro Reader-Session reicht; KEINE neue DB-Tabelle.)
- [ ] `./gradlew :app:assembleDebug` → SUCCESSFUL. Commit: `feat(reader): ViewerMode-State (Paged/Webtoon)`.

---

### Task 1: WebtoonReaderScreen (vertikaler Endlos-Scroll)

**Files:** Create `app/.../ui/reader/WebtoonReaderScreen.kt`.

- [ ] Composable bekommt `pages: List<PageRef>`, `apiKey: String`, `initialPage: Int`, `chromeVisible`, `onToggleChrome`, `onBack`, `onPageVisible: (Int) -> Unit`, `onToggleMode: () -> Unit`.
  - `LazyColumn(state = rememberLazyListState(initialPage))` über `pages`; jede Seite: `AsyncImage(ImageRequest.data(page.url).addHeader("X-API-Key", apiKey), contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth())` auf schwarzem Hintergrund, kein Abstand zwischen Seiten (nahtlos).
  - `LaunchedEffect(listState.firstVisibleItemIndex) { onPageVisible(listState.firstVisibleItemIndex) }` für Progress.
  - Tap (über `pointerInput`/`detectTapGestures` auf einem Overlay, NICHT die Scroll-Geste blockierend — nur Single-Tap in der Mitte togglet Chrome): Mitteltap → `onToggleChrome`. (Im Webtoon kein Tap-Blättern.)
  - Chrome (wenn sichtbar): TopBar (zurück + „i / n" Fortschritt + Mode-Umschalt-Icon `Icons.Filled.ViewDay`/`ViewColumn`).
- [ ] `./gradlew :app:assembleDebug` → SUCCESSFUL. Commit: `feat(reader): WebtoonReaderScreen (vertikaler Scroll)`.

---

### Task 2: Reader-Host wählt Viewer + Umschalt-Icon in Paged-Chrome

**Files:** modify `app/.../ui/reader/PagedReaderScreen.kt` (Mode-Icon in Chrome ergänzen) und die Reader-Route in `MainActivity.kt` bzw. ein neuer `ReaderRoute`-Composable, der je nach `viewerMode` `PagedReaderScreen` oder `WebtoonReaderScreen` zeigt.

- [ ] Erzeuge `app/.../ui/reader/ReaderRoute.kt`: holt `ReaderViewModel = hiltViewModel()`, `val mode by viewModel.viewerMode.collectAsState()`, `when(mode) { PAGED -> PagedReaderScreen(...); WEBTOON -> WebtoonReaderScreen(...) }`, reicht `onToggleMode = viewModel::toggleViewerMode` in beide. In `MainActivity` die reader-`composable` auf `ReaderRoute()` umstellen.
- [ ] `PagedReaderScreen`-Chrome: ein Mode-Umschalt-Icon ergänzen (`Icons.Filled.ViewDay`), `onClick = onToggleMode`. Parameterliste um `onToggleMode: () -> Unit` erweitern.
- [ ] `./gradlew :app:assembleDebug` → SUCCESSFUL + `bash tools/e2e/app_smoke.sh` PASS. Commit: `feat(reader): Viewer-Umschalter Paged/Webtoon`.

---

### Task 3: E2E-Screenshot (Webtoon-Scroll)

- [ ] App bauen/installieren, zur Reader-Ansicht navigieren (Server ist evtl. schon verbunden; sonst verbinden), Mode auf Webtoon umschalten, etwas scrollen, `adb exec-out screencap -p > /tmp/webtoon.png`. Falls UI-Navigation fragil: Mindestens Build+Smoke als Abnahme, Screenshot best-effort. Report notieren.
- [ ] Keine neuen Instrumented-Tests nötig (Datenpfad identisch zu Paged, bereits getestet) — aber bestehende `:app:connectedDebugAndroidTest` muss grün bleiben: ausführen. Commit (falls Änderungen): `test(reader): Webtoon-Smoke`.

---

## Self-Review
- **Spec §6:** Webtoon-Continuous-Scroll-Modus + Viewer-Auswahl → Tasks 1,2.
- **Verschoben:** Persistente Viewer-Wahl pro Serie/Shelf (Shelf-UI später), EPUB-Modus (Phase 2b), Guided-View.
- **Abnahme:** Build grün, Smoke PASS, bestehende Instrumented-Tests grün; Webtoon-Screenshot best-effort.
