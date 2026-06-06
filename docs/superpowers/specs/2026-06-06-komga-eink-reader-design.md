# Komga E-Ink Reader — Design-Spec

**Status:** Entwurf · **Datum:** 2026-06-06 · **Arbeitstitel:** Komga E-Ink Reader

---

## 1. Problem & Vision

E-Ink-Geräte (konkret: Onyx Boox Go Color 7 Gen 2) haben keinen Reader, der gleichzeitig:

- mit einem **Komga-Server** spricht (Browsen, Streamen, Download, Fortschritts-Sync),
- **drei Lesemodi** beherrscht — paged Comic/Manga, vertikaler Webtoon-Endlos-Scroll, EPUB-Reflow für Romane,
- **E-Ink-optimiert** ist (Refresh-Kontrolle gegen Ghosting, kräftige monochrome Darstellung, Cover-Farbkorrektur),
- die **physischen Boox-Tasten** sauber zum Blättern belegt.

Bestehende Apps erzwingen einen Kompromiss: Mihon (top Comic/Webtoon, nativ Komga, aber **null EPUB-Text** — Architektur ist Bild-Parser), KOReader/Moon+ (top EPUB + E-Ink, aber nur OPDS, schwaches Webtoon-Scroll), Komelia (nativ Komga + Comic+EPUB, aber **keine nachweisbare E-Ink-/Button-Optimierung**). Kein Single-App-Produkt schließt die Lücke.

**Vision:** Eine native Android-App (Kotlin), die diese Lücke schließt — und durch eine **quellen-agnostische Architektur** von Tag 1 für mehr als nur Komga offen ist: weitere Server (Kavita, OPDS), mehrere Quellen gleichzeitig, lokale Ordner und später nutzer-installierbare Online-Quellen-Plugins (Mihon-Modell).

## 2. Ziele & Nicht-Ziele

### Ziele
- Native Android-App in **Kotlin + Jetpack Compose**.
- **Eine** Render-Engine (MuPDF via JNI) für cbz/cbr/pdf **und** EPUB-Reflow, gerendert auf **Bitmap** für volle Pixel-/Refresh-Kontrolle.
- Quellen-Naht (`MediaSource`) als **stabile API ab Tag 1** — Runtime-Plugin-Loader erst später, aber nicht verbaut.
- Nutzer-definierte **Regale (Shelves)**, die mehrere Quellen bündeln und per Typ-Tag den Viewer festlegen.
- **Offline-first** Fortschritts-Sync mit Komga; Streamen UND Download.
- **E-Ink-Adaption:** eigener Refresh-Scheduler, Boox-SDK-Kapselung mit No-Op-Fallback, Hardware-Button-Mapping, Cover-Farbfilter.
- **i18n** (mind. DE + EN) und **Theme** (Hell / Dunkel / System) ab Start.

### Nicht-Ziele (vorerst)
- iOS / Desktop.
- Eigene EPUB-Reflow-Engine **from scratch** (wir wrappen MuPDF; crengine nur als optionaler Phase-4-Fallback).
- Runtime-APK-Plugin-Loader im MVP (nur stabile Interface-Vorbereitung).
- Komga-Admin-Features (Server verwalten, Scannen anstoßen) — Lese-Client, kein Verwaltungs-Client.
- OPDS im MVP (native Komga-REST zuerst; OPDS Phase 2).

## 3. Architektur-Überblick

Clean Architecture (Mihon-Vorbild) mit einer eigenen Engine-Schicht (KOReader-Vorbild). **Zwei tragende Nähte** kapseln die gesamte Variabilität:

