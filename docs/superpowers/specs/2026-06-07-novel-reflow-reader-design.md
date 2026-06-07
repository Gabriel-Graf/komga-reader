# Design: Roman-Reader (Reflow, crengine) — KOReader-artige Typografie

**Datum:** 2026-06-07
**Status:** Freigegeben (Brainstorming)
**Betrifft:** Naht B (`Document`), neues Modul `render-crengine`, `domain/render`,
`ResolveViewerType`, neuer `ViewerType.NOVEL`, Sync-Queue (`SyncingSource`),
neuer Reader-Screen + Typo-Panel (`eink-ui`), Fonts/Hyphenation-Assets, `NOTICE`/Provenance.

## Problem & Ziel

Der bestehende EPUB-Pfad (`EpubReaderScreen` + `ReaderViewModel.renderEpubPage`)
rendert EPUBs als **fixe Bitmap-Seiten** über MuPDF (`AndroidDrawDevice.drawPage`
mit festem Zoom, kein `layout()`). Für **Romane** fehlt damit alles, was
komfortables Lesen ausmacht: Schriftgröße, Zeilenabstand, Ränder, Schriftart,
Blocksatz, Silbentrennung — die KOReader-Knöpfe.

Ziel: ein **Roman-Reader** mit reflowbarem Text und KOReader-artiger
Typografie-Kontrolle, **E-Ink-optimiert** und **offline-first**. Quellen-agnostisch
über die bestehende Render-Naht — neue Engine **hinter** dem `Document`-Interface,
kein Kern-Umbau. Der **bisherige fixe EPUB-Bitmap-Viewer wird abgeschafft**: jede
reflowbare Datei läuft künftig über den NOVEL-Reader.

### Reader-Typen nach diesem Umbau

| ViewerType | Inhalt | Auslöser |
|---|---|---|
| **PAGED** | Manga/Comic blättern (Bild pro Seite, Zoom/Crop) | Fallback (kein Tag) · CBZ/CBR/PDF |
| **WEBTOON** | Endlos-Vertikal-Scroll | `webtoon`-Tag / readingDirection VERTICAL·WEBTOON |
| **COMIC** | Geführtes Panel-Zoomen | `comic`-Tag |
| **NOVEL** *(neu)* | **Reflow-Fließtext**, alle Typo-Knöpfe | `novel`-Tag · **jedes EPUB/MOBI/FB2-Format** |

`ViewerType.EPUB` **entfällt**; `EpubReaderScreen` + `ReaderViewModel.renderEpubPage`
werden entfernt.

### Formate (Recherche-Ergebnis)

