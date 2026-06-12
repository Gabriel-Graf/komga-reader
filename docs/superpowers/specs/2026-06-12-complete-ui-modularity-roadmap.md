# Komplette UI-Modularität — Programm-Roadmap (Dekomposition)

**Stand:** 2026-06-12 · **Status:** Roadmap (Dekomposition in Sub-Projekte) · **Phase:** ui-modularity

## Zweck

Das Endziel ist die **ganze Oberfläche modular** (eine komplett von der Community gebaute UI). Das ist
**kein einzelner Plan**, sondern ein **Programm aus unabhängigen Sub-Projekten**, je eine eigene
Spec→Plan→Bau-Runde (Projekt-Konvention). Dieses Dokument **zerlegt und ordnet** sie — es baut nichts.

**Bereits gebaut (Ausgangspunkt):** Theme-Pack (`UiPack`, Look nach Geräteklasse) · Region-Slots
**header** + **homeHeader** · **Shell-Pack** für das Home-Skelett (`AppShellState`/`DefaultShell`/
`PhoneShell`, Form-Faktor). Siehe `big-picture-and-goals.md` → ui-modularity.

## Festgelegte Programm-Entscheidungen (2026-06-12)

- **Detail-Routen = Region-Slot-komponiert, NICHT eigene Shell-Packs** (Fork A=a). Sie haben **keine
  Tab-Nav** (nur Header+Content+Back) → kein Skelett zu vertauschen, nur Regionen. **Erweiterbar:** wird
  eine Route strukturell zu unflexibel, kriegt sie *später* ein `DetailShell`-Pack — die host-gebauten
  Stücke (header/content) werden dann Pack-arrangiert, **kein Wegwerf** (additive Naht).
- **Externer APK-Pack-Lader zuletzt** (Fork B=a): voll geplant, aber **als letzte Phase** gebaut —
  er hängt von allem ab (`ui-api` eingefroren + stabiler Deskriptor).
- **`ui-api`-Modul spät extrahieren:** erst wenn die In-Tree-Verträge durch R+D+C stabilisiert sind
  (kein Premature-Freeze-Churn).
