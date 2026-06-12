# A1: `ui-api`-Modul — die UI-Pack/Slot-Verträge einfrieren — Design-Spec

**Stand:** 2026-06-12 · **Status:** Design (noch nicht gebaut) · **Sub-Projekt A1** (Teil 1) der
Roadmap `docs/superpowers/specs/2026-06-12-complete-ui-modularity-roadmap.md`.

> **Self-contained:** Frische Session ohne Gesprächskontext kann das umsetzen. Vor dem Bauen lesen:
> `.claude/rules/architecture-seams.md` (die Nähte, Modulgrenzen, docs-match-code), `big-picture-and-goals.md`
> (ui-modularity → die drei Schichten + ui-api als Soll), `komga-plugins`-Skill (Capability-/Pack-Philosophie).
> **Vorbild-Modul:** `source-api` (Naht-A-Vertrag) — A1 ist sein UI-Gegenstück.

## 1. Ziel & Scope (User-Entscheidung 2026-06-12)

Die schon gebauten In-Tree-UI-Verträge (Region-Slots, Shell-Pack, Theme-Pack, Icon-Pack) in ein **eigenes
Gradle-Modul `:ui-api`** ziehen — **gespiegelt nach `source-api`/`plugin-api`**: ein dünnes Vertrags-Modul,
das die App **und** künftige UI-Packs gemeinsam kennen. Das ist das „eigene API-Modul, additiv erweiterbar,
nicht eingefroren bis L1/L2" aus der Roadmap, vorgezogen als reine **Modul-Extraktion** (Refactor, keine
Verhaltensänderung).

**Im Scope (nur das):**
- Neues `android-library`-Modul `:ui-api` (Compose+Material3) im DAG **`domain → ui-api → app`**.
- Die **Vertrags-Typen** (Capability-Surfaces, Slot-typealias, Pack-Interfaces, reine Wert-/Resolve-Typen,
  CompositionLocal-Deklarationen) + die **entkoppelten Built-ins** (Theme-Packs, Icon-Stack) dorthin verschieben.
- Konsumenten-Imports umschreiben. Verhalten **pixel-/verhaltensgleich**.

**NICHT im Scope (eigene Folge-Sub-Projekte):**
- **A1b** — die *deklarative* Reader-Chrome-Form (Tap-Zone→Aktion-Deskriptor statt bespoke `tapModifier`,
  UI-Plugin-Form (b)). Bleibt eigenes Sub-Projekt, näher an L1/L2.
- **L1/L2** — `DeclarativeShell`, externer APK-Pack-Lader (TOFU/ABI). Der Vertrag wird hier **noch nicht
  eingefroren/versioniert** (kein ABI-Gate) — nur sauber als Modul isoliert, additiv. Das Einfrieren/Gegenstück
  zu `plugin-api` (ABI, re-export via `api()`) kommt mit dem Lader.

## 2. Spiegel-Prinzip & die Entkopplungs-Regel (zentrale Design-Entscheidung)

`source-api` enthält den **Vertrag** (`MediaSource` & Co.) **plus** den trivialen, entkoppelten Fallback
`StubSource` — aber **nie** eine konkrete Quelle (`KomgaSource`). `ui-api` macht es genauso:

> **Entkopplungs-Regel:** Ein Typ gehört nach `ui-api`, wenn er **keine** `:app`-Typen referenziert
> (keine i18n `LocalStrings`/`Strings`, keine app-Komponenten wie `EinkModal`/`StandardTopAppBar`/
> `EinkBottomBar`, kein Coil `SourceCover`, kein ViewModel, kein `BuildConfig`). Das gilt für **Verträge**
> (immer entkoppelt) **und** für **entkoppelte Built-ins** (Theme-Packs, Icon-Glyphen — sie bündeln mit dem
> Vertrag wie `StubSource`). **Gekoppelte Default-Renderer bleiben in `:app`** (sie sind das `KomgaSource`-
> Äquivalent: der konkrete Onyx-Look, der an app-i18n/-Komponenten hängt).

