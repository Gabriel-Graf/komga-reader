# Komga E-Ink Reader — Projekt-Leitfaden

Native Android-Reader-App (Kotlin/Compose) für **Onyx Boox Go Color 7 Gen2** (E-Ink),
die drei Lesemodi (paged Comic, Webtoon-Scroll, EPUB-Reflow) + nativ Komga + E-Ink-Optimierung
+ Boox-Tasten in **einer** App vereint — quellen-agnostisch von Tag 1.

**Maßgebliche Quellen — vor jeder Änderung lesen:**
- Design-Spec: `docs/superpowers/specs/2026-06-06-komga-eink-reader-design.md` (die ganze Architektur)
- Pläne: `docs/superpowers/plans/`

## CodeGraph zuerst — Pflicht (dieses Repo ist indexiert)

`.codegraph/` existiert im Repo-Root. Für **jede** Frage zum Code — "wo ist X",
"was ruft Y", "wie ist Z eingebunden/verdrahtet", Naht-/DI-/Call-Pfad-Fragen —
**zuerst CodeGraph**, vor `grep`/`find`/`Read`:
- MCP: `codegraph_explore "<Frage oder Symbole>"` (eine Antwort = verbatim Source +
  Call-Pfade), `codegraph_node <symbol|datei>`. Deferred → per ToolSearch laden.
- Shell-Fallback (immer da): `codegraph explore "<…>"`, `codegraph node <…>`.

`grep`/`find`/`Read` nur, um ein konkretes von CodeGraph nicht abgedecktes Detail
zu bestätigen — typisch **Build-/Native-/Nicht-Code-Dateien** (`build.gradle.kts`,
`CMakeLists.txt`, `*.md`, Assets). Selbst dann startet die Symbol-/Wiring-Hälfte der
Frage bei CodeGraph. Direkt mit `grep`/`find` einzusteigen, obwohl CodeGraph greift,
ist ein Regelverstoß (real passiert bei der crengine-Einbindungs-Frage 2026-06-17).

## Language

Code-facing artifacts are **always English**: code comments + KDoc, project docs (`docs/`,
`README`, this file and `.claude/rules/*` going forward), GitHub **release notes**, and **commit
messages**. Conversation with the user stays German. User-visible UI text is the one exception —
it always goes through i18n with **both German and English** values (`app/.../i18n`), as the
E-Ink design language requires; those translations are localized content, not authored docs.

## Die nicht verhandelbaren Invarianten

Diese fünf Punkte tragen das ganze Projekt. Wer sie verletzt, baut am Kern vorbei:

1. **Zwei Nähte kapseln alle Variabilität.** Naht A = Quellen (`MediaSource`), Naht B = Render/E-Ink
   (`Document`/`EinkController`; ein gemeinsames `Viewer`-Interface ist *Soll*, noch nicht gebaut).
   Neue Quelle oder neues Gerät = neue Impl **hinter** dem Interface, **nie** ein Kern-Umbau.
   → `@.claude/rules/architecture-seams.md`
2. **Alles über den Nähten ist quellen- und geräte-agnostisch — auch in der Verdrahtung.** Kein
   `KomgaSource`/`*SourceProvider`-Wissen in `domain` oder `app` (ViewModels über `SourceManager`/
   `MediaSource`, Lesen über `openPage`). Metadaten fließen durch das Domain-Modell, jede Quelle
   füllt was sie kann. → `@.claude/rules/source-agnostic-integration.md`, `@.claude/rules/source-extensibility.md`
3. **E-Ink-Designsprache ist Pflicht, nicht Geschmack.** Flach, 1.5px-Border statt Schatten, keine
   Animationen, monochrom-kräftig, Lucide-Icons via `AppIcons`-Registry, `BaseDialog`. → `@.claude/rules/eink-design-language.md`
4. **Viewer-Auflösung ist deterministisch:** `Series.contentTypeOverride ?: Shelf.contentType → ViewerType`.
   Kein fragiles Auto-Erkennen **im Lesepfad**. Auto-Erkennung des Inhaltstyps existiert nur als
   **vorab persistierter Vorschlag** in eigener, niedriger Stufe 5 (`series_auto_types`/`SuggestContentType`,
   Pixel-Heuristik) — manueller Override, Server-`readingDirection` und Bibliotheks-Default schlagen ihn alle.
   → `@.claude/skills/komga-viewer-type-resolution/SKILL.md`
5. **Offline-first + TDD + E2E.** Lesefortschritt lokal (`dirty`) → Sync-Queue. Jedes Feature mit
   Unit-Tests (Domain/Mapper pure) **und** kleinem E2E gegen die lokale Test-Komga verifizieren.
   → `@.claude/rules/roadmap-and-invariants.md`

## Module (Schnitt = Architektur)