- **Reflowbar (Ziel dieses Readers):** EPUB, MOBI, FB2, AZW3 (DRM-frei) — crengine
  deckt alle über denselben Pfad ab. („mimi-Format" = **MOBI**.)
- **PDF: kein echtes Reflow.** Fester Text, absolut positioniert. Bleibt beim
  bestehenden `PagedViewer` (Zoom/Crop), **nicht** Teil dieses Readers.
- **DRM-Kindle (AZW mit DRM):** nicht unterstützt.

## Entscheidungen (Brainstorming)

| # | Entscheidung |
|---|---|
| Verhältnis | **`ViewerType.NOVEL`** (Reflow) ersetzt den fixen EPUB-Bitmap-Viewer vollständig. `ViewerType.EPUB` + `EpubReaderScreen` werden entfernt. Alle EPUBs → NOVEL. |
| Engine | **crengine** (CoolReader-Engine) als neue `Document`-Impl. Echte KOReader-Typo-Qualität (Hyphenation, Blocksatz ohne „rivers"). |
| Typo-Knöpfe v1 | Schriftgröße · Zeilenabstand · Seitenränder · Schriftart + Blocksatz + Hyphenation. |
| Bonus v1 | Status-Footer (% / Seite / Kapitel) · Inhaltsverzeichnis (TOC) · Volltextsuche · Gehe-zu-%. |
| Settings-Scope | **Global**, persistiert (ein Satz für alle Romane). |
| Anzeige | **Paged-only** (Blättern, Full-Refresh bei Bildwechsel). |
| Fortschritt | **Lokal: crengine-xpointer** (exakt, font-unabhängig) als SSoT. **Zu Komga: nur `totalProgression`-%** über Sync-Queue. |

## Nicht-Ziele (YAGNI)

- **Kein** Continuous-Scroll in v1 (E-Ink-Ghosting; ist Webtoon-Viewers Job).
- **Kein** Pinch-Zoom (Reflow — „näher heran" = Schriftgröße hoch).
- **Kein** Per-Buch-Typo-Override in v1 (global; Override später).
- **Keine** volle CFI↔xpointer-Brücke (nur grobes %-Seek beim Komga-Pull).
- **Kein** PDF-Reflow.
- **Nicht in v1:** Lesezeichen/Markierungen/Notizen, Wörterbuch (StarDict),
  Wikipedia, Übersetzen, Vokabel-Trainer, TTS, Lese-Statistik, Profile.
- **Kein** stiller MuPDF-Fallback, wenn crengine ausfällt (siehe Phase 0).

## Phase 0 — Lizenz-Gate (hart, vor jeder NDK-Zeile)

crengine ist GPL-2.0 (teils -or-later). Das Projekt ist **AGPL-3.0-or-later**
(MuPDF). **GPL-2.0-only ⨯ AGPL-3.0 = Lizenzkonflikt.** Nur GPL-2.0-**or-later**
(oder anderweitig GPL-3-kompatibel) lässt sich auf (A)GPL-3 heben.

**Dieser Schritt läuft VOR allem anderen. Kein Vendoren, kein Build davor.**

- **0a — Header-Scan (automatisiert):** crengine-Quelle holen, Skript über alle
  `.c/.cpp/.h/.hpp` im **Render-Pfad** laufen lassen. Pro Datei die Lizenz-Aussage
  klassifizieren: SPDX-Tag (`GPL-2.0-or-later` vs `GPL-2.0-only`) bzw. den
  Klartext-Header („version 2 … or … any later version" vs. „version 2" ohne
  „later"). Report = Tabelle Datei → Befund. Dateien ohne klare Aussage werden
  als **unklar** geflaggt (= behandelt wie rot, bis geklärt).
- **0b — Entscheidungsknoten:**
  - **Grün:** alle Dateien im Render-Pfad GPL-2.0-or-later / GPL-3-kompatibel →
    weiter zu Phase 1.
  - **Rot:** **eine einzige** GPL-2.0-only- oder unklare Datei im Render-Pfad →
    **Stopp.** Feature wird nicht gebaut. Rückkehr zum Nutzer für Neu-Entscheid.
    „So be it" ist als akzeptiertes Ergebnis vereinbart — kein automatischer
    Fallback.
- **0c — Dokumentation:** Lizenz-Befund, crengine-Quelle (Permalink), Version,
  Erfassungsdatum → `NOTICE` + Provenance-Datei (Regel `data-provenance`).
  Gebündelte Fonts und Hyphenation-Patterns ebenso (Name/URL/Lizenz/Version).

## Architektur-Überblick

```
app (Compose) ─ NovelReaderScreen + Typo-Panel + Footer/TOC/Suche (eink-ui)
     │ ruft über Domain-Interface, kennt KEINE Engine
     ▼
domain/render
     ReflowableDocument : Document      ← Seam-Erweiterung (engine-neutral)
     ReflowConfig (Wert)                ← em/lineHeight/margins/font/align/hyph
     Chapter, SearchHit                 ← TOC / Suche
     │
     ▼ NAHT B
render-crengine (NEU, NDK)              render-core (MuPDF, unverändert)
     CrengineDocument : ReflowableDocument
     cr3-JNI-Bridge → crengine (C++)
     → rendert Seite in Bitmap → RenderedPage
```

- **Über der Naht** (`app`, `domain`) kein crengine-Wissen. Kein
  `import …crengine…` außerhalb des Moduls — sonst Naht verletzt.
- `render-crengine` hängt **nur** von `domain` ab (Modulgrenzen-Regel).
- MuPDF (`render-core`) bleibt unangetastet; könnte später eine Teilmenge von
  `ReflowableDocument` implementieren — Interface bleibt deshalb neutral.

## Komponenten im Detail

### 1. Seam-Erweiterung — `ReflowableDocument` (domain/render)

Das bestehende `Document` (pageCount/pageSize/renderPage/close) kann kein Reflow.
Neues Sub-Interface, **engine-agnostisch** (kein crengine-Jargon im Namen):

```kotlin
interface ReflowableDocument : Document {
    /** Re-Layout mit neuer Typo-Konfiguration; danach ändert sich pageCount(). */
    fun applyLayout(cfg: ReflowConfig)

    /** Inhaltsverzeichnis: Titel + stabiler Anker. */
    fun chapters(): List<Chapter>

    /** xpointer der aktuell sichtbaren Seite (font-unabhängig stabil). */
    fun currentAnchor(): String

    /** Exakter Sprung (Resume am selben Gerät, TOC, Suchtreffer). */
    fun seekToAnchor(anchor: String)

    /** Grobes %-Seek (Komga-Pull, Cross-Device). */
    fun seekToProgress(fraction: Float)

    /** Volltextsuche; Hit = Anker + Snippet. */
    fun search(query: String): List<SearchHit>
}

data class Chapter(val title: String, val anchor: String, val depth: Int)
data class SearchHit(val anchor: String, val snippet: String)
```

- `pageCount()`/`renderPage(index, …)` arbeiten nach `applyLayout` auf der
  **aktuellen** Pagination (crengine paginiert intern nach Viewport+em).
- Render-Target bleibt strikt Bitmap → `RenderedPage` (Domain Android-frei).

### 2. `ReflowConfig` (Domain-Wert) + CSS-Mapping

```kotlin
data class ReflowConfig(
    val fontSizeEm: Float,           // Schriftgröße (crengine: em / Render-DPI)
    val lineHeight: Float,           // 1.0 .. 2.0
    val margin: Margins,             // T/B/L/R in dp/px
    val fontFamily: String,          // gebündelter Font-Name
    val textAlign: TextAlign,        // LEFT | JUSTIFY
    val hyphenation: Hyphenation,    // OFF | Language(lang)
)
```

- **Pure Mapping-Funktion** `ReflowConfig → crengine-Properties/CSS` —
  unit-testbar ohne Engine. Zeilenabstand/Ränder/Align via crengine-Style-Props
  bzw. User-CSS; Schriftgröße via em/Render-DPI; Hyphenation via Sprach-Pattern.

### 3. ViewerType-Auflösung (`ResolveViewerType`)

- `ViewerType` verliert `EPUB`, bekommt `NOVEL`. `ContentType.NOVEL` existiert
  bereits.
- **Stufen-Reihenfolge bleibt unverändert** (Regel `komga-viewer-type-resolution`,
  „nicht umsortieren"). Nur zwei Ziele ändern sich:
  - **Stufe 2:** `book.format == EPUB → NOVEL` (statt `→ EPUB`).
  - **`map`:** `NOVEL → ViewerType.NOVEL` (statt `→ EPUB`).
- Ergebnis-Regel:
  1. Override → map · 2. Format EPUB → **NOVEL** · 3. VERTICAL/WEBTOON → WEBTOON
  · 4. Fallback → map · 5. CBZ/CBR/PDF → PAGED · 6. PAGED.
  `map`: MANGA→PAGED, COMIC→COMIC, WEBTOON→WEBTOON, **NOVEL→NOVEL**.
- **Deterministisch**, kein Auto-Erkennen (Invariante #4). Jede `.epub`/reflowbare
  Datei landet im NOVEL-Reader — kein Tag nötig; das `novel`-Tag deckt zusätzlich
  Nicht-EPUB-Fälle/Regal-Defaults ab.
- `docs/domain/viewer-type-resolution.md` + Skill `komga-viewer-type-resolution`
  entsprechend nachziehen (Stufe-2-Ziel, map-Tabelle, EPUB→NOVEL).

### 4. NovelReaderScreen (app, `eink-ui`)

- **Paged**: HW-Tasten + Tap-Zonen blättern; Tap Mitte = Chrome ein/aus, sonst
  immersiv. Seitenwechsel/Re-Layout als **bewusster Full-Refresh** über
  `RefreshScheduler`/`EinkController`.
- **Render**: `renderPage(currentIndex)` → Bitmap → `FilteredReaderImage`
  (E-Ink-Farbfilter-Naht bleibt erhalten).
- **Keine Animation** — alle Übergänge über `LocalEinkMode` gegatet
  (Regel `animation-gating`).

### 5. Typo-Panel (app, `eink-ui`)

- `BaseDialog`/Bottom-Sheet, max. ein Dialog gleichzeitig, Hardware-Back = zu.
- Beschriftete **Lucide-Icons** via `AppIcons`-Registry; 1.5px-Border, flach.
- Controls: Schriftgröße (±), Zeilenabstand, Ränder, Font-Auswahl,
  Links/Blocksatz, Hyphenation an/aus. Änderung → `applyLayout` → Full-Refresh.
- Alle Texte via `i18n` (DE+EN, echte Umlaute, Compile-Zeit-Parität).

### 6. Fortschritt + Sync (offline-first)

- **Lokal (SSoT):** Tabelle `novel_progress` (Room), Schlüssel `sourceId`+bookId,
  Spalten: `anchor` (xpointer), `fraction` (0..1), `dirty`, `updatedAt`.
  Resume am selben Gerät → `seekToAnchor(anchor)` exakt.
- **Zu Komga:** über `SyncingSource`/Sync-Queue nur `totalProgression`-% pushen
  (`dirty`→Queue, wie bestehender Progress-Sync). Pull = `seekToProgress(%)`
  (grob, Cross-Device).
- Room-Migration nach dem **Recreate-Table-Muster** (Regel
  `room-migration-destructive-pitfall`) — kein `ALTER ADD COLUMN+DEFAULT`.

### 7. TOC · Footer · Suche

- **Footer:** %, Seite (der aktuellen Pagination), Kapiteltitel — via `i18n`.
- **TOC:** `chapters()` → Liste → `seekToAnchor`.
- **Suche:** Eingabe → `search(query)` → Treffer-Liste (Snippet) → `seekToAnchor`.

### 8. Fonts & Hyphenation (Assets)

- 2–3 E-Ink-taugliche Fonts bündeln (Serif z.B. Literata/Bitter + ein Sans),
  bei crengine registrieren.
- Hyphenation-Patterns **DE + EN** mitliefern.
- Lizenzen aller Assets → `NOTICE` + Provenance (Erfassungsdatum, URL, Lizenz).

## Datenfluss

```
Bytes (KomgaSource: Stream/Download)
   → CrengineDocument.open(bytes, formatHint)
   → applyLayout(ReflowConfig aus globalen Settings)
   → seekToAnchor(gespeicherter xpointer)  [Resume]
   → renderPage(index) → Bitmap → FilteredReaderImage
Blättern / Typo-Änderung → applyLayout/seek → Full-Refresh
Seite gesettled → currentAnchor() + fraction → novel_progress (dirty)
                → Sync-Queue → Komga totalProgression %
```

## Fehlerbehandlung

- crengine-Open scheitert (kaputtes/DRM-File) → klare Fehlerkachel im Reader,
  kein Crash; Buch bleibt in Bibliothek.
- Fehlender/ungültiger xpointer (z.B. nach Quell-Update) → Fallback auf
  `seekToProgress(fraction)`, sonst Anfang.
- Nicht-Boox-HW → `NoOpEinkController` (Full-Refresh = no-op), Reader läuft.

## Tests (TDD + E2E)

**Pure Unit (zuerst, gesetzt + leer/null):**
- `ResolveViewerType` mit `NOVEL`-Tag und Fallbacks.
- `ReflowConfig → crengine-Properties/CSS` Mapping.
- Progress-Mapper: `currentAnchor`/`fraction` ↔ `novel_progress` ↔ Komga-%.
- Lizenz-Scan-Klassifizierer (or-later vs only vs unklar) als reine Funktion.

**Instrumented / E2E (lokale Test-Komga, Emulator `eink_test` 1264×1680@300):**
- Roman öffnen → Schriftgröße ändern → `pageCount` ändert sich → Position
  (xpointer) bleibt erhalten.
- Fortschritt → `dirty` → %-Push zu Komga; Pull → grobes %-Seek.
- TOC-Sprung, Suchtreffer-Sprung.
- Boox-Screenshot für reale Typo-Qualität (Hyphenation/Blocksatz).

## Bau-Reihenfolge

0. **Lizenz-Gate** (0a Scan → 0b Knoten → 0c Doku). Rot = Ende.
1. **Spike:** crengine NDK-Cross-Build + minimaler JNI-Render **einer** Seite
   in Bitmap (de-risk Build + Lizenz-Pfad).
2. **Seam-Erweiterung** `ReflowableDocument`/`ReflowConfig`/`Chapter`/`SearchHit`
   in `domain`, Unit-Tests fürs Mapping.
3. **ViewerType.NOVEL** + `ResolveViewerType`-Eingriff (Stufe-2-Ziel + map,
   `EPUB` raus); Doku/Skill nachziehen.
4. **NovelReaderScreen** (paged) + Render-Pfad über `CrengineDocument`;
   **`EpubReaderScreen` + `renderEpubPage` entfernen**, Reader-Dispatch umstellen.
5. **Typo-Panel** + `ReflowConfig`-Anwendung + globale Settings-Persistenz.
6. **Fortschritt** (`novel_progress`, xpointer) + Komga-%-Sync (`SyncingSource`).
7. **TOC + Footer + Volltextsuche + Gehe-zu-%**.
8. **Fonts/Hyphenation bündeln** + `NOTICE`/Provenance final.

## Risiken

- **R0 (Showstopper):** crengine-Lizenz nicht or-later → Feature entfällt
  (Phase 0, akzeptiert).
- **R1:** crengine-NDK-Bau (freetype/harfbuzz/fribidi/png/jpeg/zlib/utf8proc) ist
  aufwändig — Spike zuerst, CoolReader/KOReader als Vorlage.
- **R2:** xpointer↔Komga-% ist verlustbehaftet (Cross-Device nur grob) — bewusst
  akzeptiert; lokaler Resume bleibt exakt.
- **R3:** crengine APK-Größenbeitrag (Engine + Fonts + Hyph-Patterns) — im Auge
  behalten.