Konkret:
- **Theme-Packs** (`MonoEinkPack`/`KaleidoPack`/`LcdPack`) + `packFor`/`UiPackRegistry` + `DesignTokens`/
  `EinkTokens`/`designTokensFor`/`DepthSurface`: reine Farb-/Maß-Daten, **keine** app-Kopplung → **ui-api**.
  (So behalten `LocalUiPack`/`LocalDesignTokens` ihre echten Default-Werte.)
- **Icon-Stack** (`IconKey`/`IconPack`/`DefaultIconPack`/`ActiveIconPack`/`AppIcons`/`LucideIcons`): null
  app-Kopplung, **muss** außerdem vor App-Start auflösbar sein (prozess-global, in Datenklassen-Feldern
  gelesen) → der ganze Stack nach **ui-api** (Glyph-Default = `StubSource`-Analog).
- **Slot-Default-Renderer** (`DefaultHeader` + `DefaultSlots` + `DefaultDialog`/`DefaultSettings`/
  `DefaultSeriesTile`/`DefaultReaderOverlay`/`DefaultDetailScaffold`/`DefaultReaderScaffold`/
  `DefaultHomeHeader`) und **`DefaultShell`/`PhoneShell`/`ShellPackRegistry`**: koppeln an `LocalStrings`,
  app-Komponenten, Coil → **bleiben in `:app`**.

**Folge für `LocalResolvedSlots`:** sein Default braucht `DefaultSlots` (app) → der Default kann **nicht**
mehr in `ui-api` aufgelöst werden. Lösung: `UiSlots.resolve` wird **2-arg** (`resolve(pack, defaults)`,
pure), und `LocalResolvedSlots` bekommt einen **Error-Default** (`error("…")`, Standard-Compose-Muster, da
der Host ihn immer bereitstellt). App liefert `defaults` aus `DefaultSlots`. Das ist die **einzige**
Signatur-Änderung. Alle anderen Locals (`LocalUiPack`, `LocalDesignTokens`, `LocalDisplayBehavior`,
`LocalEinkMode`) behalten ihre echten Defaults (entweder im ui-api liegend oder app-lokal in
`LoadingIndicator.kt`).

## 3. Modul-Setup

### 3.1 `settings.gradle.kts`
`include(":ui-api")` ergänzen (Reihenfolge egal; nach `:domain`).

### 3.2 `ui-api/build.gradle.kts` (neu) — **erstes android-library-Compose-Modul im Repo**
```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.komgareader.ui"
    compileSdk = 34
    defaultConfig { minSdk = 28 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    api(project(":domain"))                       // re-exportiert Series, DisplayBehavior, ShellLayoutMode
    implementation(platform(libs.compose.bom))
    api(libs.compose.ui)                          // @Composable, Modifier, Color, ImageVector, BoxScope …
    api(libs.compose.material3)                   // ColorScheme, Shapes, Typography (UiPack-Vertrag)
    implementation("androidx.compose.foundation:foundation")

    testImplementation(libs.junit.jupiter)
    testImplementation(kotlin("test"))
}
// JUnit5 für die reinen Vertrags-Tests (SlotFallback/ShellSelection/IconPack):
tasks.withType<Test> { useJUnitPlatform() }
```
> Alle `libs.*`-Aliase existieren bereits (`gradle/libs.versions.toml`). `api(...)` für domain/compose-ui/
> material3, damit `:app` die Vertrags-Typen transitiv sieht (wie source-api `api(project(":domain"))`).

### 3.3 `app/build.gradle.kts`
`implementation(project(":ui-api"))` ergänzen (bei den anderen `project(...)`-Deps). Die direkten
`compose.bom`/`compose.ui`/`material3`-Deps von app **bleiben** (app rendert weiter selbst).

## 4. Paket-Layout in `ui-api`

Eigener Wurzel-Namespace `com.komgareader.ui.*` (analog dazu, dass `source-api` unter `com.komgareader.domain.*`
und **nicht** unter `…app…` liegt — das Vertrags-Modul besitzt seine eigene Wurzel, nicht die der App):