```
┌─ UI (Compose) ─────────────────────────────────────────────┐
│  Bibliothek (Regal-Tabs, Grid, Badges) · Reader-Host ·      │
│  Settings · BaseDialog · Theme (Hell/Dunkel/System) · i18n  │
└──────────────────────────── ↓ ─────────────────────────────┘
┌─ Domain (kennt weder UI noch Daten) ───────────────────────┐
│  Modelle: Source · Shelf · Series · Book · Page · Progress  │
│  UseCases · Repository-Interfaces · ViewerType-Auflösung    │
└────────── ↓ NAHT A: Quellen ──────── ↓ NAHT B: Engine ─────┘
┌─ MediaSource ───────────┐   ┌─ PageRenderer / Viewer ───────┐
│ KomgaSource (REST)      │   │ MuPDF (C++/JNI) → Bitmap      │
│ LocalSource (id=0)      │   │ RefreshScheduler (E-Ink)      │
│ [OpdsSource, Plugins…]  │   │ EinkController (Onyx ⟂ No-Op) │
│ SourceManager + Stub    │   │ Paged/Webtoon/Epub-Viewer     │
└─────────────────────────┘   └───────────────────────────────┘
┌─ Data (imperative Shell) ──────────────────────────────────┐
│  Room-DB (quellenübergreifend) · Offline-Sync-Queue ·       │
│  Download-Manager · PageCache (Hash+mtime, Prefetch)        │
└─────────────────────────────────────────────────────────────┘
```

**Leitprinzip:** Alles über den Nähten ist quellen- und geräte-agnostisch. Eine neue Quelle oder ein neues E-Ink-Gerät = neue Implementierung hinter dem Interface, **kein Kern-Umbau**.

### Gradle-Module
| Modul | Inhalt |
|---|---|
| `app` | Compose-UI, ViewModels, Reader-Host, DI-Setup (imperative Shell) |
| `domain` | Modelle, UseCases, Repository-**Interfaces** — keine Android-/Netz-Abhängigkeit |
| `data` | Room-Impls der Repos, Sync-Queue, Download-Manager |
| `source-api` | Stabile `MediaSource`-API (Naht A) — Kandidat für späteres `compileOnly` der Plugins |
| `source-komga` | Komga-REST-Implementierung |
| `source-local` | `LocalSource` (Dateisystem, id=0) |
| `render-core` | `PageRenderer`/`Document`-Interface + MuPDF-JNI-Wrapper (Naht B) |
| `eink` | `EinkController`, `RefreshScheduler`, Boox-SDK-Adapter + No-Op |
| `i18n` | Typsichere String-Keys, DE + EN |
| `ui-core` | Theme-Tokens, BaseDialog, gemeinsame Composables, Icon-Registry |

## 4. Domain-Modell

```kotlin
// Eine Backend-Verbindung. Stabile, deterministische ID (Hash aus name/typ/config).
interface MediaSource { val id: Long; val name: String; val kind: SourceKind /* KOMGA, LOCAL, OPDS, PLUGIN */ }

// Nutzer-definiertes Regal: bündelt Quellen, deklariert den Lesemodus.
data class Shelf(
    val id: Long,
    val name: String,
    val contentType: ContentType,      // COMIC | NOVEL | WEBTOON  → Default-Viewer
    val sourceIds: List<Long>,         // n:m zu MediaSource
)

enum class ContentType { COMIC, NOVEL, WEBTOON }

data class Series(
    val id: Long, val sourceId: Long, val remoteId: String,
    val title: String, val coverUrl: String?,
    val contentTypeOverride: ContentType? = null,   // pro Serie überschreibbar (Mihon viewerFlags-Muster)
)

data class Book(
    val id: Long, val sourceId: Long, val seriesId: Long, val remoteId: String,
    val title: String, val format: BookFormat,      // CBZ | CBR | PDF | EPUB
    val pageCount: Int, val downloadState: DownloadState,  // REMOTE | DOWNLOADING | LOCAL
)

data class ReadProgress(
    val bookId: Long, val page: Int, val totalPages: Int,
    val completed: Boolean, val locator: String?,   // EPUB: CFI/Position; Comic: Seitenindex
    val dirty: Boolean,                              // noch nicht zu Komga gepusht
    val updatedAt: Long,
)
```

**Viewer-Auflösung (deterministisch):** `Series.contentTypeOverride ?: Shelf.contentType → ViewerType`. Kein fragiles Auto-Erkennen.

## 5. Naht A — Quellen (`source-api`)

Geschichtet nach Mihon-Vorbild, JSON statt HTML-Scraping:

```kotlin
interface MediaSource {
    val id: Long; val name: String; val kind: SourceKind
}
interface BrowsableSource : MediaSource {
    suspend fun browse(page: Int, filter: SourceFilter): PagedResult<Series>
    suspend fun search(query: String, page: Int): PagedResult<Series>
    suspend fun books(seriesId: String): List<Book>
    suspend fun pages(bookId: String): List<PageRef>           // URL/Pfad pro Seite
    suspend fun openBook(bookId: String): ByteSource           // Stream oder lokale Datei
}
interface SyncingSource : MediaSource {                        // Komga kann das, LocalSource nicht
    suspend fun pushProgress(bookId: String, progress: ReadProgress)
    suspend fun pullProgress(bookId: String): ReadProgress?
}
```

- **Deterministische ID:** `id = hash64(name + kind + serverUrl)` — global eindeutig ohne zentrale Registry, mehrere Komga-Server möglich.
- **`SourceManager`:** reaktive `Map<Long, MediaSource>` (Flow), injiziert `LocalSource` immer als id=0; `StubSource`-Fallback, wenn ein Bibliotheks-Eintrag auf eine entfernte/deinstallierte Quelle zeigt → Bibliothek bricht nie.
- **Stabilität für spätere Plugins:** `source-api` hat **keine** App-/Android-internen Abhängigkeiten, ist versioniert. Phase-4-Runtime-Loader (ChildFirst-ClassLoader, `compileOnly`-Lib, Signatur-Trust) kann andocken, ohne das Interface zu brechen.

### KomgaSource (MVP)
Native Komga-REST-API (`/api/v1/...`): Auth (API-Key oder Basic), `series`, `books`, `pages`, `progress` (write-back). Streamt Seiten per HTTP; lädt ganze Bücher für Offline.

## 6. Naht B — Render & E-Ink

### Render-Engine (`render-core`)
```kotlin
interface Document { fun pageCount(): Int; fun pageSize(i: Int): Size
                     fun renderPage(i: Int, target: Bitmap, zoom: Float, rot: Int) }
```
- **MuPDF** (C++/NDK) rendert cbz/cbr/pdf **und** EPUB-Reflow in eine `android.graphics.Bitmap` (`fz_pixmap` → Bitmap). Render-Target strikt von der View getrennt (KOReader-Blitbuffer-Muster).
- **Interface-Kapselung:** `Document`/`PageRenderer` ist die Naht. Falls MuPDFs EPUB-Reflow qualitativ nicht reicht, klinkt sich **crengine nur für EPUB** ein, ohne den Rest zu berühren (Phase 4, optional).
- **PageCache:** LRU, Key = Hash(Quelle, Buch-ID, mtime, Render-Params); Prefetch der Nachbarseiten im Worker.

### RefreshScheduler (`eink`) — die wertvollste KOReader-Übernahme
Eigene Schicht mit **Modus-Präzedenz + Region-Merge + partial→full-Promotion** gegen Ghosting:
- `FAST/A2` während Scroll, `PARTIAL` beim Blättern, `FULL` (flash) bei Bildwechsel/Rotation/nach N Partials.
- Jeder Refresh trägt seine `(x,y,w,h)`-Region.

### EinkController (`eink`)
```kotlin
interface EinkController {
    fun refresh(region: Rect, mode: RefreshMode)
    fun setContrast(level: Int); fun invert(enabled: Boolean)   // Dark Mode
    val hardwareButtons: Flow<ButtonEvent>
    val capabilities: EinkCapabilities                          // hasEink, canColor, …
}
```
- **OnyxEinkController:** Boox-SDK (`EpdController`/Onyx-SDK, Repo `onyx-intl/OnyxAndroidDemo`) — Refresh-Modi pro Region, Volume/Page-Tasten abfangen.
- **NoOpEinkController:** auf Emulator/Nicht-Boox — Standard-View-Invalidate, Buttons = normale KeyEvents. Entwicklung crasht nie auf Nicht-Boox-HW.
- **Cover-Farbfilter:** Boox-Kaleido-Display dämpft Farben — optionaler Sättigungs-/Kontrast-Boost auf Cover-Bitmaps vor Anzeige (Phase 3).

