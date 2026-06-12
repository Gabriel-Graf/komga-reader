# S0: Shell-Restposten — Form-Faktor-Override + Drawer-Akzent (+ compact-Header verifizieren) — Design-Spec

**Stand:** 2026-06-12 · **Status:** Design (noch nicht gebaut) · **Sub-Projekt S0** der
Roadmap `docs/superpowers/specs/2026-06-12-complete-ui-modularity-roadmap.md`

> **Self-contained:** Frische Session ohne Gesprächskontext kann das umsetzen. Vor dem Bauen lesen:
> `architecture-seams.md` (Shell-Pack-Naht), `eink-design-language.md` + `animation-gating.md` (E-Ink),
> die Shell-Pack-Spec `2026-06-12-modular-ui-shell-pack-design.md`. Template für die neue Einstellung ist
> das bestehende **`displayMode`**-Setting (gleiche Schichten domain→data→app→UI).

## 1. Ziel & Scope

Drei Shell-Pack-Restposten. Gebaut werden die zwei mit klarem Wert; der dritte wird **empirisch
verifiziert** (nicht spekulativ poliert):

- **S0.1 — Form-Faktor-User-Override (BAUEN):** Der Shell-Form-Faktor wird heute hart aus
  `formFactorFor(screenWidthDp)` (<600dp=compact) abgeleitet. Der Nutzer soll ihn überschreiben:
  **Auto / Kompakt / Tablet**. Muster 1:1 wie `displayMode`.
- **S0.3 — PhoneShell-Drawer-Akzent (BAUEN):** Die ausgewählte Drawer-Zeile nutzt heute Material3-
  Default-Farben statt der Host-Mono-Tokens. Sie soll `LocalDesignTokens.current.accent`/`onAccent`
  nutzen — wie `EinkBottomBar` es bereits tut (Konsistenz).
- **S0.2 — compact-Header (VERIFIZIEREN, nur fixen wenn real kaputt):** Kein verifiziertes Problem.
  Erst auf dem Emulator im erzwungenen Compact-Modus (über S0.1) ansehen; nur fixen, wenn der Header
  sichtbar bricht/überläuft. Sonst dokumentieren „verifiziert ok, kein Fix" (YAGNI).

## 2. Ausgangslage (Ist, verifiziert)

- **`app/.../ui/shell/ShellPack.kt`:** `formFactorFor(widthDp) = if (widthDp < 600) COMPACT else EXPANDED`;
  `ShellPackRegistry.forFormFactor(ff) = COMPACT→PhoneShell / EXPANDED→DefaultShell`.
- **Auswahl im Host** (`app/.../ui/home/HomeScreen.kt`, ~Z. 342-352):
  ```kotlin
  val configuration = LocalConfiguration.current
  val pack = ShellPackRegistry.forFormFactor(formFactorFor(configuration.screenWidthDp))
  pack.Render(AppShellState(destinations, selectedId, onSelect))
  ```
- **`app/.../ui/shell/PhoneShell.kt`:** Drawer über `ModalNavigationDrawer`; je Destination ein
  `NavigationDrawerItem(icon, label, selected = d.id == state.selectedId, onClick = …)` — **ohne**
  `colors`-Argument → Material3-Default-Auswahlfarbe (nicht token-gesteuert).
- **`EinkBottomBar.kt`** (DefaultShell-Nav, Vergleich): aktive Zelle nutzt `LocalDesignTokens.current.accent`
  als `tint`/Balken — **so soll der Drawer auch**.
- **Settings-Persistenz-Muster (`displayMode` als Template):**
  - `domain/.../repository/SettingsRepository.kt`: `val displayMode: Flow<String>` + `suspend fun
    setDisplayMode(value: String)`.
  - `data/.../repository/RoomSettingsRepository.kt`: `override val displayMode = dao.observe(KEY_DISPLAY)
    .map { it ?: "EINK" }`; `override suspend fun setDisplayMode(v) = dao.put(SettingEntity(KEY_DISPLAY, v))`;
    Key-Konstante im `private companion object`.
  - `app/.../ui/settings/SettingsViewModel.kt`: `val displayMode = settings.displayMode.stateIn(…, "EINK")`
    + `fun setDisplayMode(v) = viewModelScope.launch { settings.setDisplayMode(v) }`; im State-Objekt verdrahtet.
  - `app/.../ui/settings/SettingsContent.kt` (`AppearanceSettingsContent`, ~Z. 534-591): `PickerRow` +
    `PickerModal` über ein Domain-Enum (`DisplayMode.entries`), Labels via `LocalStrings`.
  - i18n: typsichere `Strings` (de+en, Compile-Zeit-Parität).

