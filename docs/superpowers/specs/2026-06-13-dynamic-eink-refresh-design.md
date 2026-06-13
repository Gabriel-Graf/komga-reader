# Dynamischer E-Ink-Refresh- & Farbmodus pro Lese-Kontext

Datum: 2026-06-13
Status: Design (genehmigt) → Plan ausstehend
Naht: B (Render & E-Ink, `architecture-seams.md`)

> Code, KDoc und Kommentare in der Implementierung sind **englisch** (Projektkonvention).
> Dieses Spec-Dokument ist deutsch (konsistent mit den übrigen Specs).

## Problem

Der heutige App-getriebene Refresh-Pfad ist schlecht abgestimmt und deshalb per Default
**aus** (`deviceManagedRefresh = true`). Konkret:

- `OnyxEinkController.enterFastMode()` erzwingt app-weit den Low-Level-Modus `UpdateMode.DU`
  (Speed) via `applyAppScopeUpdate(...)`.
- Der `RefreshScheduler` (`@Deprecated`) promotet nach N Partials einen `UpdateMode.GC`
  (Voll-/Ghosting-Clear). Dieser GC-Sprung **beim Lesen** wirkt wie der „Regal"-Modus und
  stört den Nutzer.
- Der Nutzer bevorzugt feste Onyx-EinkWise-Modi je nach Inhalt (z. B. **HD** beim Lesen,
  **Speed** beim Webtoon-Scrollen) statt einer App-Heuristik, die ihm einen Modus aufzwingt.

Die Onyx-internen EinkWise-Modi sind klar besser abgestimmt als unsere Heuristik. Da die
Wiederholrate ohnehin eine **E-Ink-Treiber-Einstellung** ist (Naht B), binden wir die
Onyx-EinkWise-Modi direkt ein und schalten sie **automatisch** je nach Lese-Kontext —
statt für den Nutzer zu raten, was ihm gefällt.

## Ziel

Der Nutzer definiert **pro Lese-Kontext** (Homescreen, Manga/paged, Webtoon, geführter
Comic, Roman) eine **Kombination aus Refresh-Modus + Farbmodus**. Die App schaltet diese
Kombination **automatisch**, sobald der jeweilige Kontext sichtbar wird. Keine manuelle
Umschaltung mehr, keine App-Heuristik, die einen Modus erzwingt.

## SDK-Verifikation (Ist, gegen `onyxsdk-device:1.3.5` AAR geprüft)

Beide Achsen sind real über `com.onyx.android.sdk.api.device.epd.EpdController` schaltbar —
**kein Phantom-API** (`docs-match-code`):

**Refresh-Modus (app-weit, high-level):**
- `EpdController.setAppScopeRefreshMode(UpdateOption)` / `getAppScopeRefreshMode()`
- `UpdateOption`-Werte = exakt die EinkWise-Modi:

  | `UpdateOption` | Boox-Name | Einsatz |
  |---|---|---|
  | `NORMAL` | HD | scharfes Lesen, Homescreen |
  | `FAST_QUALITY` | Balanced | Kompromiss |
  | `REGAL` | Regal | Text, Anti-Ghosting |
  | `FAST` | Speed | Scrollen (Webtoon) |
  | `FAST_X` | Ultra/A2 | sehr schnell, niedrige Qualität |

  App-scope heißt: betrifft **nur unsere App**, das OS isoliert andere Apps und stellt beim
  App-Verlassen selbst wieder her — kein Cleanup nötig.

**Onyx-System-Farbmodus:**
- `EpdController.enableColorAdjust()` / `disableColorAdjust()` (Farb-Anhebung an/aus)
- `EpdController.applyMonoLevel(int)` / `applyColorFilter(int)` (Stufen, optional — in v1
  nicht zwingend)
- `GlobalContrastController.setGlobalContrast(int)` (separat, nicht Teil von v1)

  ⚠️ **Konflikt-Risiko:** Onyx-Farbanhebung läuft *zusätzlich* zu unserem `ColorProfile`-Filter
  (Cover/Seiten-Farbkorrektur). Doppelverarbeitung möglich. Deshalb: **Default jeder Achse =
  „System nicht anfassen"**; Onyx-Farbe ist opt-in.

**Nicht verwendet:** `SimpleEACManage` (EAC = per-App-EinkWise-Registry des Launchers) wird
**nicht** gebraucht — `setAppScopeRefreshMode` steuert unsere App direkt zur Laufzeit (YAGNI).

