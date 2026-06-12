# Modulare UI: Shell-Pack — die Form-Faktor-Naht

**Stand:** 2026-06-12 · **Status:** Design (noch nicht gebaut) · **Phase:** ui-modularity (P2/P3-Strang)

## 1. Ziel

Die ganze UI **um die Reader-Engines herum** auswechselbar machen — bis zu einer komplett
anders aufgebauten Oberfläche, die **dasselbe kann, aber fundamental anders angeordnet** ist
(Nav woanders, Buttons an anderen Orten, anderer Layout-Baum). Motivierender Fall: ein
**Phone-Formfaktor**, der sich vom heutigen E-Ink/Tablet-Layout im **Skelett selbst**
unterscheidet — nicht nur im Stil.

Core hält die Funktionalität, die UI stellt nur dar. Eine neue Oberfläche = **neue Impl hinter
der Naht, kein Kern-Umbau** (gleiches Naht-Prinzip wie Quellen/Geräte/Theme).

## 2. Das 3-Schichten-Modell

Die modulare UI staffelt sich in drei Schichten, jede eine Naht mit Default + Built-in-Varianten:

| Schicht | Tauscht | Auswahl-Trigger | Status |
|---|---|---|---|
| **Theme-Pack** (`UiPack`) | Look: Farbe/Token/Typo/Shapes | Geräteklasse (`DisplayBehavior`) | **gebaut** (Mono/Kaleido/Lcd) |
| **Shell-Pack** (`AppShellState`/`DefaultShell`) | **ganzes Layout-Skelett**: Nav-Ort, Anordnung, Baum | **Form-Faktor** (Fensterbreite) | **dieser Spec** |
| **Region-Slots** (`UiSlotPack`) | einzelne Chrome-Regionen, die ein Shell-Pack platziert | vom Shell-Pack gewählt | header+homeHeader gebaut, Rest Soll |

Der Shell-Pack ist die **neue oberste Naht**: das `homeHeader`-Muster eine Ebene höher
(`DefaultHomeHeader(state)` → `DefaultShell(appShellState)`).

