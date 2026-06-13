# Phase 1 · Plan 4e/… — Reader (PagedViewer) + E-Ink-Buttons + Sync

> REQUIRED SUB-SKILL: subagent-driven-development / executing-plans.

**Goal:** Serie antippen → Buchliste → Buch öffnen → **paged Comic-Reader**: Seiten von Komga streamen, blättern per Tap-Zonen UND Hardware-Lautstärketasten, Chrome ein/aus per Mitteltap, Lesefortschritt zu Komga pushen/pullen. Verifiziert gegen die lokale Komga auf dem Emulator.

**Architecture-Entscheidung (wichtig):** Für **gestreamte** Komga-Comics liefert der Server die Seiten bereits als **fertige Bilder** (`/books/{id}/pages/{n}` → JPG). Der PagedViewer zeigt diese Bilder direkt via Coil (mit `X-API-Key`) — **kein MuPDF** beim Streaming (Bilder sind serverseitig schon gerastert). `:render-core`/MuPDF wird erst bei Download/lokalen Roh-Dateien und EPUB gebraucht (Phase 2). Der `Viewer` ist damit über einen „Seiten-Provider" abstrahiert: Streaming-Provider = Komga-Bild-URLs.

**Tech:** Compose `HorizontalPager` · Coil · Hilt-VM · KomgaSource (`pages`, `openPage`, `push/pullProgress`) · NoOp-EinkController + Volume-Key-Routing.

## Test-Komga (läuft): Emulator `http://10.0.2.2:25600/api/v1/`, Key `<KOMGA_API_KEY>`. Serie `Berserk` (id `0QKVPRDV0293Z`) → Buch `vol01` (id `0QKVPRDV42BFA`, 4 Seiten).

---

### Task 0: LibraryViewModel — Auto-Reload bei Verbindungsänderung (Bugfix)

**Files:** modify `app/.../ui/library/LibraryViewModel.kt`.

- [ ] **Step 1** Statt einmaligem `init { refresh() }`: `state` reaktiv aus `servers.config` ableiten, plus manueller Refresh-Trigger. Ersetze den State-Aufbau:
```kotlin
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel @Inject constructor(
    private val servers: ServerRepository,
    private val sourceProvider: KomgaSourceProvider,
) : ViewModel() {

    private val refreshTrigger = MutableStateFlow(0)

    val state: StateFlow<LibraryUiState> =
        combine(servers.config, refreshTrigger) { config, _ -> config }
            .flatMapLatest { config ->
                flow {
                    emit(LibraryUiState.Loading)
                    val source = sourceProvider.from(config)
                    if (config == null || source == null) { emit(LibraryUiState.NoServer); return@flow }
                    emit(runCatching { source.browse(0, SourceFilter()) }
                        .fold(
                            { LibraryUiState.Content(it.items, config.apiKey) },
                            { LibraryUiState.Error(it.message ?: "Verbindung fehlgeschlagen") },
                        ))
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState.Loading)

    fun refresh() { refreshTrigger.value++ }
}
```
(Import `kotlinx.coroutines.flow.combine`. Entferne den alten `_state`/`init`-Code.)
- [ ] **Step 2** `./gradlew :app:assembleDebug` → SUCCESSFUL. Commit: `fix(app): Bibliothek lädt reaktiv bei Server-Verbindung`.

---

### Task 1: NoOp-EinkController + Hardware-Tasten-Routing

**Files:** Create `app/.../eink/NoOpEinkController.kt`, `app/.../eink/HardwareButtons.kt`; modify `MainActivity.kt`.

