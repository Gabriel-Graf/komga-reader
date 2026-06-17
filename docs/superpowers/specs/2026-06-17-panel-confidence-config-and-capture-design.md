# Panel-Confidence-Config + Misdetection-Capture — Design-Spec

**Datum:** 2026-06-17
**Branch:** `feat/panel-confidence-config` (komga-reader)
**Status:** komga-reader side BUILT (Tasks 4–15, branch `feat/panel-confidence-config`). ComicCutter 0.4.0 released. KomgaReaderPlugins plugin (`komga-panel-model-yolo`, Task 17) still pending. Spannt **vier Repos**: ComicCutter (Lib), komga-reader (App + `plugin-api`),
KomgaReaderPlugins (`komga-panel-model-yolo`-Plugin).
**Baut auf:** `2026-06-12-data-plugin-foundation-design.md` (generisches `PluginCategory` +
`discoverDataPlugins`) und `2026-06-12-data-plugin-features-design.md` (`PluginConfigForm`,
data-only-Muster).

## Problem & Motivation

Drei zusammenhängende Wünsche rund um das `PANEL_MODEL`-Plugin (YOLO-Panel-Erkennung):

1. **Einstellbare Mindest-Confidence.** Heute ist der Detektor-Schwellwert hart verdrahtet:
   `PanelSourceProvider.kt:40` → `MlFilter(0.25f, 0.7f, 0.0f, null)`. Der Nutzer will die
   Mindest-Confidence für ein gültiges Panel selbst setzen — und zwar über ein **Zahnrad am
   Plugin** im Plugins-Tab (data-only Plugins haben heute nur Info + Deinstall, kein Zahnrad).
2. **Debug-Overlay mit Confidence + Panel-Nummer.** Das vorhandene Panel-Debug-Overlay
   (`guidedPanelOverlay`) zeichnet nur Rechtecke. Es soll je Panel oben-links die **Nummer**
   (Reading-Order-Index) **und die Confidence** zeigen.
3. **Misdetection-Capture für PC-Labeling.** Statt einen Box-Editor auf E-Ink nachzubauen
   (Latenz, Touch-Präzision, dupliziert das vorhandene `mllabeltool`), bekommt das Reader-Overlay
   einen **1-Tap-Capture-Button**: kopiert die aktuelle Seite **plus die aktuell erkannten
   (falschen) Panels** in ein konfigurierbares Zielverzeichnis. Gelabelt + retrained wird auf dem
   PC mit dem bestehenden `mllabeltool` (lädt Ordner, zeigt Sidecar als Prediction-Baseline,
   exportiert YOLO-txt) → `komga-yolo-spike` (Train + INT8-Quantize).

### Architektur-Realität (warum vier Repos)

- Der Schwellwert ist **Host-Verhalten**, kein Plugin-Datum: das Plugin liefert nur die
  Modell-Bytes (`hasCode="false"`), der Host fährt die Inferenz. `MlFilter`s Confidence ist
  bereits ein **Runtime-Konstruktor-Parameter** → Wertänderung ist billig.
- `com.panela.comiccutter.PanelRect` trägt **nur** `x/y/width/height`, **keinen Score** (verifiziert
  per `javap` an `comic-cutter-jvm-0.3.1.jar`). Die Overlay-Confidence-Anzeige braucht daher eine
  **ComicCutter-Lib-Änderung** (Score am Panel durchreichen), neu auf JitPack publishen,
  Version in komga-reader bumpen.
- Data-only Plugins haben heute **keinen** Config-Pfad: `ConfigSchema`/`PluginConfigForm` sind an
  Code-`SourcePlugin`s gebunden; `FieldType` kennt nur `TEXT/SECRET/URL/BOOL`, **kein NUMBER**.

## Scope

### Im Spec
1. **ComicCutter**: `PanelRect.score: Float`; ML-Source füllt ihn; Geometric-Source setzt z.B. `1.0`.
   Release `0.4.0` (JitPack).