## 3. Design — S0.1 (Form-Faktor-Override)

### 3.1 Domain: Präferenz-Enum + Repository-Feld
- Neues Domain-Enum `domain/.../model/ShellLayoutMode.kt`: `enum class ShellLayoutMode { AUTO, COMPACT, EXPANDED }`
  (analog `DisplayMode`). `AUTO` = heutiges Verhalten (aus Breite ableiten).
- `SettingsRepository`: `val shellLayoutMode: Flow<String>` + `suspend fun setShellLayoutMode(value: String)`.

### 3.2 Data: Room-Persistenz
- `RoomSettingsRepository`: `KEY_SHELL_LAYOUT = "shell_layout_mode"` (companion); `override val shellLayoutMode
  = dao.observe(KEY_SHELL_LAYOUT).map { it ?: ShellLayoutMode.AUTO.name }`; `override suspend fun
  setShellLayoutMode(v) = dao.put(SettingEntity(KEY_SHELL_LAYOUT, v))`. **Kein Schema-Change** (Key-Value-
  `SettingEntity`-Tabelle existiert, neuer Key braucht keine Migration).

### 3.3 ViewModel
- `SettingsViewModel`: `val shellLayoutMode = settings.shellLayoutMode.stateIn(…, ShellLayoutMode.AUTO.name)`
  + `fun setShellLayoutMode(v: String) = viewModelScope.launch { settings.setShellLayoutMode(v) }`; im
  State-Objekt (das `buildSettingsSections`/`AppearanceSettingsContent` konsumiert) verdrahten.

### 3.4 Host-Auswahl (HomeScreen) — Override schlägt Auto
Den Override in `HomeShellHost` lesen (über die schon vorhandene Settings-Quelle/das VM, das `HomeScreen`
nutzt — per `grep` finden, wie `HomeScreen` an Settings kommt; falls nötig den `shellLayoutMode`-Flow dort
`collectAsState`). Auswahl:
```kotlin
val layoutMode = runCatching { ShellLayoutMode.valueOf(shellLayoutModeStr) }.getOrDefault(ShellLayoutMode.AUTO)
val formFactor = when (layoutMode) {
    ShellLayoutMode.AUTO -> formFactorFor(configuration.screenWidthDp)
    ShellLayoutMode.COMPACT -> ShellFormFactor.COMPACT
    ShellLayoutMode.EXPANDED -> ShellFormFactor.EXPANDED
}
val pack = ShellPackRegistry.forFormFactor(formFactor)
```
`ShellLayoutMode` (domain) → `ShellFormFactor` (app) wird **hier** (app) gemappt — domain kennt
`ShellFormFactor` nicht.

### 3.5 Settings-UI
In `AppearanceSettingsContent` (nahe dem `displayMode`-Picker) eine `PickerRow` + `PickerModal` über
`ShellLayoutMode.entries`, Labels via neue i18n-Keys. **i18n (de+en, beide Sprachen pflegen, echte
Umlaute):** ein Setting-Label (z. B. `settingsShellLayout` = „Layout-Modus" / „Layout mode") + drei
Options-Labels (`shellLayoutAuto` = „Automatisch"/„Automatic", `shellLayoutCompact` = „Kompakt
(Telefon)"/„Compact (phone)", `shellLayoutExpanded` = „Breit (Tablet)"/„Wide (tablet)"). Such-Terme der
Sektion (`searchTerms`) um die neuen Labels ergänzen, falls die Sektion welche pflegt.

### 3.6 Tests (S0.1)
- **Pure** (`domain` oder `app`-Unit, Compose-frei): `formFactorFor` bleibt unverändert getestet; neuer
  Test der Override-Auflösung — eine kleine pure Funktion `resolveFormFactor(mode: ShellLayoutMode, widthDp:
  Int): ShellFormFactor` extrahieren (in `ShellPack.kt`, neben `formFactorFor`) und testen: AUTO→breite-
  abhängig, COMPACT→COMPACT, EXPANDED→EXPANDED. (Das hält die Host-Auswahl dünn + unit-testbar.) Echte Umlaute.

## 4. Design — S0.3 (PhoneShell-Drawer-Akzent)

