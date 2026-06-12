# Region-Slot R3: `tiles` (SeriesTile) — Design-Spec

**Stand:** 2026-06-12 · **Status:** Design (noch nicht gebaut) · **Sub-Projekt R3** der
Roadmap `docs/superpowers/specs/2026-06-12-complete-ui-modularity-roadmap.md`

> **Self-contained:** Frische Session ohne Gesprächskontext kann das umsetzen. Vor dem Bauen lesen:
> `komga-plugins`-Skill (Capability-Rezept, Säule 3), `architecture-seams.md` (UI-Slot-Naht) und
> `shared-structure-before-variants.md` (die Konsolidierung in §3 ist genau dieser Regel geschuldet).
> **Direktes Vorbild:** die schon gebauten Regionen **dialog** (R1, `git show ad8f0cc`) + **settings**
> (R2) in `app/src/main/kotlin/com/komgareader/app/ui/slots/UiSlots.kt`. R3 ist dasselbe Muster für die
> fünfte Region — mit einem **vorgelagerten DRY-Schritt**.

## 1. Ziel

Die **Bibliotheks-Kachel** (`SeriesTile` — Cover + Lokal/Cloud-Badge + Titel-Band) hinter eine benannte,
adressierbare **`tiles`-Region** legen. Ein UI-Pack kann die Kachel anders rendern (anderes Badge,
anderer Titel-Stil, anderer Rahmen), ohne dass die Grid-Aufrufer sich ändern. Fünfte Region nach
**header · homeHeader · dialog · settings**.

## 2. Der Schnitt — die einzelne Kachel, nicht das Grid

**Der Slot tauscht die einzelne Serien-Kachel, nicht das Grid.** Konsistent mit den Vorgänger-Regionen
(dialog = ein Dialog, header = ein Header): die `tiles`-Region ist **eine `SeriesTile`**. Das
`LazyVerticalGrid` + die Spaltenzahl bleiben **Screen-Eigentum** (jeder Screen besitzt sein Grid/Layout —
analog: der Dialog-Aufrufer entscheidet *wann* ein Dialog erscheint, der Slot nur *wie* er aussieht).

- **Host-gebaut/Screen-Eigentum (bleibt):** das Grid, die Spaltenzahl (`GridCells.Fixed(3)` etc.), welche
  `Series` angezeigt werden, die Navigation (`onClick`/`onLongClick` mit `sourceId`).
- **Pack-gewählt (austauschbar):** *wie* eine einzelne Kachel aussieht (Cover-Rahmen, Badge, Titel-Band).

> **Warum nicht das ganze Grid:** Spaltenzahl/Padding/welche-Items sind screen-spezifisch (Library 3-fix,
> SeriesDetail adaptiv, Collections umschaltbar). Ein „Grid-Slot" müsste all das in eine Surface zwängen.
> Buch-Kacheln (`ChapterTile`), Collage-Kacheln (`CollageTile`) und Member-Kacheln sind **eigene** spätere
> Regionen — R3 deckt die **Serien-Kachel** ab (die in Library + Gruppen vorkommt).

## 3. Vorgelagerter Schritt (PFLICHT, vor dem Slot): `GroupSeriesCover` → `SeriesTile` vereinheitlichen

`shared-structure-before-variants.md`: bevor die Serien-Kachel hinter einen Slot kommt, wird das bestehende
**Near-Duplikat eliminiert** — sonst reskinnt ein tiles-Pack nur die Library, nicht die Gruppen.

**Ist:** `GroupBrowseRoute.kt` (Z. 117-170) hat ein privates `GroupSeriesCover`, ein ~95%-Klon von
`SeriesTile`. Drei Abweichungen, alle vom Nutzer zur Vereinheitlichung freigegeben (2026-06-12):
1. **kein `.clip(RoundedCornerShape(4.dp))`** → Cover quadratisch hinter rundem Rand (SeriesTile clippt).
2. **Titel als inline `Text` mit `fontSize = 10.sp`, padding 2** statt geteiltem `TileTitleBand`
   (`labelSmall`, padding 6/3).
3. **kein `onLongClick`** (nur `onClick`).

**Soll:** `GroupBrowseRoute` ruft die zentrale `SeriesTile` (nach dem Slot-Umbau also automatisch über den
Slot). Die kosmetischen Diffs (runde Ecken, `TileTitleBand`) sind **gewollt** (konsistenter Look).
**Long-Press:** `GroupBrowseRoute` hat **keinen** Download-Callback (nur `onOpenSeries` + eigenes
`GroupBrowseViewModel`); darum `onLongClick = {}` übergeben — **verhaltensgleich** (GroupSeriesCover hatte
ohnehin kein Long-Press), kein VM-Plumbing, kein Scope-Wachstum.