## Designentscheidung: voll dynamischer Deskriptor (geräte-agnostisch)

Die Modi werden **nicht** als feste Domain-Enum modelliert, sondern als **offene, vom
Controller gemeldete Liste**. Ein künftiges Geräte-Plugin meldet seine eigenen Modi; die UI
rendert, was der aktive Controller meldet. Das ist die maximal agnostische Form (Naht B,
Plugin-Zukunft) — bewusst gewählt über die kompaktere Enum-Variante.

**Folge:** Die Domain kann die sinnvollen Defaults nicht kennen (HD/Speed sind Onyx-IDs).
Deshalb liefert der **Controller** auch die geräte-spezifischen Per-Kontext-Defaults; der
Nutzer überschreibt nur. So bleiben `domain` und `app` frei von Onyx-IDs.

## Architektur

### 1. Domain-Verträge (pure, kein Onyx-Typ) — `domain/eink/`

```kotlin
// What is currently on screen — drives which profile is applied.
enum class EinkContext { HOME, PAGED, WEBTOON, COMIC, NOVEL }

// A device-advertised mode option. `label` is the device default display name;
// the app may override it via i18n for known built-in ids.
data class EinkModeOption(val id: String, val label: String)

// null on an axis = "leave device/system default untouched".
data class EinkContextProfile(
    val refreshModeId: String? = null,
    val colorModeId: String? = null,
)
```

`EinkCapabilities` (bestehend) wird erweitert:
```kotlin
val refreshModes: List<EinkModeOption>   // empty = axis unsupported → UI hides it
val colorModes:   List<EinkModeOption>
```

`EinkController` (bestehend) wird erweitert:
```kotlin
fun applyRefreshMode(id: String?)        // null/unknown id = graceful no-op
fun applyColorMode(id: String?)
fun defaultProfile(ctx: EinkContext): EinkContextProfile   // device-specific sane defaults
```

**Pure Resolver** (domain, unit-testbar):
```kotlin
fun resolveEinkProfile(
    ctx: EinkContext,
    userOverrides: Map<EinkContext, EinkContextProfile>,
    controller: EinkController,   // for defaultProfile(ctx)
): EinkContextProfile
```
Vorrang: **User-Override > Controller-Default > „nicht anfassen" (null)**. Pro Achse einzeln
(ein gesetzter Refresh-Override + nicht gesetzter Farb-Override → Farbe fällt auf Default).

### 2. `eink-onyx` — einzige Stelle mit Onyx-Wissen

`OnyxEinkController`:
- `refreshModes` = 5 Optionen mit stabilen IDs (`"hd"`, `"balanced"`, `"regal"`, `"speed"`,
  `"ultra"`) → `applyRefreshMode(id)` mappt auf `UpdateOption` und ruft
  `setAppScopeRefreshMode(...)`. Unbekannte ID → no-op.
- `colorModes` = `"system"` / `"color"` / `"mono"` → `applyColorMode(id)` ruft
  `enableColorAdjust()` bzw. `disableColorAdjust()` (+ ggf. `applyMonoLevel`). `"system"`/null
  = nichts tun.
- `defaultProfile(ctx)`:
  - HOME / PAGED / COMIC / NOVEL → refresh `"hd"`, color `"system"`
  - WEBTOON → refresh `"speed"` (Scroll), color `"system"`
- `capabilities` meldet die obigen Listen.

`NoOpEinkController` (Nicht-Boox/Emulator): `refreshModes`/`colorModes` **leer**, `applyXxx`
= no-op, `defaultProfile` = leeres Profil. → Settings-Achse wird automatisch versteckt.

### 3. App-Shell — dynamisches Schalten — `app/data/`

`EinkContextController` (`@Singleton`):
- hält den aktiven `EinkContext` (intern), liest User-Overrides aus `SettingsRepository`.
- `setContext(ctx)` → `resolveEinkProfile(...)` → `controller.applyRefreshMode(profile.refreshModeId)`
  + `controller.applyColorMode(profile.colorModeId)`.
- Reader-Screens melden ihren Kontext beim Eintritt (generalisiert das heutige
  `EinkReaderEffect` → `EinkContextEffect(ctx)`), Home meldet `HOME`. Mapping
  `ViewerType`/`ViewerMode` → `EinkContext` ist trivial.