### Viewer (`app`/`ui-core`)
```kotlin
interface Viewer { fun bind(book: Book, progress: ReadProgress); fun onButton(e: ButtonEvent); fun teardown() }
```
- **PagedViewer** (Pager: L→R / R→L / vertikal), **WebtoonViewer** (RecyclerView, continuous vertical), **EpubViewer** (Reflow + Aa-Settings).
- Gemeinsame **Chrome-Logik:** Tap Mitte = Bars ein/aus; sonst immersiv. Hardware-Buttons + Tap-Zonen blättern.
- **Guided View** (Tap-Panel → Vollbild → Weiter): Phase 2, eigenes Modul mit OpenCV-Gutter-Detection, hinter Interface — unabhängig von der Engine.

## 7. Sync, Streaming & Download

- **Offline-first:** Lesen schreibt `ReadProgress(dirty=true)` lokal in Room → `SyncQueue` pusht zu Komga, sobald online (`SyncingSource.pushProgress`). Pull beim Öffnen für Cross-Device-Stand. Konfliktregel: jüngstes `updatedAt` gewinnt.
- **Streaming:** Seiten per HTTP von Komga, in PageCache. Kein Download nötig.
- **Download:** ganzes Buch → lokale Datei, `downloadState = LOCAL`, danach von `LocalSource`-Logik gelesen. Badge in der Bibliothek (☁ remote · ⤓ lädt · ✓ lokal).

## 8. UI

- **Ein Home-Screen — Bibliothek:** Regal-Tabs oben (`Alle · <Regale> · +`), Grid mit Cover, Herkunfts-/Offline-Badge, Lesefortschritts-Balken, Typ-Ribbon. Quellen-/Regal-Filter.
- **Regal-Editor:** Name, Typ→Viewer-Umschalter (Comic/Novel/Webtoon), Quellen-Auswahl (1+ Quellen, gemischt). „Eigener Ordner" = `LocalSource` auf nutzergewählten Pfad.
- **Reader:** 3 Modi (§6), tap-toggle-Chrome, Material-Symbols-Icons.
- **Settings:** Side-Tab-Bar — Server (Komga verbinden), Quellen/Regale, E-Ink (Refresh-Verhalten, Kontrast, Button-Belegung), Anzeige (Theme Hell/Dunkel/System, Sprache), Über.
- **E-Ink-Designsprache:** weißer Hintergrund (Dark-Mode invertiert), Tiefe über **1.5px-Border statt Schatten/Verläufe**, runde Ecken (8px), **keine Animationen** (sofortige State-Wechsel + gezielter Refresh), Material3 flach (Ripple/Elevation aus).
- **Icons:** **Material Symbols Outlined, Weight ~500** (`material-icons-extended`) — native, monochrom, E-Ink-kräftig. Zentrale Icon-Registry in `ui-core`.
- **BaseDialog:** ein Composable als Basis aller Dialoge (sticky Header/Footer, scrollender Body, Hardware-Back = abbrechen), max. ein Dialog gleichzeitig.

## 9. i18n
- Modul `i18n`: typsichere String-Keys, je Sprache eine Map (`de`, `en`), Compile-Zeit-Parität (Key fehlt in einer Sprache = Build-Fehler/Lint).
- Namespacing nach Bereich (`common.*`, `library.*`, `reader.*`, `settings.*`).
- Echte Umlaute/ß im Deutschen. Platzhalter-Interpolation für Zähler/Namen. Sprache als persistiertes Setting (Default `system`).

## 10. Tech-Stack
Kotlin · Jetpack Compose (Material3, flach) · Room · Coroutines/Flow · MuPDF (NDK/JNI, C++) · OpenCV (Phase 2, Guided View) · Onyx-Boox-SDK (gekapselt) · DI (Hilt oder Koin — Entscheidung in der Plan-Phase) · OkHttp/Ktor + kotlinx.serialization für Komga-REST · Material Symbols (`material-icons-extended`).

## 10a. Lizenz (Design-Entscheidung 2026-06-06)
Die App nutzt **MuPDF** als einzige Render-Engine (cbz/cbr/pdf/epub aus einer Hand). MuPDF ist **AGPL-3.0**. Entscheidung: **AGPL akzeptiert — die App wird unter AGPL-3.0-or-later Open Source** (passt zum offenen Plugin-Ökosystem). Konsequenz: jede Verteilung muss den App-Quellcode unter AGPL offenlegen. `LICENSE` (AGPL-3.0-Volltext) und `NOTICE` (Drittsoftware) liegen im Repo-Root. Alternativen (Pdfium-Split, kommerzielle Artifex-Lizenz) wurden verworfen.

