# L1: `DeclarativeShell` — deskriptor-getriebenes Home-Skelett (in-tree) — Design-Spec

**Stand:** 2026-06-12 · **Status:** Design · **Sub-Projekt L1** der Roadmap `…complete-ui-modularity-roadmap.md`.
Vorläufer von **L2** (externer APK-Pack-Lader + ABI-Einfrieren).

> **Self-contained.** Vorher lesen: `architecture-seams.md` (Shell-Pack-Naht), `big-picture-and-goals.md`
> (ui-modularity → die drei Schichten + der **1→3-Pfad**: „Form 3 ist nur Form 1, bei der die Anordnungs-Logik
> von **Daten** statt Code getrieben ist"). Modul-Stand: `AppShellState`/`ShellPack`/`ShellFormFactor`/
> `formFactorFor`/`resolveFormFactor` liegen in `:ui-api` (`com.komgareader.ui.shell`); die zwei bespoke
> Built-ins `DefaultShell` (Bottom-Bar) + `PhoneShell` (Drawer) in `:app` (`com.komgareader.app.ui.shell`);
> `ShellPackRegistry` (app) wählt nach Form-Faktor; `HomeScreen` (HomeShellHost) ist der Host.

## 1. Ziel (der 1→3-Pfad, in-tree)

Heute sind die beiden Home-Skelette **zwei handgeschriebene `ShellPack`-Objekte** (`DefaultShell`/`PhoneShell`).
L1 ersetzt sie durch **eine** `DeclarativeShell`, die per **Daten-Deskriptor** (`ShellDescriptor`) entscheidet,
welches Skelett sie rendert — „Form 1 mit Anordnung aus Daten" (Big-Picture). Damit treibt der **Deskriptor-Pfad
die echte App** (kein toter Parallelcode), und L2 muss nur noch einen `ShellDescriptor` aus einem externen APK
laden und an `DeclarativeShell` reichen. Reiner verhaltens-/pixelgleicher Umbau: dieselbe `AppShellState`,
dasselbe Rendering pro Form-Faktor.

## 2. Vertrag (neu, `:ui-api`, `com.komgareader.ui.shell`)

Neue Datei `ui-api/src/main/kotlin/com/komgareader/ui/shell/ShellDescriptor.kt` — **pure Daten, kein Compose**
(damit ein externer Pack ihn später serialisiert liefern kann):
```kotlin
/**
 * Endliches Nav-Anordnungs-Vokabular des Home-Skeletts. Die einzige reale Variabilität zwischen den
 * heutigen Built-ins. Additiv erweiterbar (SIDE_RAIL etc.), ohne bestehende Deskriptoren zu brechen.
 */
enum class ShellNavStyle { BOTTOM_BAR, DRAWER }

/**
 * Daten-Deskriptor eines Home-Skeletts: **was wohin**, nicht **wie** (das `wie` rendert die host-eigene
 * [DeclarativeShell] aus den host-gebauten [AppShellState]-Stücken). Kein Compose, kein opaker Blob →
 * trägt den 1→3-Pfad: ein externer APK-Pack liefert später genau dieses Datum (L2), der Host rendert.
 */
data class ShellDescriptor(val navStyle: ShellNavStyle)

/** Pure Form-Faktor → Deskriptor-Auflösung: EXPANDED = Bottom-Bar (Tablet/E-Ink), COMPACT = Drawer (Phone).
 *  Compose-frei, unit-testbar — die Built-in-Auswahl als Daten. */
fun descriptorFor(formFactor: ShellFormFactor): ShellDescriptor = when (formFactor) {
    ShellFormFactor.EXPANDED -> ShellDescriptor(ShellNavStyle.BOTTOM_BAR)
    ShellFormFactor.COMPACT -> ShellDescriptor(ShellNavStyle.DRAWER)
}
```

## 3. Renderer (app, `com.komgareader.app.ui.shell`)

Neue `app/ui/shell/DeclarativeShell.kt`:
```kotlin
/**
 * Die EINE host-eigene Shell: interpretiert einen [ShellDescriptor] (Daten) und rendert die host-gebauten
 * [AppShellState]-Stücke (header/content/nav) im vom Deskriptor benannten Skelett. Ersetzt die früheren
 * bespoke Built-ins [DefaultShell]/[PhoneShell] — dieselbe Anordnung, jetzt deskriptor-geschaltet. Das ist
 * der In-Tree-Beleg des 1→3-Pfads: ein externer Pack (L2) liefert nur den Deskriptor, dieser Renderer bleibt.
 * E-Ink-Invarianten (Drawer-snapTo statt Slide) bleiben host-erzwungen.
 */
class DeclarativeShell(val descriptor: ShellDescriptor) : ShellPack {
    @Composable
    override fun Render(state: AppShellState) = when (descriptor.navStyle) {
        ShellNavStyle.BOTTOM_BAR -> BottomBarShell(state)
        ShellNavStyle.DRAWER -> DrawerShell(state)
    }
}
```
Die zwei privaten `@Composable fun BottomBarShell(state: AppShellState)` / `DrawerShell(state: AppShellState)`
sind die **verbatim** aus `DefaultShell.Render`/`PhoneShell.Render` übernommenen Bodies (Scaffold + Bottom-Bar
bzw. ModalNavigationDrawer + TopAppBar-Burger). Sie dürfen in `DeclarativeShell.kt` liegen oder in eigenen
privaten Dateien — wichtig: **byte-gleiches Rendering**, kein Verhalten ändern.

**`DefaultShell.kt`/`PhoneShell.kt` (die `object … : ShellPack`) entfallen** (ihre Bodies leben jetzt als die
privaten Composables). Falls Imports/Helfer (`EinkBottomBar`/`BottomNavItem`/`LocalContentBottomInset`/
`NavigationDrawerItemDefaults`/`LocalEinkMode`/`LocalDesignTokens`) mitwandern: unverändert übernehmen.

## 4. Registry (app)

`ShellPackRegistry.forFormFactor` liefert jetzt eine `DeclarativeShell` mit dem Form-Faktor-Deskriptor:
```kotlin
object ShellPackRegistry {
    fun forFormFactor(formFactor: ShellFormFactor): ShellPack = DeclarativeShell(descriptorFor(formFactor))
}
```
`HomeScreen` (Host) bleibt **unverändert** (`forFormFactor(resolveFormFactor(...))` liefert weiter einen
`ShellPack`). KDoc-Kommentar in HomeScreen (Z. 348, „compact → PhoneShell/Drawer …") auf
„compact → Drawer-Deskriptor, sonst Bottom-Bar-Deskriptor" anpassen.

## 5. Tests

- **ui-api** neue `ShellDescriptorTest.kt` (pure): `descriptorFor(EXPANDED).navStyle == BOTTOM_BAR`,
  `descriptorFor(COMPACT).navStyle == DRAWER`. Echte Umlaute.
- **app** `ShellSelectionTest.kt` umstellen: `assertSame(DefaultShell, …)` entfällt (Registry liefert jetzt
  `DeclarativeShell`-Instanzen). Neu: `(forFormFactor(EXPANDED) as DeclarativeShell).descriptor.navStyle ==
  ShellNavStyle.BOTTOM_BAR` und `… COMPACT … == DRAWER`. KDoc anpassen (keine `DefaultShell`/`PhoneShell`-Refs).

## 6. Swap-Beweis (Debug-Preview, optional aber erwünscht)

`app/src/debug/.../ui/shell/DeclarativeShellPreview.kt`: rendert `DeclarativeShell(ShellDescriptor(DRAWER))`
und `DeclarativeShell(ShellDescriptor(BOTTOM_BAR))` über **dieselbe** Beispiel-`AppShellState` nebeneinander —
zeigt, dass nur der Deskriptor das Skelett wechselt. Nur Debug/Preview.

## 7. Akzeptanz

- `ShellNavStyle`/`ShellDescriptor`/`descriptorFor` in `:ui-api` (pure, Compose-frei). `DeclarativeShell` (app)
  rendert beide Skelette deskriptor-geschaltet; `DefaultShell`/`PhoneShell`-Objekte entfernt; Registry liefert
  `DeclarativeShell(descriptorFor(ff))`. `HomeScreen` unverändert.
- `./gradlew :ui-api:test :app:assembleDebug` grün. `ShellDescriptorTest` + umgestellter `ShellSelectionTest` grün.
- **E2E (Emulator):** **beide** Form-Faktoren verhaltens-/pixelgleich zu vorher — EXPANDED (Boox-Maße) =
  Bottom-Bar-Home (Stöbern/Sammlungen/Bibliotheken/Plugins/Einstellungen); COMPACT (über Layout-Modus „Kompakt"
  oder schmale Breite) = Drawer mit Burger, aktive Zeile mono-Akzent, E-Ink-snapTo. Tab-Wechsel funktioniert.
- **docs-match-code (selber Branch):** `architecture-seams.md` (Shell-Pack-Naht: zwei bespoke Built-ins →
  **eine** `DeclarativeShell` + `ShellDescriptor`/`ShellNavStyle`/`descriptorFor`; Built-in-Skelette sind jetzt
  deskriptor-geschaltete private Composables), `big-picture-and-goals.md` (ui-modularity: `DeclarativeShell`
  von Soll → Ist; offen bleibt nur der **externe Lader L2**), `UiSlots.kt`-KDoc (Z. 31 `DefaultShell`/
  `PhoneShell` → `DeclarativeShell`), Memory-Roadmap [[ui-modularity-roadmap]] + [[modular-ui-shell-pack]].

## 8. Nicht in L1 (YAGNI)

**Kein** externer Pack-Lader / APK / ABI-Gate / TOFU (= L2). **Keine** Serialisierung des Deskriptors (kommt mit
L2 — heute reicht die Daten-Klasse). **Keine** neue Nav-Geometrie (SIDE_RAIL etc. — additiv, wenn gebraucht).
**Keine** Erweiterung des Deskriptor-Vokabulars über `navStyle` hinaus (header/content-Platzierung ist in beiden
Skeletten identisch → keine Variabilität zu beschreiben; additiv später). **Keine** Verhaltensänderung.

## Bezug

Roadmap · `architecture-seams.md` (Shell-Pack-Naht) · `big-picture-and-goals.md` (1→3-Pfad). Nachfolger:
**L2** (externer APK-Pack-Lader, ABI-Einfrieren von `:ui-api`, TOFU wie `plugin-host`; lädt einen
`ShellDescriptor` extern und reicht ihn an `DeclarativeShell`).
