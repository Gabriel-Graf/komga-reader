# Architektur: Die zwei Nähte

Das ganze Projekt ruht auf **zwei tragenden Nähten**, die die gesamte Variabilität kapseln.
Über den Nähten ist alles quellen- und geräte-agnostisch. Eine neue Quelle oder ein neues
E-Ink-Gerät = **neue Implementierung hinter dem Interface, kein Kern-Umbau**. Das ist die
zentrale Design-Entscheidung (Spec §3) — sie darf nie aufgeweicht werden.

```
┌─ UI (Compose, app) — quellen-/geräte-agnostisch ───────────┐
└──────────────── ↓ ──────────────────────── ↓ ─────────────┘
┌─ Domain (kennt weder UI noch Daten noch Quelle) ───────────┐
│  Modelle · UseCases · Repository-Interfaces · ViewerType   │
└──────── ↓ NAHT A: Quellen ──────────── ↓ NAHT B: Engine ───┘
┌─ MediaSource ───────────┐   ┌─ Document (Render) ───────────┐
│ KomgaSource (REST)      │   │ MuPDF (C++/JNI) → Bitmap      │
│ OpdsSource              │   │ OnyxRefresher (E-Ink-Refresh) │
│ [LocalSource, Plugins…] │   │ EinkController (Onyx ⟂ No-Op) │
│ SourceManager + Stub     │  │ Compose-Reader-Screens        │
└─────────────────────────┘   └───────────────────────────────┘
```

> **Soll vs. Ist — diese Regel trennt beides.** Verbindliche Regeln dürfen keine
> nicht-existierenden Typen als real darstellen (sonst baut der nächste — Mensch
> oder Agent — gegen ein Phantom, siehe `docs-match-code` in der Big-Picture-Doku).
> Das Naht-**Design** (unten) ist verbindlich; wo der **Ist-Stand** abweicht, steht
> es explizit dabei. Vor dem Bauen den Ist-Stand per `grep` verifizieren, nicht der
> Vokabel der Doku vertrauen.

## Naht A — Quellen (`source-api/…/source/MediaSource.kt`)

- Jede Backend-Verbindung implementiert `MediaSource` (+ `BrowsableSource` zum Lesen,
  `SyncingSource` für Fortschritts-Sync). Stabile, deterministische `id` (Hash aus name/typ/config).
- `StubSource` hält Titel/ID, wenn die echte Quelle fehlt — die Bibliothek bricht nie.
- **Regel:** UI und `domain` kennen **nur** `MediaSource`-Interfaces, **nie** `KomgaSource`/`OpdsSource`
  konkret. Wer in `app` oder `domain` `import …source.komga…` schreibt, hat die Naht verletzt.
- Quellen-übergreifende DB (`data`): jeder Datensatz trägt `sourceId`. `LocalSource` = id 0.
- Runtime-Plugin-Loader (Phase 4) hängt sich genau hier ein — das Interface ist dafür schon stabil,
  also keine quellenspezifischen Annahmen ins Interface backen.
- **Ist-Stand (2026-06-08): die Integrationsseite ist verdrahtet.** `SourceManager` wird in `app`
  über `SourceRegistration` aus der `ServerConfig` befüllt; `ActiveSource` (app/data) ist der
  agnostische Resolver für alle ViewModels. Bilder/Seiten **und** Cover fließen über die Naht
  (Coil `SourcePageFetcher`/`SourceCoverFetcher` → `BrowsableSource.openPage`/`coverBytes`); es gibt
  **kein** `AuthHeaders` mehr und keine quellen-spezifische URL/Auth in der UI. `BrowsableSource`
  trägt jetzt `downloadFile(…, onProgress)`, `seriesIdOf`, `coverBytes`. `KomgaSource`-Typen leben
  nur noch in `ActiveSource`/`SourceRegistration`/`KomgaSourceProvider`.