- App-scope-Modus ist app-weit → genau **ein** Aufruf pro Kontextwechsel.

### 4. Persistenz + Settings-UI

- User-Overrides als **ein JSON-Blob** in den Room-Settings: Key `eink_context_profiles`,
  Wert = `Map<EinkContext, EinkContextProfile>` serialisiert. Pure Parser/Serializer in
  `data` (kotlinx.serialization, wie übrige Settings) — `domain` bleibt serialisierungsfrei.
  **Keine Room-Migration** (nur ein neuer Settings-Key im bestehenden Key-Value-Table).
- `SettingsRepository`: `val einkContextProfiles: Flow<Map<EinkContext, EinkContextProfile>>`
  + `suspend fun setEinkContextProfile(ctx, profile)`.
- Settings-Sektion **„E-Ink Dynamik"**: Matrix `EinkContext` × { Refresh-Dropdown,
  Farb-Dropdown }. Dropdowns aus `capabilities.refreshModes`/`colorModes` befüllt, plus
  Eintrag **„Gerät entscheidet"** (= null). Labels: i18n für bekannte Built-in-IDs, sonst
  der vom Deskriptor gemeldete `label`. E-Ink-Designsprache (flach, Border, keine
  ungegateten Animationen — `eink-design-language.md`, `animation-gating.md`).
- Sektion nur sichtbar, wenn `capabilities.refreshModes` nicht leer (Boox).

### 5. Altlasten entfernen (ersetzt durch das neue Modell)

- `RefreshScheduler` (`@Deprecated`, domain) — entfernen.
- `OnyxRefresher` GC-Promotion (`fullRefreshIfNeeded`/`fullRefreshNow`/`deviceManaged`/
  `GHOST_CLEAR_INTERVAL`) — entfernen; der Refresh läuft jetzt allein über den vom Gerät
  gewählten EinkWise-Modus.
- `enterFastMode()`/`exitFastMode()` (hartkodiertes `UpdateMode.DU`) — entfernt; ersetzt
  durch kontext-gesteuertes `applyRefreshMode`.
- Setting `deviceManagedRefresh` + Key `device_managed_refresh` + UI-Toggle „E-Ink Refresh" —
  entfernen. (Room-Key bleibt als unbenutzte Leiche im Table stehen — **kein** destruktiver
  Migrations-Eingriff, vgl. `room-migration-destructive-pitfall`.)
- `Viewer.refreshScheduler` aus dem `Viewer`-Vertrag entfernen; Reader-Call-Sites
  (`PagedReaderScreen`, `WebtoonReaderScreen`, `ComicReaderScreen`, `NovelReaderScreen`),
  die den Scheduler/`OnyxRefresher` triggern, entfallen.

## Verifikation

- **Unit (pure, domain):** `resolveEinkProfile` — Override schlägt Default, Default schlägt
  null, je Achse einzeln; leere Overrides; unbekannter Kontext. JSON-Parser/Serializer
  (data): Round-Trip gesetzt **und** leer/teilweise.
- **Onyx (manuell, echte Boox):** je Kontext den gewählten Modus sichtbar verifizieren (HD
  beim Lesen kein GC-Sprung; Speed beim Webtoon-Scroll; Farb-Toggle). Nicht emulierbar
  (`roadmap-and-invariants`).
- **Emulator (`eink_test`):** NoOp-Pfad crasht nicht; Settings-Sektion versteckt sich auf
  Nicht-Boox bzw. rendert + persistiert (Overrides überleben Neustart).

## Nicht-Ziele (v1)

- Onyx-`GlobalContrast`, `applyColorFilter`/`applyMonoLevel`-Stufen (nur an/aus in v1).
- Onyx-Layout-Achse (unklar definiert, raus).
- EAC/per-App-Launcher-Registry (`SimpleEACManage`).
- Migration unseres `ColorProfile`-Filters in dieses Modell (bleibt separate Farbkorrektur).
- Externes Geräte-Plugin selbst (nur die agnostische Naht steht bereit dafür).

## Dokumentations-Nachzug (`docs-match-code`, gleicher Commit-Stream)

`architecture-seams.md` (Naht B), ggf. `big-picture-and-goals.md` (Geräteklassen/Refresh)
und `PROJECT-STATUS.md` auf den neuen Ist-Stand ziehen, sobald gebaut.
