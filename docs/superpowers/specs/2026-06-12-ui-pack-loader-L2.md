# L2: Externer UI-Pack — installierbarer deklarativer UI-Deskriptor (data-only Plugin) — Design-Spec

**Stand:** 2026-06-12 · **Status:** Design · **Sub-Projekt L2** (Schlussstein) der Roadmap
`…complete-ui-modularity-roadmap.md`. Baut auf L1 (`DeclarativeShell`/`ShellDescriptor`), I1 (`ActiveIconPack`),
Theme-Pack (`DesignTokens`).

> **Self-contained.** Vorher lesen: `big-picture-and-goals.md` (ui-modularity → „Community **installiert** eine
> UI"; **deklarativ, kein arbiträres Compose**; E-Ink-Invarianten **host-erzwungen**), `architecture-seams.md`
> (Plugin-Loader/data-only-Mechanik, Shell-/Theme-/Slot-Nähte), `source-extensibility.md`. Vorbild-Kategorie:
> **LANGUAGE** (Laufzeit-Override, aktiv-Auswahl per Setting) — UI-Pack ist analog.

## 1. Ziel & Prinzip

Ein **extern installierbarer UI-Pack**: ein **data-only APK** (kein Code, nur JSON-Asset), das einen
**deklarativen Deskriptor** liefert und damit Teile der Oberfläche ersetzt — **jede Sektion optional**
(Subset-Packs: nur Icons, nur Shell, nur Theme oder Kombinationen; fehlende Sektion → Host-Default). Das ist
der „Community installiert eine UI"-Endzustand, **streng deklarativ** (kein Plugin-Compose, kein Host-Rechte-
Crash-Risiko) und nutzt die **bestehende** data-only-Plugin-Infrastruktur (`discoverDataPlugins`, Repo-Browser,
Fingerprint-verifizierte Installation, Prune) **1:1 wieder** — wie COLOR_PRESET/READER_PRESET/LANGUAGE.

**Drei Sektionen (alle optional):**
- **shell** — `navStyle` (BOTTOM_BAR/DRAWER): überschreibt den Form-Faktor-Default (L1 `descriptorFor`).
- **icons** — Remap unter den **bestehenden** Lucide-Glyphen (`IconKey`→`IconKey`): kein SVG-Import (YAGNI I1).
- **theme** — Token-Override (Akzent-Hex, Eckradius) auf das geräteklassen-gewählte Pack.

> **E-Ink-Invariante host-erzwungen (zentral):** der **Akzent-Override** wird NUR angewandt, wenn die
> Geräteklasse Akzentfarbe erlaubt (`DisplayBehavior.allowsAccentColor`) — auf **mono E-Ink ignoriert** (bleibt
> Schwarz, User-Entscheidung 2026-06-10). Bewegung/Akzent-Policy bleibt Host, nie Pack. Eckradius/Shell/Icons
> sind invariant-neutral und gelten immer.

## 2. ABI / Kategorie (plugin-api, plugin-host — additiv, kein Bruch)

- `PluginCategory` (`plugin-api/.../PluginCategory.kt`): Konstante **`UI_PACK`** additiv ergänzen
  (`{ COLOR_PRESET, READER_PRESET, LANGUAGE, UI_PACK }`). **Kein PluginAbi-Bump nötig** — die Kategorie ist der
  Diskriminator; UI-Pack-APKs deklarieren `ABI_VERSION=2` (im Gate `MIN_SUPPORTED=1..VERSION=2`). `PluginAbi`
  bleibt unverändert (`VERSION=2`).
- **Kein** ui-api-Code-ABI-Freeze in L2: data-Packs linken **kein** ui-api (`compileOnly`) — sie sind reines
  JSON. Der Vertrag ist das **JSON-Schema** (unten) + die data-Plugin-ABI. (Der ui-api-Code-ABI-Freeze bliebe
  nur für künftige **Code**-UI-Packs nötig — nicht Teil von L2, bleibt Soll.)
- `discoverDataPlugins(PluginCategory.UI_PACK)` funktioniert **ohne Host-Änderung** (generisch). Optional dünner
  Wrapper `discoverUiPackPlugins()` analog `discoverColorPresetPlugins` — **nicht** nötig (Parser lebt in data).

## 3. JSON-Schema (der Vertrag) + Manifest

