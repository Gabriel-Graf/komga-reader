# Reader-Chrome & Settings-Vereinheitlichung — Implementierungsplan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Reader-Chrome um geteilte [Home][Settings]-Shortcuts erweitern, die Settings-Seite aus jedem Reader DRY öffnen (mit session-skopiertem Zurück-zum-Reader), die Novel-Typografie-Settings als EINE wiederverwendbare Komponente in Haupt-Settings **und** In-Reader-Panel zeigen, plus ein Divider-Fix.

**Architecture:** Alle Variabilität bleibt hinter den bestehenden Nähten. Reader teilen `ReaderScaffold`/`Viewer` (shared chrome) → die neuen Buttons leben dort, nicht pro Reader (`shared-structure-before-variants.md`). Die Typo-Settings werden eine **stateless** Komponente (Werte + Callbacks), die in beiden Mount-Punkten gegen denselben `SettingsRepository` schreibt — DRY, plugin-/modular-UI-konform. Settings-aus-dem-Reader ist eine **Compose-Navigation-Route** über dem Reader; der Back-Stack macht die Session-Skopierung automatisch (neuer Reader/Werk → alte Route weg → Button weg).

**Tech Stack:** Kotlin · Jetpack Compose · Navigation-Compose · Hilt · `SettingsRepository`/`SettingsViewModel`/`NovelReaderViewModel` · `EinkModal`/`AppIcons`/`EinkTokens` · Emulator `eink_test` (1264×1680@300).

**User-Entscheidung (2026-06-09):** Typo-Settings = **eine Komponente, beide Orte** (Haupt-Settings + In-Reader-Panel). TOC + Suche bleiben eigene In-Reader-Buttons (per-Buch-Navigation, keine globalen Settings).

**Reihenfolge & Abhängigkeiten:** A (Divider) und B (Home-Icon) unabhängig, zuerst. C (Novel-Settings DRY) unabhängig. D (Chrome-Buttons) braucht B. E (Settings-Route + Back) braucht D (der Settings-Button öffnet die Route).

**Ist-Stand (per Explore-Map verifiziert):**
- `ReaderScaffold(chrome, title, onBack, onPrev, onNext, …, actions: @Composable RowScope.() -> Unit = {}, …)` → `ReaderChromeOverlay(visible, title, onBack, actions)`. Alle 4 Reader nutzen `ReaderScaffold`. `actions` = Top-rechts-Slot.
- `ReaderRoute(onBack, …)` reicht `onBack` an jeden Reader. NavHost in `MainActivity.kt`; Settings ist ein **Tab in HomeScreen**, keine Route.
- Novel-Settings: `NovelTypoPanel.kt` (7 Settings: fontSizeEm, lineHeight, fontWeight, marginPreset, textAlign, hyphenationLang, fontFamily), alle global via `SettingsRepository` (7 Flows + 7 Setter). `NovelReaderViewModel` liest sie (`reflowConfig`-combine) + hat Setter. `SettingsViewModel` hat sie **noch nicht**.
- `AppIcons.Home` fehlt; `AppIcons.Settings`/`AppIcons.Back` existieren.
- Bottom-Bar `EinkBottomBar` ist eine **schwebende** Leiste (Margins+Radius); Höhe via `LocalContentBottomInset`. Settings-Content padded `bottom = LocalContentBottomInset`.

---

## Part A — Settings-Divider unten kürzen (Divider scheint unter der Menubar durch)

**Problem:** Ein Divider am unteren Ende des Settings-Inhalts reicht über die volle Breite und scheint neben/unter der schwebenden `EinkBottomBar` durch.

**Files:**
- Inspect: `app/src/main/kotlin/com/komgareader/app/ui/settings/SettingsScreen.kt`, `SettingsSections.kt`, `app/.../ui/components/EinkComponents.kt` (`SettingsGroup`/Divider)
- Modify: die Datei, die den fraglichen Bottom-Divider rendert

- [ ] **Step A1: Den durchscheinenden Divider lokalisieren**

`grep -rn "HorizontalDivider\|Divider(" app/src/main/kotlin/com/komgareader/app/ui/settings/`. Finde den Divider, der **am unteren Ende** des scrollenden Inhalts (bzw. zwischen letzter Sektion und Bar) liegt. Prüfe visuell am Emulator, welcher gemeint ist (zwischen den Sektionen via `SettingsGroup`, oder ein Trenner im Accordion/Master-Detail). Notiere Datei+Zeile.

