# Komga E-Ink Reader — Projekt-Leitfaden

Native Android-Reader-App (Kotlin/Compose) für **Onyx Boox Go Color 7 Gen2** (E-Ink),
die drei Lesemodi (paged Comic, Webtoon-Scroll, EPUB-Reflow) + nativ Komga + E-Ink-Optimierung
+ Boox-Tasten in **einer** App vereint — quellen-agnostisch von Tag 1.

**Maßgebliche Quellen — vor jeder Änderung lesen:**
- Design-Spec: `docs/superpowers/specs/2026-06-06-komga-eink-reader-design.md` (die ganze Architektur)
- Pläne: `docs/superpowers/plans/`

## Die nicht verhandelbaren Invarianten

Diese fünf Punkte tragen das ganze Projekt. Wer sie verletzt, baut am Kern vorbei:

1. **Zwei Nähte kapseln alle Variabilität.** Naht A = Quellen (`MediaSource`), Naht B = Render/E-Ink
   (`Document`/`Viewer`/`EinkController`). Neue Quelle oder neues Gerät = neue Impl **hinter** dem
   Interface, **nie** ein Kern-Umbau. → `@.claude/rules/architecture-seams.md`
2. **Alles über den Nähten ist quellen- und geräte-agnostisch.** Kein `KomgaSource`-Wissen in `domain`
   oder `app`. Metadaten/Features fließen durch das Domain-Modell + `MediaSource`-Interface, jede Quelle
   füllt was sie kann. → `@.claude/rules/source-extensibility.md`
3. **E-Ink-Designsprache ist Pflicht, nicht Geschmack.** Flach, 1.5px-Border statt Schatten, keine
   Animationen, monochrom-kräftig, Lucide-Icons via `AppIcons`-Registry, `BaseDialog`. → `@.claude/rules/eink-design-language.md`
4. **Viewer-Auflösung ist deterministisch:** `Series.contentTypeOverride ?: Shelf.contentType → ViewerType`.
   Kein fragiles Auto-Erkennen des Inhaltstyps.
5. **Offline-first + TDD + E2E.** Lesefortschritt lokal (`dirty`) → Sync-Queue. Jedes Feature mit
   Unit-Tests (Domain/Mapper pure) **und** kleinem E2E gegen die lokale Test-Komga verifizieren.
   → `@.claude/rules/roadmap-and-invariants.md`

## Module (Schnitt = Architektur)

| Modul | Inhalt | Darf NICHT abhängen von |
|---|---|---|
| `domain` | Modelle, UseCases, Repo-/Source-**Interfaces** | Android, Netz, irgendeiner Quelle |
| `source-komga` · `source-opds` | konkrete `MediaSource`-Impls | UI, anderen Quellen |
| `render-core` | `Document`/`PageRenderer` + MuPDF-JNI (Naht B) | UI |
| `eink-onyx` | `OnyxEinkController` (Onyx-SDK, HW-gated) | UI, Quellen |
| `guided-view` | Panel-Erkennung (pure Kotlin XY-Cut) | Engine, UI |
| `data` | Room-Impls, Sync-Queue, Download-Manager | UI |
| `app` | Compose-UI, ViewModels, DI, Viewer-Host | — (oberste Schicht) |

## Detailregeln

- `@.claude/rules/architecture-seams.md` — die zwei Nähte, Modulgrenzen, was nie umgangen wird
- `@.claude/rules/source-extensibility.md` — neue Server/Metadaten quellen-agnostisch hinzufügen (Schritt-für-Schritt)
- `@.claude/rules/eink-design-language.md` — die E-Ink-Designsprache verbatim
- `@.claude/rules/roadmap-and-invariants.md` — Phasen P1–P4, was noch kommt, was nicht aus den Augen verloren werden darf
- `@.claude/rules/shared-structure-before-variants.md` — Gemeinsames vor der N-ten Variante extrahieren (schon beim Planen mitdenken)

> Lizenz: **AGPL-3.0-or-later** (MuPDF ist AGPL). Jede Verteilung legt den Quellcode offen. `LICENSE`/`NOTICE` im Root.
