# A1b: Reader-Chrome deklarativ — `tapModifier` → `ReaderTapZones`-Deskriptor — Design-Spec

**Stand:** 2026-06-12 · **Status:** Design (gebaut wird direkt) · **Sub-Projekt A1b** der
Roadmap `…complete-ui-modularity-roadmap.md`. Nachfolger von **C1** (readerChrome-Region), Vorläufer von L1/L2.

> **Self-contained.** Vorher lesen: `architecture-seams.md` (readerChrome-Region/Naht B), `big-picture-and-goals.md`
> (ui-modularity → „Reader-Chrome deklarativ", deklarativ-statt-opak). Modul-Stand: `ReaderScaffoldState` lebt
> in `:ui-api` (`com.komgareader.ui.slots`), Renderer/Wrapper in `app/ui/reader/ReaderScaffold.kt` (A1).

## 1. Ziel

Die letzte opake Stelle der readerChrome-Region beseitigen: `ReaderScaffoldState.tapModifier: Modifier?` ist
ein **beliebiger Modifier** (jede Geometrie, jede Geste) — ein deklarativer/externer Pack kann das nie nachbauen.
A1b ersetzt es durch einen **endlichen Daten-Deskriptor** `ReaderTapZones`: die **Geometrie gehört dem Host**
(heute: horizontale Drittel), der Screen/Pack liefert **pro Zone nur die Aktion** (Daten, kein Modifier).
Das ist die „Tap-Zone→Aktion"-Form aus der Big-Picture (deklarativ, kein opaker Blob), in-tree — die
externe/Enum-Form folgt mit L1/L2.

## 2. Vertrag (neu, `:ui-api`, `com.komgareader.ui.slots`)

Neue Datei `ui-api/src/main/kotlin/com/komgareader/ui/slots/ReaderTapZones.kt`:
```kotlin
/**
 * Deklarative Tap-Zonen-Beschreibung der Reader-Region. Die **Geometrie gehört dem Host**
 * (endliches Vokabular), der Screen/Pack liefert pro Zone nur die **Aktion** — kein opaker
 * Modifier. Ersetzt das frühere bespoke `tapModifier`. Additiv erweiterbar: weitere Geometrien
 * (Hälften, Quadranten) kommen als neue Fälle hinzu, ohne bestehende zu brechen.
 */
sealed interface ReaderTapZones {
    /** Horizontale Drittel: links/mitte/rechts → je eine Aktion. Heute die einzige Geometrie. */
    data class HorizontalThirds(
        val left: () -> Unit,
        val center: () -> Unit,
        val right: () -> Unit,
    ) : ReaderTapZones
}

/**
 * Pure Zonen-Dispatch: ruft anhand des **normalisierten** Tap-Anteils (x/Breite ∈ [0,1]) die
 * passende Zonen-Aktion. Geometrie an genau einer Stelle → unit-testbar ohne Compose
 * (siehe `ReaderTapZonesTest`). Grenzen wie bisher: < 1/3 links, > 2/3 rechts, sonst Mitte.
 */
fun ReaderTapZones.HorizontalThirds.dispatch(xFraction: Float) = when {
    xFraction < 1f / 3f -> left()
    xFraction > 2f / 3f -> right()
    else -> center()
}
```

`ReaderScaffoldState` (`com.komgareader.ui.slots`): Feld `tapModifier: Modifier?` **→** `tapZones: ReaderTapZones?`.
`null` = der Screen behandelt Taps **selbst** (Escape-Luke für Comic-Panel-Hit-Test/Zoom) → **kein** Host-Tap-Layer.
KDoc anpassen; ungenutzten `Modifier`-Import in der Surface-Datei entfernen, falls dadurch tot.

## 3. Host (`app/ui/reader/ReaderScaffold.kt`)

**Wrapper `ReaderScaffold(...)`:**
- Param `tapModifier: Modifier? = null` **→** `tapZones: ReaderTapZones? = ReaderTapZones.HorizontalThirds(onPrev, chrome::toggleChrome, onNext)`
  (Default-Arg referenziert die vorangehenden Params — Standard-Drittel-Navigation, verhaltensgleich zum alten
  „kein tapModifier"-Fall für Paged/Epub/Novel).
- Param `showTapZoneHints: Boolean = tapModifier == null` **→** `= true` (die Standard-Drittel-Reader wollen Hints;
  bespoke Reader setzen `false` explizit — s. §4).
- State-Bau: `tapModifier = tapModifier` **→** `tapZones = tapZones`.

**`DefaultReaderScaffold`:** den `val taps = state.tapModifier ?: Modifier…detectTapGestures{…}`-Block ersetzen durch:
```kotlin
// Tap-Zonen: die Geometrie (Drittel) gehört dem Host; der Screen liefert die Aktionen.
// null = der Screen behandelt Taps selbst (Comic) → kein Tap-Layer.
when (val zones = state.tapZones) {
    is ReaderTapZones.HorizontalThirds -> Box(
        Modifier.fillMaxSize().pointerInput(zones) {
            detectTapGestures { offset -> zones.dispatch(offset.x / size.width.toFloat()) }
        },
    )
    null -> Unit
}
```
Imports: `ReaderTapZones` + `dispatch` ergänzen; `detectTapGestures`/`pointerInput`/`fillMaxSize` bleiben genutzt.
**Verhaltens-/Schicht-gleich:** der frühere leere `Box(taps)`-Layer (Comic: leerer Modifier = keine Interzeption)
entspricht jetzt dem `null`-Zweig (kein Layer) — beide interzipieren nichts, sichtbare Z-Reihenfolge unverändert.

## 4. Die fünf Reader

| Reader | Änderung |
|---|---|
| **PagedReaderScreen** | **keine** — kein tap-Arg → Default-Drittel (`onPrev`/toggle/`onNext`) + Hints. Call-Site unverändert. |
| **EpubReaderScreen** | **keine** — wie Paged. |
| **NovelReaderScreen** | **keine** — wie Paged (`onPrev`/`onNext` = prev/next-Page). |
| **WebtoonReaderScreen** | `tapModifier = Modifier…pointerInput(eink,pageCount){…}` **→** `tapZones = if (eink) HorizontalThirds(left={scope.launch{jumpFrame(-1)}}, center={chrome.toggleChrome()}, right={scope.launch{jumpFrame(1)}}) else HorizontalThirds(toggle,toggle,toggle)` (alle drei = `{ chrome.toggleChrome() }`). Plus `showTapZoneHints = false`. `onPrev={}`/`onNext={}` bleiben. Tote Imports (`detectTapGestures`/`pointerInput`) entfernen, `ReaderTapZones` importieren. |
| **ComicReaderScreen** | `tapModifier = Modifier` **→** `tapZones = null` + `showTapZoneHints = false`. Die bespoke Tap-Behandlung in der content-Lambda (Panel-Hit-Test/Zoom, braucht Viewport-Geometrie) bleibt **unverändert** = die Escape-Luke. |

> Hardware-Tasten (Volume) sind ein **separater Pfad** (`MainActivity`/VMs) — A1b fasst sie **nicht** an.

## 5. Tests

Neue `ui-api/src/test/kotlin/com/komgareader/ui/slots/ReaderTapZonesTest.kt` (pure JVM, JUnit5, echte Umlaute):
- linker Drittel-Anteil (z. B. 0.1) ruft `left`, nicht `center`/`right`.
- mittlerer Anteil (0.5) ruft `center`.
- rechter Anteil (0.9) ruft `right`.
- Grenzen: knapp < 1/3 → links, knapp > 2/3 → rechts, exakt 1/3 und 2/3 → Mitte (Grenzverhalten wie `<`/`>`).
- (Aktionen über aufgezeichnete `var fired` prüfen — kein Compose nötig.)

## 6. Akzeptanz

- `ReaderTapZones` (sealed, `HorizontalThirds`) + `dispatch` in `:ui-api`; `ReaderScaffoldState.tapZones` statt
  `tapModifier`. Host interpretiert die Geometrie an **einer** Stelle. Comic opt-out via `null`.
- `./gradlew :ui-api:test :app:assembleDebug` grün. `ReaderTapZonesTest` grün.
- **E2E** (Emulator `eink_test`): Paged/Comic-Reader — Links-Tap = vorige Seite, Rechts-Tap = nächste, Mitte =
  Chrome toggeln; Hints erscheinen bei den Standard-Readern, **nicht** bei Webtoon/Comic; Comic-Panel-Zoom
  (guided) unverändert. Verhaltensgleich zu vorher.
- **docs-match-code (selber Branch):** `architecture-seams.md` (readerChrome-Region: `tapModifier` → deklaratives
  `ReaderTapZones`, Comic-Escape-Luke via `null`), `big-picture-and-goals.md` (Reader-Chrome deklarativ: A1b von
  Soll → Ist; offen bleibt nur der externe Lader L1/L2 + Enum-Aktionsform), Memory-Roadmap [[ui-modularity-roadmap]].

## 7. Nicht in A1b (YAGNI)

**Kein** Enum-Aktionsvokabular (`TapAction { PREV_PAGE … }`) — die Aktionen bleiben Callbacks (in-tree); die
Enum-/externe-Deskriptor-Form kommt mit L1/L2. **Keine** neue Geometrie (Hälften/Quadranten — additiv, wenn
gebraucht). **Keine** RTL-Implementierung (`ReadingDirection.RTL` erreicht heute keinen Reader — separat). **Kein**
Anfassen der Hardware-Tasten, des Comic-Panel-Hit-Tests, der Refresh-/Naht-B-Pfade.

## Bezug

Roadmap `…complete-ui-modularity-roadmap.md` · `architecture-seams.md` (readerChrome/C1) ·
`big-picture-and-goals.md` (ui-modularity). Nachfolger: **L1/L2** (externer Pack-Lader + Enum/Deskriptor-Form).