- [ ] **Step A2: Divider horizontal einrücken / kürzen**

Den betroffenen Divider mit horizontalem Inset versehen, sodass er **nicht** die volle Breite unter die schwebende Bar zieht — analog zur Bar-Margin (`EinkBottomBar` nutzt `start=5.dp, end=5.dp`). Konkret: dem Divider `Modifier.padding(horizontal = …)` geben (z. B. passend zur Screen-/Bar-Margin), **oder** falls es der unterste Trenner ist, ihn entfernen/durch ausreichend Bottom-Spacing ersetzen. Token nutzen, kein Magic-dp (vorhandene `EinkTokens` prüfen; falls nötig eine benannte Konstante einführen). Nicht die anderen (oberen) Divider verändern.

- [ ] **Step A3: Emulator-Verifikation**

`emulator-5554`: Settings öffnen, ganz nach unten scrollen — der untere Divider scheint nicht mehr unter/neben der Menubar durch. Screenshot vorher/nachher.

- [ ] **Step A4: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/settings
git commit -m "fix(settings): unteren Divider einrücken — scheint nicht mehr unter der Menubar durch"
```

---

## Part B — `AppIcons.Home` ergänzen (Lucide)

**Files:**
- Modify: `tools/icons/icon-set.mjs` (Glyph-Liste), generiert `app/.../ui/icons/LucideIcons.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/icons/AppIcons.kt` (semantischer Eintrag)

- [ ] **Step B1: Lucide-„house" in den Generator aufnehmen**

In `tools/icons/icon-set.mjs` den Glyph `house` (lucide.dev/icons/house) ergänzen — exakt im Format der bestehenden Einträge (Name + ggf. Alias). NICHT `LucideIcons.kt` von Hand editieren.

- [ ] **Step B2: Generator laufen lassen**

`cd tools/icons && npm run generate` (oder das im README/Skript genannte Kommando). Erwartung: `LucideIcons.kt` enthält jetzt `Home`/`House` als `ImageVector` mit der zentralen `STROKE`-Konstante.

- [ ] **Step B3: Semantischen `AppIcons.Home`-Eintrag setzen**

In `AppIcons.kt`: `val Home: ImageVector get() = LucideIcons.House` (Glyph-Name an den generierten Namen anpassen). Stil = Outline, konsistent mit den anderen.

- [ ] **Step B4: Compile + Commit**

`./gradlew :app:compileDebugKotlin -q` (grün).
```bash
git add tools/icons/icon-set.mjs app/src/main/kotlin/com/komgareader/app/ui/icons/
git commit -m "feat(icons): AppIcons.Home (Lucide house) für Reader-Shortcut"
```

---

## Part C — Novel-Typografie-Settings als EINE wiederverwendbare Komponente (DRY)

**Ziel:** Die 7 Typo-Settings als **stateless** Composable (Werte + Callbacks), genutzt in (a) In-Reader-Panel und (b) Haupt-Settings (Reader-Sektion). Beide schreiben gegen denselben `SettingsRepository`.

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/ui/reader/NovelTypographyControls.kt` (shared stateless UI)
- Modify: `app/.../ui/reader/NovelTypoPanel.kt` (nutzt jetzt die shared Komponente)
- Modify: `app/.../ui/settings/SettingsViewModel.kt` (Novel-Settings-Flows + Setter, delegiert an `SettingsRepository`)
- Modify: `app/.../ui/settings/SettingsSections.kt` (Reader-Sektion bekommt die Typo-Controls) und ggf. `SettingsContent.kt`
- Modify: `app/.../i18n/Strings.kt` falls neue Labels (sonst bestehende Novel-Strings wiederverwenden)
- Test: `app/src/test/.../settings/SettingsViewModelTest.kt` (neue Novel-Setter)

- [ ] **Step C1: Bestandsaufnahme der Novel-Settings-UI**

Lies `NovelTypoPanel.kt` vollständig: welche Sub-Composables/Steppers/ChoiceRows, welche Callbacks (`onFontSize`, `onLineHeight`, `onFontWeight`, `onMargin`, `onTextAlign`, `onHyphenation`, `onFontFamily`), welche Wertebereiche/Konstanten (FONT_MIN/MAX/STEP …). Lies `NovelReaderViewModel` (Setter-Namen) und `SettingsRepository` (die 7 `novel*`-Flows + `setNovel*`-Setter). Notiere die exakten Signaturen.