| ui-api-Paket | Inhalt |
|---|---|
| `com.komgareader.ui.slots` | alle Capability-Surfaces + Slot-typealias + `UiSlotPack`/`ResolvedSlots`/`UiSlots`/`LocalResolvedSlots` + `ResolvedSlots.header`-Ext + `HomeHeaderState`/`HomeHeaderSearch`/`HomeHeaderFilter` |
| `com.komgareader.ui.shell` | `AppShellState`/`ShellDestination`/`ShellDestinationId` + `ShellPack`-Interface + `ShellFormFactor` + `formFactorFor`/`resolveFormFactor` |
| `com.komgareader.ui.theme` | `UiPack`-Interface + `MonoEinkPack`/`KaleidoPack`/`LcdPack` + `packFor`/`UiPackRegistry` + `LocalUiPack` + `DesignTokens`/`LocalDesignTokens`/`designTokensFor` + `EinkTokens` + `DepthSurface` |
| `com.komgareader.ui.icons` | `IconKey` + `IconPack` + `DefaultIconPack` + `ActiveIconPack` + `AppIcons` + `LucideIcons` |

## 5. Typ-für-Typ-Inventar (Ist → Ziel)

### 5.1 → `com.komgareader.ui.slots`
**Aus `app/ui/slots/UiSlots.kt`** (Datei aufsplitten):
- **Verschieben (Vertrag):** `HeaderSearch`, `HeaderState`, `HeaderSlot`, `HomeHeaderSlot`, `DialogSlot`,
  `SettingsSlot`, `TilesSlot`, `OverlaySlot`, `DetailSlot`, `ReaderChromeSlot`, `UiSlotPack`, `ResolvedSlots`,
  `UiSlots` (mit **neuer** 2-arg-`resolve`), `LocalResolvedSlots` (**Error-Default**), `ResolvedSlots.header`-Ext.
- **In `:app` zurücklassen** (gekoppelt, neue Datei `app/ui/slots/DefaultSlots.kt`): `DefaultSlots`,
  `DefaultHeader` (nutzt `LocalStrings`/`AppIcons`/`EinkSearchBar`/`StandardTopAppBar`).

**Surfaces aus Streu-Dateien** (jeweils nur die `*State`-Surface verschieben, Default-Renderer + Wrapper
bleiben in ihrer app-Datei):
| Surface | Ist-Datei (`:app`) | Bleibt dort (Renderer/Wrapper) |
|---|---|---|
| `DialogState` | `ui/components/EinkModal.kt` | `EinkModal`, `DefaultDialog`, `EinkInfoDialog` |
| `TileState` | `ui/components/SeriesTile.kt` | `SeriesTile`-Wrapper, `DefaultSeriesTile` |
| `ReaderOverlayState` | `ui/reader/ReaderChrome.kt` | `DefaultReaderOverlay`, `ReaderStatusBar`, Hints |
| `ReaderScaffoldState` | `ui/reader/ReaderScaffold.kt` | `ReaderScaffold`-Wrapper, `DefaultReaderScaffold` |
| `DetailScaffoldState` | `ui/detail/DetailScaffold.kt` | `DefaultDetailScaffold` |
| `SettingsState` | `ui/settings/SettingsScreen.kt` | `SettingsScreen`-Wrapper, `DefaultSettings` |
| `SettingsSection`, `SettingsSectionId` | `ui/settings/SettingsSections.kt` | `buildSettingsSections()` (nutzt `BuildConfig`/`SettingsViewModel`) |
| `HomeHeaderState`, `HomeHeaderSearch`, `HomeHeaderFilter` | `ui/home/HomeHeader.kt` | `DefaultHomeHeader`-Renderer |