Asset `ui_pack.json` (Beispiel; alle drei Sektionen optional):
```json
{
  "abiVersion": 2,
  "shell":  { "navStyle": "DRAWER" },
  "icons":  { "Home": "Library", "Settings": "Palette" },
  "theme":  { "accent": "#3A5BC7", "cornerRadius": 4 }
}
```
- `icons`: Map **IconKey-Name → IconKey-Name** (beide aus dem 41er-`IconKey`-Enum). Ungültige Namen → Eintrag
  verworfen (kein Crash). Bedeutung: „rendere die `Home`-Semantik mit dem Glyphen der `Library`-Semantik".
- `theme.accent`: Hex (`#RRGGBB` oder `RRGGBB`). `theme.cornerRadius`: Int dp (geclampt 0..32).

APK-Manifest (data-only, `android:hasCode="false"`):
```xml
<meta-data android:name="com.komgareader.plugin.DATA_CATEGORY" android:value="UI_PACK" />
<meta-data android:name="com.komgareader.plugin.DATA_ASSET"    android:value="ui_pack.json" />
<meta-data android:name="com.komgareader.plugin.ABI_VERSION"   android:value="2" />
```

## 4. Datenmodell + Parser (pure, kein Compose)

**`domain/model/UiPackSpec.kt`** (pure Primitive — wie `ReaderPreset`-Präzedenz; **keine** ui-api/Compose-Typen,
damit `data`/`domain` rein bleiben):
```kotlin
data class UiPackSpec(
    val packageName: String,
    val displayName: String,
    val abiVersion: Int,
    val navStyle: String? = null,                 // "BOTTOM_BAR" | "DRAWER" | null
    val iconRemap: Map<String, String> = emptyMap(), // IconKey-Name -> IconKey-Name
    val accentHex: String? = null,
    val cornerRadiusDp: Int? = null,
) {
    /** true, wenn der Pack mindestens eine Sektion liefert (sonst ist er wirkungslos). */
    val hasAnyOverride: Boolean
        get() = navStyle != null || iconRemap.isNotEmpty() || accentHex != null || cornerRadiusDp != null
}
```

**`data/plugin/UiPackParser.kt`** (analog `LanguageSpecParser`/`ReaderPresetParser`, `org.json`):
`fun parseUiPackSpec(json: String, packageName: String, displayName: String, manifestAbi: Int): UiPackSpec?`
— ABI-Range-Check, optionale Sektionen tolerant (fehlend → null/leer), `cornerRadius` als Int. Gibt `null` bei
kaputtem JSON; gibt eine `UiPackSpec` zurück (auch wenn leer — der Aufrufer filtert `hasAnyOverride`).
**`data` braucht keinen neuen Modul-Dep** (nur `domain` + `org.json`).

## 5. Anwendung (app) — drei Pfade, Defaults sicher

### 5.1 Aktive Auswahl persistieren (wie LANGUAGE)
- `SettingsRepository` (domain) + `RoomSettingsRepository` (data): Key **`active_ui_pack`** (`Flow<String>`,
  Default `""` = keiner), Setter `setActiveUiPack(pkg)`. **Keine** Migration (Key-Value-`SettingEntity`).
- `SettingsViewModel`: `activeUiPack: StateFlow<String>` + `availableUiPacks: StateFlow<List<UiPackSpec>>`
  (aus `PluginCatalog`) + `setActiveUiPack`.

### 5.2 Discovery (PluginCatalog)
- `PluginCatalog.scanLocal()`: zusätzlich `discoverDataPlugins(UI_PACK)` → `parseUiPackSpec` →
  `_uiPackPlugins: StateFlow<List<UiPackSpec>>` (nur `hasAnyOverride`). Prune analog (deinstalliert → raus;
  aktiver Zeiger fällt auf `""`, wenn das Paket verschwindet).