- [ ] **Step C2: Stateless `NovelTypographyControls` extrahieren**

Neue Datei `NovelTypographyControls.kt` mit einem Composable, das **alle** aktuellen Werte als Parameter und je einen Callback bekommt — die reine UI aus `NovelTypoPanel` (Steppers/ChoiceRows/Margin-Chips), ohne Dialog-Rahmen. E-Ink-Designsprache (Token, `AppIcons`, keine Material-Stock-Controls, Animation gegatet). Signatur z. B.:

```kotlin
@Composable
fun NovelTypographyControls(
    fontSizeEm: Float, onFontSize: (Float) -> Unit,
    lineHeight: Float, onLineHeight: (Float) -> Unit,
    fontWeight: Int, onFontWeight: (Int) -> Unit,
    marginPreset: String, onMargin: (String) -> Unit,
    textAlign: String, onTextAlign: (String) -> Unit,
    hyphenationLang: String, onHyphenation: (String) -> Unit,
    fontFamily: String, onFontFamily: (String) -> Unit,
    modifier: Modifier = Modifier,
)
```

- [ ] **Step C3: `NovelTypoPanel` auf die shared Komponente umstellen**

`NovelTypoPanel` rendert jetzt nur noch seinen Dialog-Rahmen (`EinkInfoDialog`/`EinkModal`) + `NovelTypographyControls(...)`, verdrahtet mit den `NovelReaderViewModel`-Settern (Live-Tuning bleibt). Keine duplizierte UI mehr. Verhalten unverändert — am Emulator gegenprüfen (Schrift ändern im Reader wirkt sofort).

- [ ] **Step C4: Failing test — `SettingsViewModel` exponiert Novel-Settings**

In `SettingsViewModelTest.kt` (bestehende Datei aus dem Verbindungs-Feature): assert, dass `SettingsViewModel` die 7 Novel-Flows spiegelt und die Setter an den (fake) `SettingsRepository` durchreichen, z. B.:

```kotlin
@Test fun setNovelFontSize_delegates_to_repository() = runTest {
    val repo = FakeSettingsRepository()
    val vm = SettingsViewModel(/* … repo … */)
    vm.setNovelFontSizeEm(1.4f)
    assertThat(repo.lastNovelFontSizeEm).isEqualTo(1.4f)
}
```
(Fake-Repo-Muster aus den bestehenden Tests übernehmen; an die echten Setter-Namen anpassen.)

- [ ] **Step C5: `SettingsViewModel` um Novel-Settings erweitern**

Die 7 `novel*`-Flows als `StateFlow` spiegeln (`stateIn`) und 7 Setter ergänzen, die an `SettingsRepository.setNovel*` delegieren (im `viewModelScope`). **Keine** Novel-Logik duplizieren — nur Fassade. Test grün.

- [ ] **Step C6: Typo-Controls in die Haupt-Settings hängen**