> `TileState.series: com.komgareader.domain.model.Series` — domain ist via `api(project(":domain"))`
> sichtbar. `DialogState`/`ReaderOverlayState`/`ReaderScaffoldState`/`DetailScaffoldState` sind reine
> Compose-Primitiven (sauber). `DetailScaffoldState.search: HeaderSearch?` — `HeaderSearch` liegt im selben
> ui-api-Paket. `SettingsSection` ist sauber (`ImageVector`+`@Composable`), nur `buildSettingsSections()`
> ist gekoppelt → bleibt.

### 5.2 → `com.komgareader.ui.shell`
**Aus `app/ui/shell/`:**
- **Verschieben:** `AppShellState.kt` (`AppShellState`, `ShellDestination`, `ShellDestinationId`) — referenziert
  `HomeHeaderState` (jetzt `com.komgareader.ui.slots`) + `ImageVector`. `ShellPack`-Interface + `ShellFormFactor`
  + `formFactorFor` + `resolveFormFactor` (aus `ShellPack.kt`; `resolveFormFactor` nutzt `domain.ShellLayoutMode`).
- **Bleibt in `:app`** (gekoppelt): `ShellPackRegistry` (referenziert `DefaultShell`/`PhoneShell`), `DefaultShell.kt`,
  `PhoneShell.kt`.
> `ShellPack.kt` aufsplitten: Interface + Form-Faktor-Funktionen → ui-api; `ShellPackRegistry` → app
> (eigene Datei `app/ui/shell/ShellPackRegistry.kt`).

### 5.3 → `com.komgareader.ui.theme`
**Verschieben (ganze Dateien, entkoppelt):** `UiPack.kt` (Interface + die drei Packs + `packFor` +
`LocalUiPack`), `UiPackRegistry.kt`, `DesignTokens.kt` (inkl. `internal AccentVivid*`/`AccentMuted` — bleiben
`internal`, jetzt sichtbar im ui-api-Modul), `EinkTokens.kt`, `DepthSurface.kt`.
**Bleibt in `:app`:** `Theme.kt` (`KomgaReaderTheme`-Host, `ThemeMode`) — der Provider, der die Locals setzt
und app-`DefaultSlots` einspeist.

### 5.4 → `com.komgareader.ui.icons`
**Verschieben (ganzer Stack, entkoppelt):** `IconPack.kt` (`IconKey`, `IconPack`, `DefaultIconPack`,
`ActiveIconPack`), `AppIcons.kt`, `LucideIcons.kt`. **Nichts** bleibt app-seitig. `tools/icons` (Generator)
**unverändert** — generiert künftig nach `ui-api/src/main/kotlin/com/komgareader/ui/icons/LucideIcons.kt`
(Generator-Ausgabepfad anpassen, falls hartkodiert — prüfen, sonst Hinweis in `tools/icons`).

## 6. Die eine Signatur-Änderung (`resolve` + `LocalResolvedSlots`)

**ui-api** `UiSlots.kt`:
```kotlin
object UiSlots {
    /** Pure: pro Region „Pack-Slot oder Default". [defaults] liefert der Host (app-`DefaultSlots`),
     *  weil der Onyx-Look an app-i18n/-Komponenten koppelt und daher nicht in ui-api liegt. */
    fun resolve(pack: UiSlotPack, defaults: ResolvedSlots): ResolvedSlots = ResolvedSlots(
        headerSlot = pack.header ?: defaults.headerSlot,
        homeHeader = pack.homeHeader ?: defaults.homeHeader,
        dialog = pack.dialog ?: defaults.dialog,
        settings = pack.settings ?: defaults.settings,
        tiles = pack.tiles ?: defaults.tiles,
        overlay = pack.overlay ?: defaults.overlay,
        detail = pack.detail ?: defaults.detail,
        readerChrome = pack.readerChrome ?: defaults.readerChrome,
    )
}

val LocalResolvedSlots = staticCompositionLocalOf<ResolvedSlots> {
    error("Kein ResolvedSlots bereitgestellt — in KomgaReaderTheme wrappen.")
}
```