### 5.3 Resolver + Apply (MainActivity, zentral — wie `resolveStrings`)
In `MainActivity` (wo schon `LocalStrings`/`LocalDisplayBehavior`/`KomgaReaderTheme` gesetzt werden):
```kotlin
val activeUiPackId by settingsViewModel.activeUiPack.collectAsState()
val uiPacks by settingsViewModel.availableUiPacks.collectAsState()
val activePack = remember(activeUiPackId, uiPacks) { uiPacks.firstOrNull { it.packageName == activeUiPackId } }
```
Drei reine **app-seitige Konverter** (`app/ui/pack/UiPackApply.kt`, unit-testbar wo sinnvoll):
- **Icons:** `fun UiPackSpec.toIconPack(): IconPack?` — baut aus `iconRemap` (Name→Name via
  `IconKey.entries.firstOrNull{it.name==…}`, ungültige weg) ein `IconPack { resolve(key) =
  remap[key]?.let{DefaultIconPack.resolve(it)} }` (sonst `null` → Fallback-Default).
  `LaunchedEffect(activePack) { ActiveIconPack.current = activePack?.toIconPack() ?: DefaultIconPack }`.
  > **Reaktivitäts-Limit (dokumentieren):** `ActiveIconPack` ist prozess-global, nicht recompose-reaktiv (I1).
  > Beim App-Start mit persistiertem Pack greift es, bevor die Screens komponieren. Ein **live** gewechselter
  > Pack greift erst, wenn die icon-lesenden Screens neu komponieren (Tab-Wechsel) bzw. nach Neustart —
  > akzeptiert (I1-Limit), kein Crash.
- **Shell:** `fun UiPackSpec.shellOverride(): ShellDescriptor?` — `navStyle?.let{ ShellNavStyle.entries.firstOrNull{e->e.name==it} }?.let{ ShellDescriptor(it) }`. Wird **in den Host gereicht**:
  `HomeScreen`/`ShellPackRegistry.forFormFactor(ff, override)` → `override ?: descriptorFor(ff)`. (Signatur von
  `forFormFactor` um optionalen `override: ShellDescriptor? = null` erweitern; `HomeScreen` reicht
  `activePack?.shellOverride()` durch.)
- **Theme:** `fun UiPackSpec.tokenOverride(): TokenOverride?` (`TokenOverride(accent: Color?, cornerRadius: Dp?)`,
  Hex→Color tolerant). `KomgaReaderTheme(tokenOverride = …)` (neuer optionaler Param) wendet auf
  `pack.designTokens(dark)` an: **Akzent nur wenn `LocalDisplayBehavior.current.allowsAccentColor`** (sonst
  ignoriert — mono-E-Ink-Invariante host-erzwungen), `cornerRadius` immer. `onAccent` aus Akzent ableiten
  (Kontrast) oder mit-übergeben — minimal: nur `accent`+`cornerRadius`, `onAccent` bleibt vom Pack.

### 5.4 Settings-UI (Auswahl, wie Sprache)
- Picker „UI-Pack" in `AppearanceSettingsContent` (Darstellung): Optionen = „Standard" + jeder
  `availableUiPacks`-Eintrag (`displayName`); Auswahl → `setActiveUiPack(pkg|"")`. i18n-Keys (de+en):
  `settingsUiPack` („UI-Pack"/„UI pack"), `uiPackDefault` („Standard"/„Default").
- Plugins-Tab: `PluginTypeFilter` um **`UI_PACKS`** ergänzen; `visible`-Liste zeigt installierte UI-Packs
  (Deinstallieren via OS-Intent wie andere data-Plugins). Discovery/Prune über `PluginCatalog`.

## 6. Repo-Distribution

- `RepoIndexParser.pluginKindOf`: `"ui_pack"` → neue `PluginKind.UI_PACK` (Enum additiv). Install-/Fingerprint-
  Pfad (`PluginInstaller`, debug-signiert, Index-`fingerprint`) **unverändert** wiederverwendet.
- **Sample-Pack im Distributions-Repo** (`KomgaReaderPlugins`, separat) später; für L2-**E2E** genügt ein lokal
  gebautes APK (§8).

## 7. Tests

- **data** `UiPackParserTest` (pure): voller Pack, Subset (nur icons / nur shell / nur theme), leerer/kaputter
  JSON → null bzw. `!hasAnyOverride`, ungültige IconKey-Namen verworfen, ABI-out-of-range → null. Echte Umlaute.
- **app** `UiPackApplyTest` (pure, wo möglich): `toIconPack` mappt gültige, überspringt ungültige Namen, leer →
  `null`; `shellOverride` mappt navStyle, ungültig → `null`; `tokenOverride` parst Hex, ungültig → `null`.
- (Die host-Gating-Logik des Akzents wird im E2E geprüft, nicht pure.)