- **Multi-Source verdrahtet (2026-06-09, #7 P2/P3):** N Quellen gleichzeitig, gemischt.
  `ActiveSource` bietet `all()` (Aggregation) + `get(sourceId)` (genau die Quelle eines Werks);
  `current()` bleibt nur Übergangs-API. Die `sourceId` jedes Werks wird **durch die Navigation
  gefädelt** (`series/{seriesId}/{sourceId}`, `reader/{bookId}/{sourceId}/…`), und alle Consumer
  lösen **pro Werk** über `get(item.sourceId)` auf statt „die erste/aktive" (`LibraryViewModel`
  aggregiert über `all()`; `SeriesDetail`/`Reader`/`Novel`/`GroupBrowse`/`Groups` via `get`).
  Settings verwaltet eine **Server-Liste** (Hinzufügen + Einzel-Entfernen). Emulator-verifiziert.
  **Ist (2026-06-09): OPDS als zweite Live-Quelle live gemischt verifiziert.** Komga-REST + OPDS
  gleichzeitig registriert, `ActiveSource.all()` liefert beide, OPDS-Werk via `downloadFile`
  geladen — bewiesen durch `MixedSourcesLiveTest` auf dem Emulator. `OpdsSource` trägt jetzt
  Basic-Auth-Credentials (Komga OPDS akzeptiert keinen `X-API-Key`-Header). Details/Integrationsregel:
  `source-agnostic-integration.md`.

## Naht B — Render & E-Ink (`render-core`, `eink-onyx`)

- **Render-Naht (Ist):** `Document`/`DocumentFactory` (`domain/render/Document.kt`) ist die
  Render-Naht: **MuPDF** rendert cbz/cbr/pdf **und** EPUB in eine `android.graphics.Bitmap`
  (`MupdfDocument` in `render-core`). Render-Target strikt von der View getrennt.
  Reicht MuPDFs Qualität nicht, klinkt sich eine andere Engine (crengine, Roman-Reflow) hinter
  `Document`/`ReflowableDocument` ein (Phase 4 / `novel-reflow-reader`) — ohne den Rest zu berühren.
- **Geräte-Naht (Ist):** `EinkController` (`domain/eink/EinkController.kt`) kapselt das Gerät:
  `OnyxEinkController` (Boox-SDK, **HW-gated** über `Build.MANUFACTURER`), `NoOpEinkController` als
  Fallback. **Entwicklung crasht nie auf Nicht-Boox-HW.** Trägt `EinkCapabilities` (hasEink/canColor/
  canInvert) — siehe Big-Picture-Doku zur Geräteklassen-Frage.