**Konkret:** `GroupSeriesCover` löschen; die `items {}`-Schleife (Z. 102-108) ruft
```kotlin
SeriesTile(
    series = series,
    isLocal = series.remoteId in localSeriesIds,
    onClick = { onOpenSeries(series.remoteId, series.sourceId) },
    onLongClick = {},
)
```
`import com.komgareader.app.ui.components.SeriesTile` ergänzen; nun ungenutzte Imports in
`GroupBrowseRoute.kt` entfernen (die nur `GroupSeriesCover` brauchte: `ImageRequest`, `SourceCover`,
`FilteredAsyncImage`, `CircleShape`, `AppIcons`, `ContentScale`, `TextOverflow`, `Color`, `remember`,
`LocalContext`, `combinedClickable`, `aspectRatio`, `border`, `background`, `size`, `Icon` etc. — **per
Compiler/`grep` prüfen**, welche sonst noch in der Datei gebraucht werden, nur die wirklich toten löschen).

## 4. Ausgangslage (Ist, verifiziert)

- **`app/.../ui/components/SeriesTile.kt`** — `SeriesTile(series, isLocal, onClick, onLongClick, modifier)`
  (Z. 44-91). Body: `Box(aspectRatio 2/3, clip+border 1.5dp, combinedClickable) { FilteredAsyncImage(Cover
  via SourceCover) · Lokal/Cloud-Badge(TopEnd) · TileTitleBand(BottomStart) }`. Cover-Request über
  `ImageRequest…SourceCover(sourceId, remoteId, isSeries=true).crossfade(false)`. Die geteilte
  `TileTitleBand` (Z. 99-116) + `TileScrim` bleiben in der Datei. **Eine** Call-Site bisher
  (`LibraryScreen.kt:162`), nach §3 zweite (`GroupBrowseRoute`).
- **`UiSlots.kt`** — trägt header+homeHeader+dialog+settings (UiSlotPack/ResolvedSlots/DefaultSlots/
  resolve/LocalResolvedSlots).
- **`SlotFallbackTest.kt`** — pure Resolver-Tests (`assertSame`), je 2 pro Region, **echte Umlaute**.

## 5. Design

### 5.1 Capability-Surface `TileState` (in `SeriesTile.kt`)

```kotlin
/** Capability-Surface der Kachel-Region: das Werk + sein Lokal-Status + die Navigations-Callbacks.
 *  Ein [TilesSlot]-Pack rendert daraus eine Kachel; Cover-Laden/E-Ink-Filter sind host-erzwungen
 *  (FilteredAsyncImage), nicht Teil hiervon. */
data class TileState(
    val series: Series,
    val isLocal: Boolean,
    val onClick: () -> Unit,
    val onLongClick: () -> Unit,
)
```

### 5.2 `DefaultSeriesTile(state: TileState, modifier: Modifier = Modifier)` (in `SeriesTile.kt`)

Der **verbatim** aus dem heutigen `SeriesTile`-Body extrahierte Onyx-Renderer (liest `state.series`/
`state.isLocal`/`state.onClick`/`state.onLongClick`). Nutzt weiter `TileTitleBand`/`FilteredAsyncImage`.
Der `modifier` bleibt Parameter (Grid-Item-Layout) — anders als bei dialog, weil eine Kachel im Grid
einen Layout-Modifier tragen kann; per `grep` ist die Library-Call-Site ohne expliziten `modifier`, aber
der Default-Renderer behält ihn für künftige Aufrufer.

### 5.3 `SeriesTile` wird dünner Host-Wrapper (Signatur unverändert)

```kotlin
@Composable
fun SeriesTile(series: Series, isLocal: Boolean, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier) {
    LocalResolvedSlots.current.tiles(TileState(series, isLocal, onClick, onLongClick), modifier)
}
```

> **Modifier-im-Slot-Entscheidung:** Der Slot-Vertrag trägt den `modifier` als zweiten Parameter (s.u.),
> weil eine Grid-Kachel einen Layout-Modifier braucht — anders als dialog/settings (ganzflächig). Das hält
> die einzige bestehende Call-Site (`LibraryScreen`) und die neue (`GroupBrowseRoute`) unverändert.

### 5.4 Slot-Vertrag in `UiSlots.kt` (additiv)

