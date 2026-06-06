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
┌─ MediaSource ───────────┐   ┌─ Document / Viewer ───────────┐
│ KomgaSource (REST)      │   │ MuPDF (C++/JNI) → Bitmap      │
│ OpdsSource              │   │ RefreshScheduler (E-Ink)      │
│ [LocalSource, Plugins…] │   │ EinkController (Onyx ⟂ No-Op) │
│ SourceManager + Stub    │   │ Paged/Webtoon/Epub-Viewer     │
└─────────────────────────┘   └───────────────────────────────┘
```

## Naht A — Quellen (`domain/source/MediaSource.kt`)

- Jede Backend-Verbindung implementiert `MediaSource` (+ `BrowsableSource` zum Lesen,
  `SyncingSource` für Fortschritts-Sync). Stabile, deterministische `id` (Hash aus name/typ/config).
- `StubSource` hält Titel/ID, wenn die echte Quelle fehlt — die Bibliothek bricht nie.
- **Regel:** UI und `domain` kennen **nur** `MediaSource`-Interfaces, **nie** `KomgaSource`/`OpdsSource`
  konkret. Wer in `app` oder `domain` `import …source.komga…` schreibt, hat die Naht verletzt.
- Quellen-übergreifende DB (`data`): jeder Datensatz trägt `sourceId`. `LocalSource` = id 0.
- Runtime-Plugin-Loader (Phase 4) hängt sich genau hier ein — das Interface ist dafür schon stabil,
  also keine quellenspezifischen Annahmen ins Interface backen.

## Naht B — Render & E-Ink (`render-core`, `eink-onyx`)

- `Document`/`PageRenderer` ist die Render-Naht: **MuPDF** rendert cbz/cbr/pdf **und** EPUB-Reflow
  in eine `android.graphics.Bitmap`. Render-Target strikt von der View getrennt.
  Reicht MuPDFs EPUB-Qualität nicht, klinkt sich crengine **nur für EPUB** hinter dem Interface ein
  (Phase 4) — ohne den Rest zu berühren.
- `EinkController` kapselt das Gerät: `OnyxEinkController` (Boox-SDK, **HW-gated** über
  `Build.MANUFACTURER`), `NoOpEinkController` als Fallback. **Entwicklung crasht nie auf Nicht-Boox-HW.**
- `RefreshScheduler`: Modus-Präzedenz + Region-Merge + partial→full-Promotion gegen Ghosting.
  Geräteunabhängig, unit-testbar.
- `Viewer`-Interface (`bind/onButton/teardown`): PagedViewer, WebtoonViewer, EpubViewer.

## Modulgrenzen (Gradle-Schnitt = erzwungene Architektur)

- `domain` hat **keine** Android-/Netz-/Quellen-Abhängigkeit. Pure Kotlin, pure Unit-Tests.
- Quellen-Module hängen nur von `domain` ab, nie voneinander, nie von `app`.
- `render-core`, `eink-onyx`, `guided-view` hängen nicht von der UI ab.
- `app` ist die imperative Shell (DI, ViewModels, Viewer-Host) — die einzige Schicht, die alles verdrahtet.
- Wenn ein Feature einen neuen Cross-Modul-Import nötig zu machen scheint: erst prüfen, ob es nicht
  hinter eine bestehende Naht gehört.

## Bezug

Gehört zu [[project-komga-eink-reader]]. Erweiterungs-Kochrezept: `source-extensibility.md`.