## 8. Sample-UI-Pack-APK (für E2E) + E2E

- Neues data-only APK-Projekt `plugin/komga-ui-pack-sample/` (Struktur wie `plugin/komga-eink-preset-kindle`:
  `build.gradle.kts` ohne Deps, `AndroidManifest.xml` mit den drei Metadata-Keys + `android:hasCode="false"`,
  `src/main/assets/ui_pack.json`). Asset: `{ "abiVersion":2, "shell":{"navStyle":"DRAWER"},
  "icons":{"Home":"Library"}, "theme":{"cornerRadius":4} }` (Akzent bewusst weglassen oder setzen — auf
  mono-E-Ink ohnehin gegated). **Nicht** ins App-`settings.gradle` aufnehmen (separates Standalone-Projekt wie
  die anderen Plugin-APKs) — per `gradle`/`apkanalyzer` bzw. eigenem Mini-Gradle bauen, dann `adb install`.
- **E2E (Emulator `eink_test`):** App bauen+installieren; Sample-APK installieren; in Settings → Darstellung →
  „UI-Pack" den Sample wählen. Verifizieren: (a) **Shell** = Drawer statt Bottom-Bar (auch bei Boox-Breite,
  weil Override den Form-Faktor schlägt), (b) **Icons** = `Home`-Glyph zeigt das Library-Symbol, (c) **Theme** =
  Karten/Tiles mit knapperem Eckradius; (d) Akzent (falls gesetzt) **bleibt Schwarz** (mono-E-Ink-Gate
  greift). „Standard" zurückwählen → alles Default. Discovery: Sample erscheint im Plugins-Tab (Filter UI_PACK).

## 9. Akzeptanz

- `PluginCategory.UI_PACK`; `UiPackSpec` (domain, pure); `UiPackParser` (data); `toIconPack`/`shellOverride`/
  `tokenOverride` (app) mit mono-E-Ink-Akzent-Gate; `active_ui_pack`-Setting; `PluginCatalog`-Discovery+Prune;
  Settings-Picker + Plugins-Tab-Filter; `RepoIndexParser` `ui_pack`-Typ; Sample-APK.
- `./gradlew :data:test :app:test :app:assembleDebug` grün; Parser-/Apply-Tests grün.
- **E2E** wie §8 (Shell/Icons/Theme wechseln, Akzent gegated, Zurück-auf-Standard).
- **docs-match-code (selber Branch):** `architecture-seams.md` (Plugin-Loader: neue data-Kategorie UI_PACK +
  die drei Apply-Pfade; ui-modularity: externer Lader **gebaut**), `big-picture-and-goals.md` (ui-modularity:
  „externer Pack-Lader" + `DeclarativeShell`-extern von Soll → Ist; offen bleibt nur Code-UI-Packs/ui-api-ABI-
  Freeze als künftiges Soll), `data-plugin-distribution`-Memory + [[ui-modularity-roadmap]].

## 10. Nicht in L2 (YAGNI)

**Kein** Code/Compose-UI-Pack (bleibt verboten — deklarativ-Regel). **Kein** ui-api-**Code**-ABI-Freeze (nur für
Code-Packs nötig). **Kein** Runtime-SVG-Icon-Import (I1-YAGNI; Remap nur unter bestehenden Glyphen). **Keine**
neue Shell-Geometrie/Theme-Felder über das Genannte hinaus (additiv später). **Keine** reaktive Live-Icon-
Umschaltung über das I1-Limit hinaus. **Kein** Per-Slot-Override (header/overlay/… einzeln) — die Region-Slots
sind in-tree swappable, der **externe** Pack steuert in L2 nur Shell/Icons/Theme (Slot-Packs extern = späteres
additives Soll).

## Bezug

Roadmap (Schlussstein) · L1 (`DeclarativeShell`/`ShellDescriptor`) · I1 (`ActiveIconPack`) · Theme-Pack
(`DesignTokens`) · `big-picture-and-goals.md` (deklarativ, E-Ink host-erzwungen) · data-only-Plugin-Mechanik
(`plugin-host`/`PluginCatalog`/Repo). Damit ist die **komplette UI extern modular** — offen bleibt nur das
additive Soll (Code-UI-Packs/ui-api-ABI, externe per-Slot-Packs).