In `SettingsSections.kt` die **Reader**-Sektion (oder eine neue Unter-Gruppe „Typografie", nur sichtbar wenn sinnvoll) um `NovelTypographyControls(...)` erweitern, verdrahtet mit den neuen `SettingsViewModel`-Settern. Labels über `i18n` (bestehende Novel-Strings wiederverwenden; fehlende in DE+EN ergänzen). Such-`query`-Highlight wie die anderen Settings-Inhalte respektieren.

- [ ] **Step C7: Verifikation**

`./gradlew :app:testDebugUnitTest --tests "*SettingsViewModelTest"` grün. Emulator: (a) Schrift in Haupt-Settings → Reader ändern, Novel-Reader öffnen → Wert übernommen; (b) Schrift im In-Reader-Panel ändern → Haupt-Settings zeigt denselben Wert (eine Quelle). Screenshots.

- [ ] **Step C8: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/reader/NovelTypographyControls.kt app/src/main/kotlin/com/komgareader/app/ui/reader/NovelTypoPanel.kt app/src/main/kotlin/com/komgareader/app/ui/settings app/src/test/kotlin/com/komgareader/app/ui/settings app/src/main/kotlin/com/komgareader/app/i18n
git commit -m "feat(novel): Typo-Settings als eine Komponente — In-Reader-Panel + Haupt-Settings (DRY)"
```

---

## Part D — Geteilte [Home][Settings]-Buttons in der Reader-Chrome (alle Reader)

**Ziel:** Oben rechts im Reader-Overlay (geteilt über `ReaderScaffold`) zwei Shortcuts: **Home** (links) und **Settings** (rechts daneben). In **allen** Readern, weil im Scaffold verdrahtet.

**Files:**
- Modify: `app/.../ui/reader/ReaderScaffold.kt` (neue Params `onHome`, `onSettings`)
- Modify: `app/.../ui/reader/ReaderChrome.kt` (`ReaderChromeOverlay` rendert die geteilten Buttons + die reader-spezifischen `actions`)
- Modify: `app/.../ui/reader/ReaderRoute.kt` (reicht `onHome`/`onSettings` an alle Reader-Screens; bzw. an `ReaderScaffold`)
- Modify: alle 4 Reader-Screens (`Paged/Webtoon/Comic/NovelReaderScreen`) — falls sie `ReaderScaffold` selbst aufrufen, die neuen Params durchreichen
- Modify: `app/.../MainActivity.kt` (liefert `onHome`/`onSettings` an `ReaderRoute`)

- [ ] **Step D1: `ReaderScaffold`/`ReaderChromeOverlay` um geteilte Top-rechts-Buttons erweitern**

`ReaderScaffold` bekommt `onHome: () -> Unit` und `onSettings: () -> Unit`. `ReaderChromeOverlay` rendert im rechten Bereich **zuerst** die geteilten Buttons `IconButton(AppIcons.Home, onClick=onHome)` und `IconButton(AppIcons.Settings, onClick=onSettings)`, **dann** die reader-spezifischen `actions()` (oder umgekehrt — Home/Settings ganz rechts, konsistent festlegen; Buttons gleich groß, 24dp, `tint = Color.White` wie der Back-Button im Overlay-Scrim). Neutral getönt (Aktionen, kein Akzent). Reihenfolge: **Home links, Settings rechts davon** (User-Vorgabe).

- [ ] **Step D2: Callbacks durch `ReaderRoute` fädeln**

`ReaderRoute` bekommt `onHome`/`onSettings` und reicht sie an jeden Reader-Screen bzw. direkt an `ReaderScaffold`. Die Reader-Screens, die `ReaderScaffold` aufrufen, geben die Params weiter (KEINE Logik pro Reader — nur Durchreichen, gleiche Naht).

- [ ] **Step D3: In `MainActivity` verdrahten**

`onHome` = zurück zur `home`-Route (`nav.navigate("home")` mit `popUpTo` o. ä., sodass der Reader verlassen wird). `onSettings` = Navigation zur neuen Settings-Route (Part E) — bis E existiert, vorerst ein TODO/temporär auf `onHome`-ähnlich oder leer, **aber** Part E im selben Branch direkt danach. (Reihenfolge: D dann E; D kann mit einem Platzhalter-`onSettings` compilen, E füllt ihn.)

- [ ] **Step D4: Emulator-Verifikation (alle Reader)**

Je einen paged/webtoon/comic/novel Reader öffnen, Chrome einblenden (Mitte tippen) → oben rechts [Home][Settings] sichtbar; Home kehrt zur Bibliothek zurück. Screenshots pro Reader.

- [ ] **Step D5: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/reader app/src/main/kotlin/com/komgareader/app/MainActivity.kt
git commit -m "feat(reader): geteilte Home/Settings-Shortcuts in der Reader-Chrome (alle Reader)"
```

---

## Part E — Settings aus dem Reader als volle Seite (DRY) mit session-skopiertem Zurück-zum-Reader

**Ziel:** Der Settings-Button im Reader öffnet **dieselbe** `SettingsScreen` (DRY) als Route über dem Reader, mit einem **Zurück-Button oben links an erster Stelle** in der Top-Bar, der in den Reader zurückführt. Sobald ein anderer Reader/Werk aktiv wird, ist die Route weg → kein Zurück-Link mehr (Back-Stack-Semantik).

**Files:**
- Modify: `app/.../MainActivity.kt` (neue Route `settings`, hostet `SettingsScreen` mit Back-Bar)
- Modify: `app/.../ui/settings/SettingsScreen.kt` ODER ein dünner Wrapper, der die Top-Bar mit Zurück-Button liefert (DRY: dieselbe `SettingsScreen`, nur in einem Scaffold mit Back-Bar)
- Inspect: wie HomeScreen die Settings-Tab rendert (damit die Route-Variante denselben Content nutzt, nicht dupliziert)

- [ ] **Step E1: Settings-Hosting verstehen (DRY-Grenze finden)**

`SettingsScreen(query)` wird heute im HomeScreen-Tab gerendert (ohne eigene Top-Bar — die TopBar liefert HomeScreen). Für die Route brauchst du dieselbe `SettingsScreen` **plus** eine Top-Bar mit Zurück-Button. Plane den dünnsten DRY-Schnitt: ein `SettingsRoute`-Composable, das ein `Scaffold`/`SubPageScaffold` mit Top-Bar (Zurück-Button links, erste Position) + `SettingsScreen(query)` im Body rendert. **Kein** Duplikat von `SettingsScreen`.

- [ ] **Step E2: `settings`-Route hinzufügen**

In `MainActivity` NavHost: `composable("settings") { SettingsRoute(onBack = { nav.popBackStack() }) }`. Der Reader-`onSettings` (Part D3) wird `{ nav.navigate("settings") }` — die Route wird **über** dem Reader gepusht, der Back-Stack hält den Reader darunter.

- [ ] **Step E3: `SettingsRoute` mit Zurück-Bar (DRY)**

`SettingsRoute(onBack)` = `SubPageScaffold`/`Scaffold` mit Top-Bar: **Zurück-Button (`AppIcons.Back`) links an erster Stelle** → `onBack` (zurück in den Reader „nach links"), Titel = Settings-Titel, Body = `SettingsScreen(query)` (dieselbe Komponente wie im Tab). Optional die TopBar-Suche wie im Tab. E-Ink-Designsprache.

- [ ] **Step E4: Session-Skopierung verifizieren (Back-Stack)**

Da die Route über dem Reader liegt: Reader → Settings → Zurück landet im selben Reader (gleiche Seite/Position). Reader → Settings → Home → Werk B öffnen → Reader B: der alte Reader-A-Eintrag ist nicht mehr unter einer Settings-Route; öffnet man jetzt Settings, führt Zurück in Reader B (den aktuellen). Erreicht man Settings über den **Bottom-Tab** (in HomeScreen), gibt es **keinen** Zurück-zum-Reader-Button (das ist die Tab-Variante, nicht die Route). Genau das gewünschte Verhalten — am Emulator durchspielen.

- [ ] **Step E5: Emulator-E2E**

(a) Reader öffnen → Settings-Button → volle Settings-Seite mit Zurück-Pfeil oben links → Zurück → zurück im Reader an gleicher Stelle. (b) Eine Typo-Setting dort ändern → zurück in den Novel-Reader → übernommen. (c) Über den Bottom-Tab geöffnete Settings haben **keinen** Zurück-zum-Reader-Button. Screenshots.

- [ ] **Step E6: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/MainActivity.kt app/src/main/kotlin/com/komgareader/app/ui/settings
git commit -m "feat(reader): Settings-Button öffnet die volle Settings-Seite (DRY) mit Zurück-zum-Reader"
```

---

## Selbst-Review (gegen die Anforderung)

- **Divider unten kürzer:** Part A. ✓
- **Gesamte Novel-Typo-Settings DRY in Haupt-Settings + Reader (eine Komponente):** Part C (User-Wahl „beide Orte"). TOC/Suche bleiben in-reader. ✓
- **In allen Readern Home + Settings oben rechts (Home links, Settings rechts):** Part D (im geteilten `ReaderScaffold`). ✓
- **Settings-Button → dieselbe Settings-Seite (DRY) mit Zurück-Button oben links erster Stelle, session-skopiert:** Part E. ✓
- **Home-Icon:** Part B (fehlte). ✓

**Querschnitt-Invarianten:** E-Ink-Designsprache (`EinkModal`/`AppIcons`/Token, neutrale Action-Icons, Animation gegatet) · geteilte Struktur statt N-fach (`ReaderScaffold`, eine Typo-Komponente) · DRY-Settings (eine `SettingsScreen`, eine Typo-Komponente, eine `SettingsRepository`-Quelle) · i18n DE+EN · TDD für die `SettingsViewModel`-Novel-Setter · Emulator-Beweis pro sichtbarem Teil · `docs-match-code` (berührte Naht-Doku im selben Commit nachziehen, v. a. wenn die Reader-Chrome/`Viewer`-Naht erweitert wird).