In `PhoneShell.Render`, am `NavigationDrawerItem`, `colors` setzen:
```kotlin
val tokens = LocalDesignTokens.current
…
NavigationDrawerItem(
    …,
    colors = NavigationDrawerItemDefaults.colors(
        selectedContainerColor = tokens.accent,
        selectedIconColor = tokens.onAccent,
        selectedTextColor = tokens.onAccent,
    ),
)
```
Import `androidx.compose.material3.NavigationDrawerItemDefaults` + `com.komgareader.app.ui.theme.LocalDesignTokens`.
So folgt die aktive Drawer-Zeile den Host-Mono-Tokens (E-Ink: schwarzer Hintergrund/weißer Text; Kaleido/LCD:
Akzent) — **konsistent mit `EinkBottomBar`**. Kein Custom-Control nötig (`NavigationDrawerItem` hat den
`colors`-Hook). E-Ink-Gating (`snapTo`) bleibt unangetastet.

## 5. S0.2 — compact-Header verifizieren (kein spekulativer Bau)

**Nach** S0.1: über den neuen Override auf dem Emulator `eink_test` (1264×1680 = expanded) **Kompakt
erzwingen** → `PhoneShell` rendert mit dem `homeHeader`-Slot (`DefaultHomeHeader`) in schmaler Spalte.
Screenshot ansehen:
- Bricht das Suchfeld / überlaufen die Aktionen / ist der Status-Cluster gequetscht? → **dann** einen
  minimalen Fix (z. B. im compact-Pfad weniger/kompaktere Header-Elemente) — als kleiner Zusatz dokumentieren.
- Sieht es ok aus? → **nichts bauen**, im Commit/Doku festhalten „compact-Header auf Emulator verifiziert,
  kein Fix nötig" (YAGNI — kein Optimierungs-Theater ohne sichtbares Problem).

> Hinweis: Der Emulator ist physisch 1264dp breit (immer expanded), aber der **Override** zwingt
> `PhoneShell` unabhängig von der Breite — genau dafür ist S0.1 auch das Test-Vehikel für S0.2/S0.3.

## 6. Akzeptanz

- **S0.1:** `ShellLayoutMode`-Enum (domain) + `shellLayoutMode`-Flow/Setter (SettingsRepository +
  RoomSettingsRepository, Key, keine Migration) + VM-StateFlow/Setter + `AppearanceSettingsContent`-Picker
  (i18n de+en) + Host-Auswahl nutzt Override (`resolveFormFactor`, pur + getestet). Default `AUTO` =
  verhaltensgleich zu heute.
- **S0.3:** PhoneShell-Drawer-Auswahl über `LocalDesignTokens` (accent/onAccent); E-Ink-Gating unberührt.
- **S0.2:** auf Emulator (erzwungen compact) verifiziert; Ergebnis dokumentiert (Fix nur bei echtem Bruch).
- Compile grün, pure Tests grün. **E2E (Emulator):** Settings → „Layout-Modus" auf „Kompakt" → die App
  schaltet auf PhoneShell-Drawer um; Drawer öffnen → aktive Zeile in Mono-Akzent (schwarz/weiß); zurück auf
  „Automatisch" → Bottom-Bar (DefaultShell) wie zuvor.
- `architecture-seams.md` (Shell-Pack: Form-Faktor jetzt user-überschreibbar; Drawer-Akzent token-getrieben)
  + `big-picture-and-goals.md` (S0-Restposten: 1+3 erledigt, 2 verifiziert) + Memory-Roadmap im selben
  Commit nachgezogen.

## 7. Nicht in S0 (YAGNI)

Kein `DeclarativeShell` (L1), kein externer Lader (L2), kein `ui-api` (A1). Keine neue Shell-Variante (nur
Override zwischen den zwei bestehenden Built-ins). S0.2 nur bei verifiziertem Problem. Form-Faktor (Shell)
bleibt orthogonal zur Geräteklasse (Theme/`displayMode`) — der neue Override ist eine **eigene** Achse,
nicht an `displayMode` gekoppelt.

## Bezug

Roadmap `…complete-ui-modularity-roadmap.md` · Shell-Pack-Spec `2026-06-12-modular-ui-shell-pack-design.md` ·
`architecture-seams.md` (Shell-Pack-Naht) · `eink-design-language.md`/`animation-gating.md`. Template:
`displayMode`-Setting. Nächste Shell-Stufe: **L1** (`DeclarativeShell`) → **L2** (externer Pack-Lader).