## 11. Phasen-Roadmap
Jede Phase = eigene Spec→Plan→Bau-Runde, jede für sich lauffähig.

- **Phase 1 / MVP** (Detail §12): Komga verbinden → Bibliothek (ein Default-Regal) → **nur PagedViewer** streamen → Boox-Buttons + Basis-Refresh → Progress-Sync. Beweist die ganze Pipeline.
- **Phase 2:** WebtoonViewer + EpubViewer · Download/Offline · Lesezeichen-Sync · Regal-Verwaltung (mehrere Quellen, Typ-Tag) · OPDS-Quelle · Guided View (OpenCV).
- **Phase 3:** Cover-Farbfilter · per-Region-Refresh-Feintuning · erweiterte E-Ink-Settings · weitere Server (Kavita).
- **Phase 4:** Runtime-Plugin-Loader (nutzer-installierbare Online-Quellen, Mihon-Modell) · optional crengine als EPUB-Engine.

## 12. Phase-1-MVP — Detailscope
**Drin:**
1. **Settings → Server:** eine Komga-Verbindung (URL + Auth), Verbindungstest, persistiert.
2. **KomgaSource** (`BrowsableSource` + `SyncingSource`): browse, search, books, pages, openBook (stream), push/pullProgress.
3. **Bibliothek:** ein automatisches Regal „Alle" über die Komga-Quelle; Grid mit Cover + Cloud-Badge + Fortschrittsbalken. Tap → Serie → Buch → Reader.
4. **render-core + MuPDF-JNI:** Comic-Seiten (cbz/pdf) auf Bitmap. PageCache + Prefetch.
5. **PagedViewer:** L→R, Tap-Zonen + Hardware-Buttons, tap-toggle-Chrome, Seiten-Slider.
6. **eink:** `EinkController` mit Onyx- + NoOp-Impl; Basis-RefreshScheduler (partial/full + Promotion); Button-Mapping.
7. **Sync:** Offline-first ReadProgress in Room + SyncQueue → Komga.
8. **Querschnitt:** i18n (DE+EN) Gerüst, Theme (Hell/Dunkel/System), BaseDialog, Icon-Registry, Modul-Schnitt + DI.

**Bewusst draußen:** Webtoon/EPUB-Viewer, Download, Regal-Editor/Multi-Source, OPDS, Guided View, Cover-Filter, Plugins.

## 13. Risiken
- **MuPDF-JNI-Build** (NDK, libc++_shared, Android-ABI) — höchstes technisches Risiko; früh als Spike absichern.
- **Onyx-SDK-Reichweite** — Refresh-/Button-API gerätespezifisch; NoOp-Fallback hält Entwicklung am Laufen, echtes Verhalten nur auf Boox testbar.
- **MuPDF EPUB-Reflow-Qualität** — erst in Phase 2 relevant; `Document`-Naht erlaubt crengine-Nachrüstung.
- **Komga-REST-Progress-Semantik** (Read-Lists, Lese-Position EPUB) — gegen echte Komga-Instanz verifizieren.

## 14. Teststrategie
- **Domain/UseCases:** pure Unit-Tests (TDD) — Viewer-Auflösung, Sync-Konfliktregel, Shelf-Aggregation.
- **source-komga:** Vertragstests gegen gemockte Komga-REST-Responses; optional Integrationstest gegen lokale Komga-Instanz.
- **render-core:** Golden-Bitmap-Tests (bekannte Seite → erwartetes Rendering).
- **eink/RefreshScheduler:** Unit-Tests der Modus-Präzedenz/Region-Merge-Logik (geräteunabhängig).
- **UI:** Compose-UI-Tests für Bibliothek/Reader-Chrome.

## 15. Offene Punkte (für Plan-Phase)
- DI-Framework: Hilt vs. Koin.
- Komga-Auth: API-Key vs. Session/Basic — was die Ziel-Komga-Version sauber unterstützt.
- Netzwerk-Lib: OkHttp+Retrofit vs. Ktor-Client.
- EPUB-Lese-Position-Format (CFI vs. MuPDF-eigene Position) und Komga-Kompatibilität.