2. **plugin-api**: `FieldType.NUMBER`; `ConfigField` um `min/max/step` (nullable) erweitert.
3. **Generische Data-Plugin-Config** (Host): Plugin deklariert Schema via `config.json`-Asset +
   Manifest-Key `DATA_CONFIG`; Host liest es resource-only; persistiert Werte in Room
   (`plugincfg:<pkg>:<key>`); Zahnrad an `DataPluginRow`; Config-Form mit **Slider**-Renderer.
4. **PanelSourceProvider** liest gespeicherte Confidence statt `0.25`; rebuildet die Source bei
   Wertänderung.
5. **Debug-Overlay**: je Panel `#<index> <score>` oben-links.
6. **Capture-Loop**: SAF-Ordnerpicker in Settings (persistierte Tree-URI); Capture-Icon-Button im
   Reader-Overlay; kopiert Seitenbild + Sidecar-JSON (mllabeltool-Prediction-Format).
7. **komga-panel-model-yolo**: `config.json`-Asset + `DATA_CONFIG`-Meta + ABI-Bump; `repo.json`.

### NICHT im Spec (YAGNI)
- NMS/IoU einstellbar (bleiben hardcoded).
- Box-Editor / In-Reader-Labeling.
- Retraining-Automatisierung (bleibt manuell PC: mllabeltool → komga-yolo-spike).
- Generischer Slider für andere Data-Plugins außer dem PANEL_MODEL (Mechanik ist generisch, aber
  nur dieses Plugin liefert vorerst ein `config.json`).

## Entwurf

### A · ComicCutter — Score am Panel (Lib, Release 0.4.0)