- **E-Ink-Refresh (Ist, 2026-06-08):** Die Refresh-**Entscheidung** (PARTIAL beim Blättern,
  FULL-Promotion gegen Ghosting / bei bewusstem Bildwechsel) liegt jetzt im geräteunabhängigen,
  pur-getesteten **`RefreshScheduler`** (`domain/eink`, Event-Zählung statt Index-Modulo +
  `mergeRegions`). Die **Ausführung** macht weiter `OnyxRefresher` (`eink-onyx`, gerätenah) +
  `EinkReaderEffect`. Eine Scheduler-Instanz pro Reader-Sitzung, von allen Readern über den
  `Viewer`-Vertrag geteilt. (`triggerGhostClearIfNeeded` ist entfernt.)
  **Update (2026-06-09): `RefreshScheduler` ist `@Deprecated` + standardmäßig wirkungslos.** Die
  Einstellung `deviceManagedRefresh` (Default **an**) überlässt den Voll-Refresh dem Onyx-Gerät —
  `OnyxRefresher.deviceManaged` macht dann `fullRefreshNow`/`fullRefreshIfNeeded` zu No-Ops (der
  Fast-Modus `enterFastMode` bleibt aktiv). Der Scheduler läuft nur noch als Fallback, wenn der
  Toggle (Settings → Reader → „E-Ink-Refresh") ausgeschaltet wird; dann blättern alle Reader
  partial mit periodischer GC-Promotion (auch der Roman-Reader — der erzwang vorher pro Seite FULL).
  Mittelfristig entfernen.
- **Reader / Viewer-Naht (Ist, 2026-06-08):** Es gibt den **`Viewer`**-Vertrag
  (`app/ui/reader/Viewer.kt`) — eine **Compose-Zustands**-Naht (chromeVisible-`StateFlow`,
  `toggleChrome`/`navigateTo`/`onPageSettled`, `refreshScheduler`), **nicht** das alte OO-`bind/
  onButton/teardown` (Compose verwaltet den Lifecycle deklarativ). Alle Reader-VMs
  (`ReaderViewModel`, `ComicReaderViewModel`, `NovelReaderViewModel`) implementieren ihn; das
  geteilte `ReaderScaffold` arbeitet dagegen. Reader bleiben eigene `@Composable`-Screens
  (`PagedReaderScreen`/`WebtoonReaderScreen`/`ComicReaderScreen`/`NovelReaderScreen`), dispatcht
  per `when(ViewerMode)`/`when(ReaderContent)` in `ReaderRoute.kt` — ein 5. Reader/UI-Plugin
  implementiert **`Viewer`** statt einer Parallel-Linie.
- **God-VM-Split (Ist, 2026-06-09 — teilweise aufgelöst):** Die webtoon-spezifische *Lade-Logik*
  ist aus `ReaderViewModel` heraus extrahiert: der nahtlose, kapitelübergreifende Strip (Index↔
  Kapitel/Seite, flache Seiten-Liste, globaler Start) entsteht jetzt im **pur-getesteten**
  `buildWebtoonStrip` (`source-api/.../source/WebtoonStripPlanner.kt`, `WebtoonStripPlan`).
  `ReaderViewModel.loadWebtoonStrip` hält nur noch das I/O (`seriesIdOf`/`books`/`pullProgress`)
  + das `SourceImage`-Mapping und delegiert die Planung. paged/webtoon/rendered **bleiben** in
  `ReaderViewModel`, weil der **In-Screen-Toggle `toggleViewerMode` (paged⟷webtoon, comic⟷webtoon)**
  beide Layouts auf **einem** geladenen `ReaderContent` rendert und dabei `_currentPage`, den
  **einen** `RefreshScheduler` (Viewer-Naht: genau eine Instanz pro Sitzung) und `frameStep`
  geteilt teilt. Ein voller Zwei-VM-Split würde diesen Toggle brechen (zweiter Scheduler verboten,
  Scroll-/Seitenposition ginge verloren oder Inhalt müsste auf Toggle neu geladen werden = Lade-
  Sturm + Verhaltensänderung). Der Toggle bleibt daher **bewusst** in `ReaderViewModel` —
  dokumentiert direkt an `toggleViewerMode`. Die genuin webtoon-spezifische Logik ist trotzdem
  raus aus dem God-VM und einzeln getestet (`WebtoonStripPlannerTest`).

- **UI-Slot-Naht / Chrome (Ist, 2026-06-09 — erste Region gebaut):** Über den Reader-Engines wird das
  *Chrome* (Header/Overlay/Tiles/Nav/Settings/Dialog) regionweise auswechselbar — das „Layout danach"-
  Stück der modularen UI (`big-picture-and-goals.md` → ui-modularity). **Gebaut ist genau die erste
  Region `header`** (`app/ui/slots/UiSlots.kt`): der In-Tree-Vertrag `HeaderSlot`
  (`@Composable (title, onBack?, actions) -> Unit`, spiegelt `StandardTopAppBar`), das optionale
  `UiSlotPack(header)`, der **pure** Resolver `UiSlots.resolve` (fehlender Slot → `DefaultSlots`, nie
  `null`, analog `StubSource`) und `LocalResolvedSlots` (im Host `KomgaReaderTheme` bereitgestellt,
  spiegelt `LocalUiPack`). Die Header-Call-Sites (`SeriesDetailScreen`, `SubPageScaffold`) rendern jetzt
  `LocalResolvedSlots.current.header(...)` statt `StandardTopAppBar` direkt; das `DefaultSlots`-Pack ist
  verhaltensgleich. **E-Ink-Invarianten host-erzwungen:** ein Slot liefert nur Inhalt/Struktur, nie die
  Bewegungs-/Akzent-Policy (die bleibt an `LocalDisplayBehavior`/`LocalDesignTokens`/`LocalEinkMode`).
  **Weiter Soll:** die anderen fünf Slots, die `ui-api`-Modul-Extraktion und der APK-Pack-Lader bleiben
  Soll (Skins-Plan P2/P3). Vertrag bewusst in-tree, **nicht** eingefroren.

## Modulgrenzen (Gradle-Schnitt = erzwungene Architektur)

- `domain` hat **keine** Android-/Netz-/Quellen-Abhängigkeit. Pure Kotlin, pure Unit-Tests.
- Quellen-Module hängen nur von `domain` ab, nie voneinander, nie von `app`.
- `render-core`, `eink-onyx`, `guided-view` hängen nicht von der UI ab.
- `app` ist die imperative Shell (DI, ViewModels, Viewer-Host) — die einzige Schicht, die alles verdrahtet.
- Wenn ein Feature einen neuen Cross-Modul-Import nötig zu machen scheint: erst prüfen, ob es nicht
  hinter eine bestehende Naht gehört.

## Bezug

Gehört zu [[project-komga-eink-reader]]. Erweiterungs-Kochrezept: `source-extensibility.md`.