**Form-Faktor (Shell) ⟂ Geräteklasse (Theme)** sind orthogonale Achsen (konsistent mit
„Geräteklassen sind nicht binär"):
- Boox = breit (expanded) + E-Ink → **Tablet-Shell + Mono-Theme**
- Phone = schmal (compact) + LCD → **Phone-Shell + Lcd-Theme**
- kleines E-Ink = compact + eink → **Phone-Shell + Mono-Theme** (fällt automatisch richtig)

## 3. Vertragsform: Ansatz 1 jetzt, Ansatz 3 als Endform

Zwei Formen, gestaffelt wie bei Quellen-Plugins und Theme:

1. **In-Tree-Compose-Shell-Pack (dieser Spec):** `@Composable (AppShellState) -> Unit`, ordnet mit
   voller Compose-Freiheit an. Built-ins (Default-/Phone-Shell) sind Compose — wie `DefaultHomeHeader`.
3. **Deklarativer Shell-Pack (Endform, externe APK-Packs, Phase 4 — NICHT dieser Spec):** externer Pack
   liefert **kein** arbiträres Compose mit Host-Rechten, sondern einen **Daten-Deskriptor** (Nav-Stil-Enum,
   welche Region an welchem Anker). Der Host shippt **eine** `DeclarativeShell` (selbst ein Ansatz-1-Built-in,
   parametrisiert per Deskriptor) und rendert **dieselben** `AppShellState`-Stücke an die genannten Plätze.

**Der 1→3-Pfad ist Evolution, kein Rewrite:** beide Formen konsumieren **dieselbe** `AppShellState`.
Form 3 ist nur Form 1, bei der die Anordnung von Daten statt Code getrieben ist.

**Die Bedingung, die 1→3 trägt (im Design ab Tag 1 erzwingen):** `AppShellState` ist ein **Satz
benannter, einzeln renderbarer Stücke** mit **endlichem Anordnungs-Vokabular** — **nie** ein opaker
„hier ist ein Content-Lambda, mach was"-Blob. Opak = ein Deskriptor kann es nie nachbauen.

## 4. Der Schnitt: Core-Nav ⟂ Shell-Anordnung

Es gibt **zwei Sorten Navigation**; der Schnitt läuft dazwischen:

| | Was | Besitzer |
|---|---|---|
| **Route-Nav** | Stack voller Screens: `home` → `series/{}` → `reader/…` → `settings`. Back-Stack, Deep-Link, Route-Args, `onOpenSeries`/`onOpenBook`/`onHome` | **Core** (`MainActivity` NavHost) — **unberührt** |
| **Destination-Nav** | Tab-Wechsel *innerhalb* `home`: Library ↔ Collections ↔ Groups ↔ Plugins ↔ Settings | Shell-Pack rendert das **Steuer-Element**; Core besitzt **Menge + Inhalt + Auswahl-State** |

**Der Shell-Pack-Bereich = exakt das heutige `HomeScreen`.** Nichts darüber, nichts darunter:

```
NavHost (Core, unberührt)
├─ "home"      → AppShell(appShellState)   ◄── HIER tauscht der Shell-Pack
│                 DefaultShell = Bottom-Bar | PhoneShell = Drawer/Rail
├─ "series/{}" → SeriesDetailScreen        (eigene Route, header-Slot)
├─ "reader/…"  → ReaderRoute               (Core, UNBERÜHRT — Geschwister-Route)
└─ "settings"  → SettingsRoute
```

**Reader bleibt unberührt, weil er eine Geschwister-Route ist**, die **über** die Home-Shell
gepusht wird — er liegt nicht *in* der Shell. Buch öffnen = NavHost pusht `reader/…`, Shell-Pack
ist nicht beteiligt. „Reader unberührt" kostet daher nichts.

## 5. Die Capability-Surface `AppShellState`

```kotlin
// Core baut das. Shell-Pack liest + ordnet an.
data class AppShellState(
    val destinations: List<ShellDestination>,
    val selectedId: ShellDestinationId,
    val onSelect: (ShellDestinationId) -> Unit,
)

data class ShellDestination(
    val id: ShellDestinationId,            // stabile Enum-ID (LIBRARY/COLLECTIONS/GROUPS/PLUGINS/SETTINGS)
    val icon: ImageVector,                 // für das Nav-Control
    val label: String,                     // lokalisiert
    val header: HomeHeaderState,           // per-Tab-Header — SCHON GEBAUT, wiederverwendet
    val content: @Composable () -> Unit,   // host-gebauter Tab-Screen (volle Logik im Core)
)
```

### Der entscheidende Schnitt — Daten vs. host-gebautes Composable

| Stück | Form | Warum |
|---|---|---|
| **Nav** (`destinations` + `selectedId` + `onSelect`) | **Daten** | Das **Widget selbst** (Bottom-Bar vs. Drawer vs. Rail) *ist* die Variabilität. Pack **baut** das Nav-Control aus den Daten → genau hier entsteht der Form-Faktor-Unterschied. Reine Präsentation über (Icon, Label, Auswahl), **null Logik** → kein Leck. |
| **content** | host-gebautes `@Composable` | Trägt volle Screen-Logik. Pack **platziert** nur, baut nie nach (sonst Duplikat). |
| **header** | `HomeHeaderState` (Surface) | Schon Capability-Surface (status/search/filter/menu/actions host-gebaut). Pack arrangiert via bestehenden `homeHeader`-Slot. |

**Prinzip (Verfeinerung von „UI neu, Kernlogik gleich"):** *Reine-Präsentation-über-Daten*-Stücke
(Nav) gehen als **Daten** rein — die Widget-Wahl *ist* die Variabilität, also darf das Pack sie neu
bauen. *Logik-gebundene* Stücke (content, header-Menüs) gehen als **host-gebaute** Composables; das
Pack platziert nur.

### Endliches Anker-Vokabular (Soll-Grammatik, NICHT in diesem Spec gebaut)

Nur dokumentiert, damit In-Tree-Packs es nicht sprengen (1→3-Bedingung):
- `ShellNavStyle = BOTTOM_BAR | SIDE_RAIL | DRAWER` — was ein künftiger Deskriptor wählt.
- Header sitzt fix über dem Content; Content füllt den Rest. **Variabel ist nur Nav-Stil + sein Ort.**
- → Die Deskriptor-Grammatik einer getabten Shell ist winzig (im Kern `navStyle`). YAGNI: heute baut der
  In-Tree-Pack frei in Compose, das Enum existiert nur als dokumentierte Zielform.

### Mechanik wandert mit

`LocalContentBottomInset` (gemessene Nav-Höhe → Scroller halten letzte Items frei) besitzt künftig
**der Shell-Pack** — ein Bottom-Bar-Pack stellt ihn, ein Drawer-Pack braucht ihn evtl. nicht.
Core-Content liest ihn unverändert.

## 6. Auswahl: Form-Faktor-Trigger

Spiegelbild der Theme-Auswahl: pure Funktion + Registry.

```kotlin
fun shellPackFor(windowSizeClass: WindowWidthSizeClass): ShellPack   // compact → Phone, sonst Default
object ShellPackRegistry { /* analog UiPackRegistry */ }
```

Im Host (`MainActivity`/`KomgaReaderTheme`) ausgewertet, als `LocalAppShell` bereitgestellt.
Fehlt ein Pack → Default (nie `null`, analog `StubSource`/`DefaultSlots`).

- **Auto nach Fensterbreite (dieser Spec):** compact (<600dp) → Phone-Shell; medium/expanded → Default.
- **User-Override (Soll, NICHT dieser Spec):** Shell manuell erzwingen, wie der `DisplayMode`-Override
  beim Theme — additiv, später.

## 7. Bau-Reihenfolge (Phasen)

- **Phase A — Surface extrahieren (verhaltens-erhaltend):** `AppShellState`/`ShellDestination`/
  `ShellDestinationId` definieren. Die **Logik** aus `HomeScreen` (Tab-State, query/typeFilter/
  downloadedOnly, Dialog-Flags, openCollectionId, ViewModels) in einen Core-„ShellHost" ziehen, der
  `AppShellState` baut. Das **Rendering** (Scaffold + `EinkBottomBar` + `when(selected)`) wird
  `DefaultShell` — **pixelgleich** zum heutigen Layout. Bestehende Tests als Netz. Reiner Resolver-Test
  analog `SlotFallbackTest` (fehlendes Pack → Default).
- **Phase B — Auswahl-Naht:** `shellPackFor(windowSizeClass)` + `ShellPackRegistry` + `LocalAppShell`-
  Wiring im Host. Für alle heutigen Breiten weiter `DefaultShell` → **keine** Verhaltensänderung.
- **Phase C — Phone-Shell als Swap-Beweis:** zweites Built-in (`PhoneShell`, Drawer/Rail-Nav, andere
  Anordnung) aus **derselben** `AppShellState`. Beweist Form-Faktor-Tausch **end-to-end** auf dem
  Emulator (compact-Breite) — analog wie Mono/Kaleido/Lcd den Theme-Tausch und `AlternativeHomeHeader`
  den Header-Tausch beweisen.

**Soll/später (nicht dieser Spec):** übrige Region-Slots (overlay/tiles/settings/dialog), `ui-api`-Modul,
`DeclarativeShell` + externer APK-Pack-Lader (Phase 4), User-Override des Form-Faktors.

## 8. Invarianten (host-erzwungen, NIE im Pack)

- **E-Ink: Bewegung/Akzent** bleiben am Host (`LocalDisplayBehavior`/`LocalDesignTokens`/`LocalEinkMode`).
  Ein Shell-Pack liefert Inhalt/Struktur/Anordnung, **nie** die Bewegungs-/Farb-Policy
  (`eink-design-language.md`, `animation-gating.md`).
- **Quellen-Agnostik:** der ShellHost baut Content aus den bestehenden Screens/VMs — keine
  quellen-spezifischen Typen (`source-agnostic-integration.md`).
- **docs-match-code:** dieser Spec, `big-picture-and-goals.md` (ui-modularity), `architecture-seams.md`
  und der `komga-plugins`-Skill werden beim Bauen im selben Commit auf den Ist-Stand gezogen.

## 9. Tests

- **Pure:** Resolver `shellPackFor` (compact→Phone, expanded→Default) + Fallback (fehlendes Pack→Default)
  als Unit-Test, Compose-frei.
- **E2E:** Emulator `eink_test` (Boox-Maße, expanded) zeigt `DefaultShell` unverändert; ein
  compact-Profil zeigt `PhoneShell` mit Drawer/Rail — beide navigieren dieselben Destinations, öffnen
  denselben Reader.

## Bezug

`big-picture-and-goals.md` (ui-modularity — 3-Schichten + Shell-Pack), `architecture-seams.md`
(UI-Slot-Naht), `shared-structure-before-variants.md` (Gemeinsames vor der N-ten Variante),
`komga-plugins`-Skill (Plugin-/Capability-Rezept). Vorbild: `modular-home-header`
(`HomeHeaderState`/`DefaultHomeHeader` — dieselbe Form eine Ebene tiefer).