| Modul | Inhalt | Darf NICHT abhängen von |
|---|---|---|
| `domain` | Modelle, UseCases, Repo-/Render-/Eink-**Interfaces** | Android, Netz, irgendeiner Quelle, `source-api` |
| `source-api` | Naht-A-Quellen-Vertrag (`MediaSource` & Co., `SourceManager`, `SourceId`) — wird von `plugin-api` re-exportiert, **noch nicht eingefroren** | Android, Netz, UI; hängt nur an `domain` |
| `source-komga` · `source-opds` | konkrete `MediaSource`-Impls | UI, anderen Quellen |
| `source-local` | `LocalSource` (`SourceKind.LOCAL`, id 0): liest einen vom Nutzer gewählten SAF-Geräteordner als `BrowsableSource`. **Android-Library** (braucht `Context`/SAF), **renderer-frei** — CBZ-Seiten via `openPage` (roher Zip-Eintrag, `java.util.zip`), PDF/CBR/EPUB whole-file via `downloadFile`. Pure Logik (Naming/ComicInfo-Parser/CBZ/Mapper) JVM-unit-getestet | UI, anderen Quellen, **`render-core`** |
| `plugin-api` | Plugin-ABI-Vertrag (`SourcePlugin`, `ConfigSchema`, `PluginAbi`, `PluginMetadata`); re-exportiert `source-api` via `api()` | Android, Netz, UI; hängt nur an `source-api`/`domain` |
| `plugin-sdk` | geshadetes Single-Jar (plugin-api+source-api+domain, keine Relocation) — das **eine** `compileOnly`-Artefakt für externe Plugins (`com.komgareader:plugin-sdk`) | — (nur Build-Aggregat) |
| `plugin-host` | Runtime-Loader (`PluginHost`, `AbiGate`, `DiscoveredPlugin`, TOFU-Pinning, `PathClassLoader`) | `app`-Schicht (UI); wird von Hilt in `app` bereitgestellt |
| `render-core` | `Document`/`DocumentFactory` + MuPDF-JNI (Naht B) | UI |
| `eink-onyx` | `OnyxEinkController` (Onyx-SDK, HW-gated) | UI, Quellen |
| ~~`guided-view`~~ | **entfernt** — Panel-Erkennung ist jetzt die externe Lib **comic-cutter** (`io.github.gabriel-graf:comic-cutter-jvm` + `comic-cutter-onnx-jvm`, Paket `com.panela.comiccutter.*`), verdrahtet über `PanelSourceProvider` (`app`). Geometrisch (`GeometricPanelSource`) per Default, ML (`MlPanelSource`+ONNX) bei `PANEL_MODEL`-Plugin (Naht B) | — |
| `ui-api` | UI-Pack/Slot/Shell/Theme/Icon-**Verträge** (Capability-Surfaces, Slot-typealias, `UiSlotPack`/`ResolvedSlots`/`UiSlots`, `AppShellState`/`ShellPack`, `UiPack` + Theme-Packs + `DesignTokens`, Icon-Stack) + die **entkoppelten Built-ins** (Theme-Packs, Icon-Glyphen). DAG `domain → ui-api → app` (A1) | ViewModels, Netz, Quellen; hängt nur an `domain` + Compose |
| `data` | Room-Impls, Sync-Queue, Download-Manager | UI |
| `app` | Compose-UI, ViewModels, DI, Reader-Host, **gekoppelte Default-Renderer** (`DefaultSlots`/`DefaultHeader`/`DefaultShell`/`PhoneShell`/`buildSettingsSections`, `Theme.kt`-Host) | — (oberste Schicht) |

## Detailregeln

- `@.claude/rules/big-picture-and-goals.md` — Vision, Langzeit-Ziele, Gos/Nogos; prüf jedes Feature dagegen, nicht nur gegen den heutigen Code
- `@.claude/rules/architecture-seams.md` — die zwei Nähte (Soll vs. Ist), Modulgrenzen, was nie umgangen wird
- `@.claude/rules/source-agnostic-integration.md` — Naht A in der Verdrahtung durchsetzen: VMs über `SourceManager`, Lesen über `openPage`, nie konkrete Quelle/Provider
- `@.claude/rules/source-extensibility.md` — neue Server/Metadaten quellen-agnostisch hinzufügen (Schritt-für-Schritt)
- `@.claude/rules/eink-design-language.md` — die E-Ink-Designsprache verbatim
- `@.claude/rules/roadmap-and-invariants.md` — Phasen P1–P4, was noch kommt, was nicht aus den Augen verloren werden darf
- `@.claude/rules/shared-structure-before-variants.md` — Gemeinsames vor der N-ten Variante extrahieren (schon beim Planen mitdenken)

> Lizenz: **AGPL-3.0-or-later** (MuPDF ist AGPL; comic-cutter ist ebenfalls AGPL-3.0). Jede Verteilung legt den Quellcode offen. `LICENSE`/`NOTICE` im Root.