```kotlin
data class PanelRect(
    val x: Int, val y: Int, val width: Int, val height: Int,
    val score: Float = 1.0f,   // additiv, Default 1.0 → bestehende Aufrufer brechen nicht
)
```
- `MlPanelSource`/`MlFilter`: den nach NMS bekannten Score in `PanelRect.score` schreiben.
- `GeometricPanelSource`: `score = 1.0f` (kein Confidence-Begriff → „sicher").
- `ReadingOrder.sort` bleibt unverändert (sortiert nach Geometrie, Score wandert mit).
- **Release:** Tag `0.4.0` → JitPack baut. komga-reader bumpt
  `com.github.Gabriel-Graf.ComicCutter:comic-cutter-{jvm,onnx-jvm}:0.4.0`.
- **Lib-Tests:** ML-Source füllt Score (>0, ≤1); Geometric-Source = 1.0; `copy`-Default greift.

### B · plugin-api — NUMBER-FieldType

```kotlin
enum class FieldType { TEXT, SECRET, URL, BOOL, NUMBER }

data class ConfigField(
    val key: String, val label: String, val type: FieldType,
    val required: Boolean = false, val default: String? = null,
    val min: Double? = null, val max: Double? = null, val step: Double? = null, // nur NUMBER
)
```
- Additiv; bestehende Code-Plugin-`configSchema()` (Kavita/Calibre) unberührt.
- ABI: `PluginAbi.VERSION` additiv erhöhen (neue Capability), `MIN_SUPPORTED` unverändert.

### C · Host — generische Data-Plugin-Config

**Deklaration (kein Code).** Neuer Manifest-Key parallel zu `DATA_ASSET`:
```kotlin
const val DATA_CONFIG = "com.komgareader.plugin.DATA_CONFIG"  // Asset-Name der config.json
```
`config.json` (Plugin-Asset):
```json
{ "fields": [
  { "key": "min_confidence", "label": "Mindest-Confidence", "type": "NUMBER",
    "min": 0.1, "max": 1.0, "step": 0.05, "default": "0.25" } ] }
```

**Discovery + Parse.** `discoverDataPlugins(category)` liefert zusätzlich den optionalen
`configJson` (resource-only via `createPackageContext(pkg, 0)`, gleicher Pfad wie Modell-Bytes).
Reiner Parser in `:data`: `parseConfigSchema(json): ConfigSchema?` (org.json, NUMBER mit
min/max/step; unbekannte Felder überspringen).

**Persistenz.** Werte als Key-Value in der bestehenden Room-`SettingsDao`, Key-Schema
`plugincfg:<packageName>:<fieldKey>`. Neue `SettingsRepository`-Methoden:
```kotlin
fun pluginConfig(pkg: String, key: String): Flow<String?>
suspend fun setPluginConfig(pkg: String, key: String, value: String)
```
Effektiver Wert = gespeichert ?: `ConfigField.default`. Kein neuer Table (YAGNI, folgt
Color-/Reader-Preset-Linie „kein Room-Table, wenn KV reicht").

**UI.** `PluginsScreen.DataPluginRow`: **Zahnrad sichtbar, wenn das Plugin ein `config.json`
trägt** (Schema nicht-leer). Tap → `PluginConfigForm` wiederverwendet, erweitert um einen
**Slider-Renderer** für `NUMBER` (themed Custom-Slider, E-Ink-Animation host-gegatet; min/max/step
aus dem Feld, Live-Wert-Label). Speichern → `setPluginConfig`.

### D · PanelSourceProvider — Confidence lesen

```kotlin
val conf = settings.pluginConfig(PANEL_YOLO_PKG, "min_confidence").first()?.toFloatOrNull() ?: 0.25f
MlPanelSource(OnnxModelRunner(bytes), MlFilter(conf, 0.7f, 0.0f, null))
```
- `PanelSourceProvider` cached die Source; bei Confidence-Änderung **invalidieren** (Source
  neu bauen), damit die nächste Detection den neuen Wert nutzt. Confidence-Flow beobachten oder
  beim Reader-Eintritt neu lesen (einfachste korrekte Variante: invalidieren, wenn der gelesene
  Wert vom zuletzt gebauten abweicht).

### E · Debug-Overlay — Index + Score

- `ComicPageLoader.PageDetection.panels: List<PanelRect>` trägt nach (A) den Score.
- Overlay (Reader-Compose, hinter `guidedPanelOverlay`): je Panel oben-links Text
  `#${i + 1} ${"%.2f".format(panel.score)}` (z.B. `#1 0.83`). Index = Position in der
  Reading-Order-sortierten Liste (1-basiert). Klein, kontrastreich, E-Ink-tauglich.

### F · Misdetection-Capture (PC-Labeling-Loop)

**Settings (SAF).** Neue Reader-/Tools-Settings-Zeile „Misdetection-Zielordner":
- Button → `ACTION_OPEN_DOCUMENT_TREE`; Ergebnis-Tree-URI via
  `contentResolver.takePersistableUriPermission(...)` festhalten; URI-String in Room
  (`misdetection_dir`). Anzeige des gewählten Ordnernamens; „Entfernen" möglich.
- **Kein** roher Dateipfad (Scoped Storage) — Schreiben via `DocumentFile`/`ContentResolver`.

**Reader-Overlay-Button.** Icon-Button im Comic-Reader-Overlay, **nur sichtbar, wenn
`misdetection_dir` gesetzt**. Tap (auf aktueller Seite) schreibt in den Tree:
- Bild: `<serie>_<bookId>_p<seite>.png` (Original-/Render-Bitmap der Seite).
- Sidecar: `<…>.json` im **mllabeltool-Prediction-Format**, **pixel-space**:
  ```json
  { "items": [ { "box": [x, y, w, h], "label": "panel", "score": 0.83 } ] }
  ```
  Boxen = aktuelle (falsche) Detektion, skaliert auf Originalbild-Pixel. Toast/Snackbar bestätigt.
- Dateinamen kollisionssicher (Serie+Buch+Seite); existiert die Datei, Suffix `_2` etc.

**PC-Seite (Doku, kein Code hier).** `mllabeltool` öffnet den Ordner → Sidecar erscheint als
Prediction-Baseline → korrigieren → Export YOLO-txt → `komga-yolo-spike` Train + INT8-Quantize →
neues `best.int8.onnx` ins Plugin. README-Abschnitt im Plugin verlinkt den Loop.

### G · komga-panel-model-yolo (KomgaReaderPlugins-Repo)

- `src/main/assets/config.json` (Schema aus C).
- Manifest: `<meta-data android:name="com.komgareader.plugin.DATA_CONFIG" android:value="config.json" />`;
  `ABI_VERSION` auf die neue ABI heben.
- `repo.json`: ABI/Version des Plugins nachziehen.
- README: Misdetection-Capture → mllabeltool → Retrain-Loop dokumentieren.

## Datenfluss

```
Plugin(config.json) ──discoverDataPlugins──> Host parseConfigSchema ──> ConfigSchema(NUMBER)
        │                                                                      │
   Zahnrad(DataPluginRow) ──> PluginConfigForm(Slider) ──setPluginConfig──> Room(plugincfg:…)
        │                                                                      │
PanelSourceProvider.pluginConfig(min_confidence) ──> MlFilter(conf) ──> MlPanelSource.detect()
        │                                                                      │
   ComicPageLoader.detect ──> PageDetection(panels: PanelRect{score}) ──> ReadingOrder.sort
        │                                                  │
   Overlay #idx score (guidedPanelOverlay)        Capture-Button ──> DocumentFile(png + sidecar json)
```

## Tests (TDD-Pflicht)

- **ComicCutter (Lib):** ML-Source schreibt Score (0<s≤1); Geometric=1.0; `copy`-Default.
- **plugin-api:** `ConfigField`-NUMBER mit min/max/step konstruierbar (Daten-Klasse, trivial).
- **:data Parser (rein):** `parseConfigSchema` — NUMBER mit/ohne min/max/step; default;
  unbekannter Typ übersprungen; leeres/fehlerhaftes JSON → `null`/leer.
- **SettingsRepository:** `pluginConfig`/`setPluginConfig` Round-Trip; Default-Fallback.
- **PanelSourceProvider:** gelesene Confidence landet in `MlFilter` (über Test-Doubles/Naht);
  Invalidierung bei Wertänderung.
- **Capture (rein):** Sidecar-JSON-Serialisierung (PanelRect→pixel-space items, Skalierung);
  Dateinamens-Kollisions-Suffix. SAF-I/O selbst = dünne Shell, Integrationstest `assumeTrue`-gegated.
- **E2E (`assumeTrue`-gegated wie Color-Preset):** Plugin mit `config.json` → Discovery → Schema →
  Form → `setPluginConfig` → Wert wirkt in PanelSourceProvider.

## Architektur-Bezug (verbindliche Rules)

- `architecture-seams.md` — Discovery/Config-Lesen bleibt im `plugin-host`; `domain` netz-/I/O-frei;
  SAF-I/O in der App-Shell.
- `plugin-domain` — data-only-Config = additive Capability (Schema-via-Asset, kein Code).
- `source-extensibility.md` — neue Capability = Parser + Persistenz + UI, kein Kern-Umbau.
- `eink-design-language.md` + `animation-gating.md` — Slider/Picker host-gegatet animiert.
- `shared-structure-before-variants.md` — `PluginConfigForm` für Code- und Data-Plugins geteilt.
- `docs-match-code` — diese Spec + `architecture-seams.md` im selben Commit nachziehen, sobald gebaut;
  `data-provenance` (global) — Trainings-/Capture-Daten-Herkunft im Plugin-README halten.

## Entscheidungen (festgehalten)

| Fork | Entscheidung |
|---|---|
| Confidence-UI | Generische Data-Plugin-Config (Zahnrad), `config.json`-Asset + `DATA_CONFIG`-Meta. |
| Schwellwerte | Nur Confidence, Slider **0.1–1.0**, Default 0.25, step 0.05. NMS/IoU bleiben hardcoded. |
| Config-Persistenz | Room-KV `plugincfg:<pkg>:<key>`, kein neuer Table. |
| Overlay | `#<reading-order-index> <score 2-Nachkomma>` oben-links je Panel. |
| Score-Quelle | ComicCutter `PanelRect.score` (Lib-Release 0.4.0, additiv Default 1.0). |
| Capture-Inhalt | Bild **+** Sidecar-JSON (mllabeltool-Prediction-Format, pixel-space). |
| Capture-Ziel | SAF-Tree-URI (persistiert), **kein** roher Pfad. Button nur bei gesetztem Ordner. |
| Labeling-Ort | PC (mllabeltool → komga-yolo-spike). Kein In-Reader-Editor. |
| Repos | ComicCutter (Lib) + komga-reader (App+plugin-api) + KomgaReaderPlugins (Plugin). |