**app** neue Datei `app/ui/slots/DefaultSlots.kt`:
```kotlin
object DefaultSlots {
    val header: HeaderSlot = { state -> DefaultHeader(state) }
    // … homeHeader/dialog/settings/tiles/overlay/detail/readerChrome wie bisher …
    /** Die mitgelieferten Slots als aufgelöstes Pack — Default-Argument für [UiSlots.resolve]. */
    val resolved: ResolvedSlots = ResolvedSlots(header, homeHeader, dialog, settings, tiles, overlay, detail, readerChrome)
}
/** App-Komfort: resolve gegen das mitgelieferte Default-Pack. */
fun resolveSlots(pack: UiSlotPack): ResolvedSlots = UiSlots.resolve(pack, DefaultSlots.resolved)
```
> `DefaultHeader` wandert mit nach `DefaultSlots.kt` (oder bleibt in einer app-`slots`-Datei) — Hauptsache
> raus aus dem ui-api-`UiSlots.kt` wegen `LocalStrings`.

**`Theme.kt`** (app): `LocalResolvedSlots provides UiSlots.resolve(slotPack)` → `… provides resolveSlots(slotPack)`.

**Debug-Previews (7):** `UiSlots.resolve(UiSlotPack(dialog = …))` → `resolveSlots(UiSlotPack(dialog = …))`
(je eine Zeile; Import `com.komgareader.app.ui.slots.resolveSlots`).

## 7. Import-Umschreibung (Blast-Radius, deterministisch)

Nach dem Verschieben ändern sich Konsumenten-Imports **nur für verschobene Typen**. Old→New-Präfixe:
- `com.komgareader.app.ui.slots.{verschobene Typen}` → `com.komgareader.ui.slots.…`
  (⚠ **`DefaultSlots`/`DefaultHeader`/`resolveSlots` bleiben `com.komgareader.app.ui.slots`** — nicht
  umschreiben.)
- `com.komgareader.app.ui.icons.*` → `com.komgareader.ui.icons.*` (**alles**, ~36 Dateien)
- `com.komgareader.app.ui.theme.{UiPack,MonoEinkPack,KaleidoPack,LcdPack,packFor,UiPackRegistry,LocalUiPack,DesignTokens,LocalDesignTokens,designTokensFor,EinkTokens,depthSurface}` → `com.komgareader.ui.theme.…`
  (⚠ **`KomgaReaderTheme`/`ThemeMode` bleiben `com.komgareader.app.ui.theme`**.)
- `com.komgareader.app.ui.shell.{AppShellState,ShellDestination,ShellDestinationId,ShellPack,ShellFormFactor,formFactorFor,resolveFormFactor}` → `com.komgareader.ui.shell.…`
  (⚠ **`DefaultShell`/`PhoneShell`/`ShellPackRegistry` bleiben `com.komgareader.app.ui.shell`**.)
- Streu-Surfaces: `DialogState`/`TileState`/`ReaderOverlayState`/`ReaderScaffoldState`/`DetailScaffoldState`/
  `SettingsState`/`SettingsSection`/`SettingsSectionId`/`HomeHeaderState`/`HomeHeaderSearch`/`HomeHeaderFilter`
  ziehen aus ihren alten `app.ui.{components,reader,detail,settings,home}`-Paketen nach `com.komgareader.ui.slots`
  → Konsumenten importieren neu. Die **Renderer/Wrapper** in denselben alten Dateien referenzieren die Surface
  künftig per ui-api-Import.

**Vorgehen:** pro verschobenem Typ exakt per FQN umschreiben (kein pauschales Paket-Replace — die Pakete sind
gesplittet). `./gradlew :app:compileDebugKotlin` iterativ bis grün; jeder „unresolved reference" zeigt eine
vergessene Import-Zeile.

## 8. Tests verschieben