- [ ] **Step 1** `HardwareButtons.kt` — ein App-weiter Bus für Tasten-Events:
```kotlin
package com.komgareader.app.eink

import com.komgareader.domain.eink.ButtonEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Verteilt physische Tasten-Events (Volume = Blättern) an den aktiven Reader. */
@Singleton
class HardwareButtonBus @Inject constructor() {
    private val _events = MutableSharedFlow<ButtonEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<ButtonEvent> = _events
    fun emit(event: ButtonEvent) { _events.tryEmit(event) }
}
```
`NoOpEinkController.kt`:
```kotlin
package com.komgareader.app.eink

import com.komgareader.domain.eink.ButtonEvent
import com.komgareader.domain.eink.EinkCapabilities
import com.komgareader.domain.eink.EinkController
import com.komgareader.domain.eink.RefreshMode
import com.komgareader.domain.eink.Region
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Fallback für Nicht-Boox-Geräte/Emulator: kein E-Ink-Steuern, Buttons aus dem Bus. */
class NoOpEinkController @Inject constructor(bus: HardwareButtonBus) : EinkController {
    override val capabilities = EinkCapabilities(hasEink = false, canColor = true, canInvert = true)
    override val buttonEvents: Flow<ButtonEvent> = bus.events
    override fun refresh(region: Region, mode: RefreshMode) { /* No-Op */ }
    override fun setContrast(level: Int) { /* No-Op */ }
    override fun setInverted(inverted: Boolean) { /* No-Op */ }
}
```
Hilt-Bindung: in einem `@Module @InstallIn(SingletonComponent::class)` (neu `app/.../di/AppModule.kt`) `@Provides @Singleton fun einkController(bus: HardwareButtonBus): EinkController = NoOpEinkController(bus)`.
- [ ] **Step 2** `MainActivity.kt`: `HardwareButtonBus` injizieren (`@Inject lateinit var buttonBus`), `onKeyDown` überschreiben — `KEYCODE_VOLUME_UP` → `ButtonEvent(HardwareButton.PAGE_PREV)`, `KEYCODE_VOLUME_DOWN` → `ButtonEvent(HardwareButton.PAGE_NEXT)`, `buttonBus.emit(...)`, `return true` um System-Lautstärke zu unterdrücken **während ein Reader aktiv ist** (für MVP immer schlucken ist ok). Sonst `super`.
- [ ] **Step 3** `./gradlew :app:assembleDebug` → SUCCESSFUL. Commit: `feat(app): NoOp-EinkController + Volume-Tasten-Bus`.

---

### Task 2: Navigation + Serien-Detail (Buchliste)

**Files:** Create `app/.../ui/series/SeriesDetailViewModel.kt`, `SeriesDetailScreen.kt`; modify `MainActivity.kt` (Routen), `LibraryScreen.kt` (Tap → Detail).

- [ ] **Step 1** Routen erweitern: `library` → `series/{seriesId}` → `reader/{bookId}/{pageCount}`. `LibraryScreen`: `SeriesCover` klickbar → `onOpenSeries(series.remoteId)`.
- [ ] **Step 2** `SeriesDetailViewModel(@HiltViewModel)` mit `SavedStateHandle` (seriesId) + ServerRepository + KomgaSourceProvider: lädt `source.books(seriesId)` → `StateFlow<List<Book>>` (+ apiKey). `SeriesDetailScreen`: TopBar (zurück + Serientitel), Liste der Bücher (Titel + Seitenzahl), Tap → `onOpenBook(book.remoteId, book.pageCount)`.
- [ ] **Step 3** `./gradlew :app:assembleDebug` → SUCCESSFUL. Commit: `feat(app): Serien-Detail mit Buchliste + Navigation`.

---

### Task 3: ReaderViewModel + PagedReaderScreen

**Files:** Create `app/.../ui/reader/ReaderViewModel.kt`, `PagedReaderScreen.kt`; modify `MainActivity.kt` (reader-Route).

- [ ] **Step 1** `ReaderViewModel(@HiltViewModel, SavedStateHandle bookId)` + ServerRepository + KomgaSourceProvider + HardwareButtonBus:
  - lädt `source.pages(bookId)` → `List<PageRef>` + apiKey; `pullProgress(bookId)` → Start-Seite.
  - State: `pages`, `apiKey`, `currentPage` (MutableStateFlow), `chromeVisible`.
  - `onPageSettled(index)`: `currentPage=index`; `viewModelScope.launch { source.pushProgress(bookId, ReadProgress(bookId=0, page=index+1, totalPages=pages.size, updatedAt=now)) }`. (now: `System.currentTimeMillis()`.)
  - sammelt `bus.events` → bei PAGE_NEXT/PAGE_PREV `currentPage` ±1 (geklemmt) und exponiert als Event/State, das der Pager beobachtet (z.B. ein `requestedPage: StateFlow<Int>`).