- **Reihenfolge: risikoärmstes Stück zuerst** („Theme zuerst, Layout danach, Lader zuletzt").

## Gemeinsames Rezept (jedes Sub-Projekt gleich)

Jede Region/Shell folgt dem [[komga-plugins]]-Rezept (Säule 3) — darum ist jedes Sub-Projekt **klein
und gemustert**: (1) Capability-Surface aus **benannten Stücken** (Daten vs. host-gebautes Composable —
reine-Präsentation-über-Daten als Daten, logik-gebunden als Composable); (2) **Host besitzt die Logik**,
Pack arrangiert nur; (3) In-Tree-Slot mit **garantiertem Default** (fehlt das Pack-Stück → Default, nie
`null`); (4) **E-Ink-Invarianten host-erzwungen** (Bewegung/Akzent nie im Pack); (5) **Swap-Beweis**
(Built-in-Alternative oder Debug-Preview). Verbindlich: `architecture-seams.md`, `eink-design-language.md`,
`animation-gating.md`, `shared-structure-before-variants.md`.

## Sub-Projekte (Dekomposition)

### Phase R — die 4 übrigen Region-Slots (risikoärmstes Stück zuerst)

Jeder Slot ist isoliert, klein, beweist eine Region. `UiSlotPack`/`ResolvedSlots`/`DefaultSlots` additiv
um je ein optionales Feld erweitern.

- **R1 — `dialog`-Slot (`BaseDialog`).** Der eine Dialog-Rahmen (sticky Header/Footer, scrollender Body)
  hinter eine `dialog`-Region. Isoliert, low risk. Swap-Beweis: Debug-Preview-Alternative.
  *Abhängigkeit:* keine. **Erstes Sub-Projekt.**
- **R2 — `settings`-Slot.** Die Settings-Screen-Struktur (Side-Tabs + gruppierte Zeilen) als Surface
  hinter eine `settings`-Region. Medium (hat Struktur). *Abhängigkeit:* keine.
- **R3 — `tiles`-Slot.** Das Tile-/Listen-Rendering (`SeriesTile`/`TileViewMode`) hinter eine `tiles`-
  Region. Medium; quer genutzt (Library **und** Detail-Routen) → vor D. *Abhängigkeit:* keine.
- **R4 — `overlay`-Slot.** Chrome-Overlays (Lade-/Empty-State, transiente Hinweise) hinter eine
  `overlay`-Region. Koppelt leicht an Reader-Chrome → nach R1–R3, vor C. *Abhängigkeit:* keine.

### Phase D — andere Vollbild-Routen modular (Region-komponiert)

- **D1 — SeriesDetail/GroupBrowse/CollectionDetail.** Nutzen schon den `header`-Slot; ihr **Body** wird
  slot-komponiert (Tiles aus R3 + ein schlankes Detail-`content`-Arrangement). **Region-Slot, kein
  Shell-Pack** (Entscheidung oben); erweiterbar zu `DetailShell`, falls je nötig.
  *Abhängigkeit:* R3 (tiles).

### Phase C — Reader-Chrome modular (Engines bleiben Core)

- **C1 — Reader-Chrome-Regionen.** `ReaderScaffold`/Overlay/Chrome-Buttons/Tap-Zonen hinter Slots; die
  **deklarative UI-Plugin-Form** (Plugins (b): Tap-Zone→Aktion-Beschreibung, der Host rendert + steuert
  Refresh). Größtes/riskantestes UI-Stück. **Reader-Engines unberührt** (Naht B, Render/Refresh/E-Ink-
  Garantie). *Abhängigkeit:* R4 (overlay) + bestehende `Viewer`-Naht.

### Phase A — `ui-api`-Modul (Verträge einfrieren)

- **A1 — `ui-api`-Modul-Extraktion.** Slot-/Shell-/Theme-Verträge (`UiSlotPack`/`ShellPack`/
  `AppShellState`/`UiPack`/`HomeHeaderState`/…) aus `app/ui/...` in ein dünnes, **additiv-stabiles**
  Modul (Kandidat neben `plugin-api`). Cross-cutting-Refactor, verhaltens-erhaltend.
  *Abhängigkeit:* R+D+C (Verträge stabil).

### Phase L — externer Pack-Lader (das echte „Community installiert eine UI") — zuletzt

- **L1 — `DeclarativeShell` + Deskriptor (in-tree).** **Eine** Host-`DeclarativeShell`, die `AppShellState`/
  Slots aus einem **Daten-Deskriptor** (Nav-Stil-Enum, Anker) anordnet — beweist die deklarative Form
  in-tree (Ansatz 3 über dieselbe Surface). *Abhängigkeit:* A1.
- **L2 — externer APK-Pack-Lader.** Separates APK, ABI-Gate, TOFU-Pinning (Modell aus `plugin-host`
  wiederverwenden), Discovery, Install — das tatsächlich ladbare UI-Pack. *Abhängigkeit:* A1 + L1.

### S0 — Shell-Pack-Restposten (klein, parallel/jederzeit)

Form-Faktor-User-Override · compact-Header-Politur · PhoneShell-Drawer-Auswahlfarbe auf Host-Mono-Tokens.
Low-Prio, an eine frühe R-Runde anhängbar.

## Bau-Sequenz (dependency-/risiko-geordnet)

```
R1 dialog → R2 settings → R3 tiles → R4 overlay   (Region-Slots, je isoliert)
                                  └→ D1 detail-routen (braucht R3)
                                       R4 └→ C1 reader-chrome (braucht R4 + Viewer-Naht)
                                                 └→ A1 ui-api einfrieren (Verträge stabil)
                                                       └→ L1 DeclarativeShell
                                                             └→ L2 externer APK-Lader
S0 Shell-Restposten — parallel, jederzeit (low-prio)
```

Jeder Knoten = eigene Spec→Plan→Bau-Runde. Nach jedem ist die App lauffähig + ein Stück modularer.

## Bezug

`big-picture-and-goals.md` (ui-modularity, „Weg zur kompletten Modularität"), `architecture-seams.md`
(Region-Slot- + Shell-Pack-Naht), [[komga-plugins]]-Skill (Capability-Rezept), `shared-structure-before-
variants.md`. Vorbilder: `2026-06-12-modular-home-header` (Region-Slot) + `2026-06-12-modular-ui-shell-pack`
(Shell-Pack).