Reine JVM-Vertrags-Tests wandern in `ui-api/src/test/kotlin/…` (das Modul nutzt JUnit5):
- `SlotFallbackTest` (16 Tests) → testet `UiSlots.resolve` — **anpassen** an 2-arg (`resolve(pack, fakeDefaults)`
  mit trivialen Fake-Slots statt `DefaultSlots`; testet weiter „Pack-Slot überschreibt, sonst Default-Referenz").
- `ShellSelectionTest` (8 Tests, `formFactorFor`/`resolveFormFactor`) → ui-api (`ShellPackRegistry`-Tests, die
  `DefaultShell`/`PhoneShell` referenzieren, **bleiben in app** — die Built-ins sind app-seitig; ggf. Test
  splitten: reine `resolveFormFactor`-Tests → ui-api, Registry-Tests → app).
- `IconPackTest` (3 Tests) → ui-api.
> Echte Umlaute in allen Testnamen. Tests müssen im neuen Modul grün laufen (`./gradlew :ui-api:test`).

## 9. Debug-Previews

Die 7 Swap-Beweis-Previews **bleiben in `:app`** (`app/src/debug/…`) — sie referenzieren sowohl Vertrag
(ui-api) als auch Default-Renderer/Komponenten (app). Nur Imports + `resolveSlots`-Aufruf anpassen (§6/§7).
`IconPackPreview` referenziert `AppIcons`/`ActiveIconPack` (jetzt ui-api) → Import anpassen.

## 10. Akzeptanz

- `:ui-api` existiert (android-library, Compose, `api(project(":domain"))`), `domain → ui-api → app` zyklenfrei.
- Alle Vertrags-Typen + entkoppelten Built-ins (Theme-Packs, Icon-Stack) liegen in `com.komgareader.ui.*`;
  alle gekoppelten Default-Renderer (`DefaultSlots`/`DefaultHeader`/`DefaultShell`/`PhoneShell`/`ShellPackRegistry`/
  `buildSettingsSections`) + der `Theme.kt`-Host bleiben in `:app`.
- `UiSlots.resolve` ist 2-arg; `LocalResolvedSlots` hat Error-Default; app-`resolveSlots`/`DefaultSlots.resolved`
  speist den Host + die Previews. **Keine** andere Verhaltens-/Signatur-Änderung.
- `./gradlew :ui-api:test :app:assembleDebug` grün. `:ui-api:test` enthält die migrierten reinen Tests.
- **E2E:** App startet auf dem Emulator (`eink_test`, Boox-Maße); Bibliothek/Detail/Reader/Settings/Plugins
  zeigen **pixel-/verhaltensgleiche** Icons, Theme, Slots wie vor der Extraktion (reiner Refactor).
- **docs-match-code (selber Commit):** `architecture-seams.md` (Shell-/Slot-/Theme-Naht: Vertrag jetzt im Modul
  `:ui-api`, nicht mehr in-tree `app/ui/...`; Modul-Tabelle in `CLAUDE.md` um `ui-api` ergänzen),
  `big-picture-and-goals.md` (ui-modularity: „`ui-api`-Modul" von Soll → Ist; weiter Soll: ABI-Einfrieren mit
  L1/L2), Memory-Roadmap [[ui-modularity-roadmap]] (A1-Teil1 gebaut, A1b deklarativ bleibt offen).

## 11. Nicht in A1 (YAGNI)

**Kein** ABI-Gate/Versionierung (kommt mit L1/L2-Lader, dann re-exportiert das Lader-Modul `ui-api` via `api()`
wie `plugin-api`→`source-api`). **Kein** `DeclarativeShell`, **kein** externer Pack-Lader, **kein** deklarativer
Reader-Chrome-Deskriptor (A1b). **Keine** neue Slot-Region. **Keine** Verhaltensänderung — verschobene Typen
bleiben byte-für-byte gleich (nur Paket). `tools/icons`-Generator-Logik unverändert (nur ggf. Ausgabepfad).

## Bezug

Roadmap `…complete-ui-modularity-roadmap.md` · Vorbild `source-api` (Naht A) · `architecture-seams.md` ·
`big-picture-and-goals.md` (ui-modularity, die drei Schichten). Nachfolger: **A1b** (deklarativer
Reader-Chrome), **L1/L2** (externer Lader + ABI-Einfrieren).