- [ ] **Step 2** `PagedReaderScreen`:
  - `HorizontalPager(state = rememberPagerState(initialPage, pageCount))`. Jede Seite: `AsyncImage(ImageRequest.data(pages[i].url).addHeader("X-API-Key", apiKey))`, `ContentScale.Fit`, schwarzer Hintergrund.
  - Tap-Gesten: linkes Drittel → vorige, rechtes Drittel → nächste, Mitte → Chrome toggeln (`pointerInput`/`detectTapGestures` mit Positions-Auswertung).
  - `LaunchedEffect(pagerState.currentPage)` → `viewModel.onPageSettled(...)`. `LaunchedEffect(viewModel.requestedPage)` → `pagerState.animateScrollToPage` (für Button-Navigation).
  - Chrome (sichtbar wenn `chromeVisible`): TopBar (zurück + Titel), unten Seitenzähler „i / n".
- [ ] **Step 3** reader-Route in MainActivity einhängen (`onOpenBook` navigiert dahin). `./gradlew :app:assembleDebug` → SUCCESSFUL + `bash tools/e2e/app_smoke.sh` PASS. Commit: `feat(app): PagedReader (Streaming, Tap+Button-Blättern, Progress-Sync)`.

---

### Task 4: Instrumented-E2E (Reader gegen echte Komga) + Screenshot

**Files:** Create `app/src/androidTest/kotlin/com/komgareader/app/ReaderFlowInstrumentedTest.kt`.

- [ ] **Step 1** Test (nutzt KomgaSourceProvider direkt gegen lokale Komga):
```kotlin
@Test fun laedt_seiten_und_synct_fortschritt() = runTest {
    val source = KomgaSourceProvider().from(ServerConfig(
        name = "T", baseUrl = "http://10.0.2.2:25600/api/v1/",
        apiKey = "<KOMGA_API_KEY>"))!!
    val books = source.books("0QKVPRDV0293Z")          // Berserk
    val book = books.first { it.remoteId == "0QKVPRDV42BFA" } // vol01
    val pages = source.pages(book.remoteId)
    assertEquals(4, pages.size)
    val bytes = source.openPage(pages.first())
    assertTrue(bytes.size > 1000)                       // echtes Bild
    source.pushProgress(book.remoteId, ReadProgress(bookId=0, page=2, totalPages=4, updatedAt=1))
    val pulled = source.pullProgress(book.remoteId)!!
    assertEquals(2, pulled.page)                        // Komga hat den Stand
}
```
- [ ] **Step 2** `docker start komga-test` (idempotent); `./gradlew :app:connectedDebugAndroidTest` → alle grün (LibraryFlow + ReaderFlow). 
- [ ] **Step 3 (Screenshot)** Per adb: App starten → (falls nötig Server verbinden, oder bestehende Verbindung nutzen) → Serie Berserk → Buch vol01 → Reader; `adb exec-out screencap -p > /tmp/reader.png`. Falls UI-Navigation zu fragil: überspringen, Instrumented-Test ist der Beweis. Report notieren.
- [ ] **Step 4** Commit: `test(app): Instrumented-E2E Reader laedt Seiten + synct Fortschritt`.

---

## Self-Review-Notiz
- **Spec-Abdeckung:** §6 PagedViewer + Tap/Button-Blättern + Chrome, §7 Progress-Sync (push/pull) → Tasks 1,3,4; Naht B Geräteseite (EinkController NoOp + Buttons) → Task 1; Auto-Reload-Bug → Task 0.
- **Bewusst verschoben:** MuPDF-PagedViewer für lokale/Download-Dateien + EPUB-/Webtoon-Viewer (Phase 2), Offline-Progress-Queue (jetzt direkter Push zu Komga), Onyx-SDK-Refresh (kein Boox-HW zum Testen), Custom-Slider statt Seitenzähler.
- **Abnahme:** Build grün, Smoke PASS, `connectedDebugAndroidTest` lädt echte Seiten + Progress-Roundtrip gegen lokale Komga.