```kotlin
typealias TilesSlot = @Composable (state: TileState, modifier: Modifier) -> Unit
```
- `UiSlotPack(header, homeHeader, dialog, settings, tiles: TilesSlot? = null)`
- `ResolvedSlots(header, homeHeader, dialog, settings, tiles: TilesSlot)`
- `UiSlots.resolve`: `tiles = pack.tiles ?: DefaultSlots.tiles`
- `DefaultSlots.tiles: TilesSlot = { state, modifier -> DefaultSeriesTile(state, modifier) }`
  (Import `TileState`, `DefaultSeriesTile`)
- Klassen-KDoc oben auf „header + homeHeader + dialog + settings + tiles gebaut" nachziehen; Soll-Regionen
  (overlay/nav) unberührt.

### 5.5 E-Ink-Invarianten (host-erzwungen)

Cover-Laden über `FilteredAsyncImage` (E-Ink-Desaturierung) + `crossfade(false)` bleibt im
`DefaultSeriesTile` — host-erzwungen, nicht Pack-Sache. Der Slot liefert nur die Kachel-Struktur.

### 5.6 Swap-Beweis (Debug-Preview, keine Nutzer-Einstellung)

`app/src/debug/kotlin/com/komgareader/app/ui/components/TileSlotPreview.kt`: ein `AlternativeTile`
(`TilesSlot`) mit **anderer Anordnung** — z. B. Titel **über** dem Cover statt Scrim-Band unten, oder
Badge unten links — plus `@Preview` mit `LocalResolvedSlots provides
UiSlots.resolve(UiSlotPack(tiles = AlternativeTile))` über einen `SeriesTile`-Aufruf mit Fake-`Series`.
Analog `DialogSlotPreview.kt`/`SettingsSlotPreview.kt`. Falls `AlternativeTile` eine Surface-Fähigkeit
bewusst weglässt, das im KDoc dokumentieren (R1/R2-Lehre).

## 6. Tests

- **Pure (`SlotFallbackTest.kt` erweitern):** zwei Tests analog settings — fehlender `tiles`-Slot fällt auf
  `DefaultSlots.tiles` zurück (`assertSame`); gelieferter überschreibt. **Echte Umlaute** (`fällt`/`zurück`/
  `überschreibt`). KDoc-Kopf nachziehen.
- **E2E (Emulator `eink_test`, 1264×1680):** Bibliothek (Stöbern) zeigt das 3-Spalten-Kachel-Grid
  **unverändert** (Cover, Lokal/Cloud-Badge, Titel-Band); Gruppen-Browse zeigt **dieselbe** Kachel jetzt
  mit rundem Cover-Rand + `TileTitleBand` (kosmetisch konsolidiert). Kachel öffnet bei Tap das Werk.
  (Test-Komga muss verbunden sein — sonst notfalls Library-Empty/Connect-Screen + Compile/Unit als Beweis.)

## 7. Akzeptanz

- `GroupSeriesCover` entfernt; `GroupBrowseRoute` nutzt `SeriesTile` (`onLongClick = {}`); tote Imports weg.
- `SeriesTile` rendert über `LocalResolvedSlots.current.tiles(...)`; `LibraryScreen.kt:162` unverändert.
- `UiSlotPack`/`ResolvedSlots`/`DefaultSlots`/`UiSlots.resolve` tragen `tiles` additiv; die vier
  bestehenden Regionen unberührt.
- `DefaultSeriesTile` verhaltens-/pixelgleich zur alten `SeriesTile` (Library E2E). Pure Fallback-Tests
  grün. Swap-Preview kompiliert.
- `architecture-seams.md` (UI-Slot-Naht: „fünf Regionen gebaut") + `big-picture-and-goals.md` Roadmap +
  Memory-Roadmap im selben Commit nachgezogen (docs-match-code).

## 8. Nicht in R3 (YAGNI)

Kein User-Tiles-Override, kein `ui-api`-Modul, keine Touch an overlay/nav-Slots. **Keine** Slot-ifizierung
der anderen Kachel-Typen (`ChapterTile`/Buch, `CollageTile`/Sammlung, `MemberTile`, `AddWorkTile`) — das
sind eigene spätere Regionen/Tasks; R3 = nur die **Serien-Kachel**. Keine Änderung am Grid/an der
Spaltenzahl der Screens.

## Bezug

Roadmap `2026-06-12-complete-ui-modularity-roadmap.md` · `architecture-seams.md` (UI-Slot-Naht) ·
`shared-structure-before-variants.md` (Konsolidierung §3) · `komga-plugins`-Skill (Capability-Rezept) ·
Vorbild-Bauten R1 `dialog` (ad8f0cc) + R2 `settings`.
