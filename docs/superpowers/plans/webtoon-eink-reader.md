# Plan: Webtoon-Reader — E-Ink-Frame-Scroll + nahtlose Multi-Kapitel-Verkettung

Branch `feat/webtoon-eink-reader` (Worktree, abgezweigt von master @ dc85daa).
Anderer Agent arbeitet parallel auf master.

## Ziel (aus User-Vorgaben)

1. **Nahtlose Multi-Kapitel-Verkettung (WICHTIGSTES)** — Webtoons haben keine
   Seiten, nur unendliches Scrollen. Alle Kapitel einer Serie werden ohne Naht
   untereinander gehängt, sodass beim Scrollen ein durchgehender Fluss entsteht.
   Der User merkt nicht, wann ein Kapitel endet. Gilt für beide Modi.
2. **Breiten-Zoom fix** — Bild füllt immer 100% Breite (kurze Seite), nie die
   lange Höhe (FillWidth). Wie das Original-Handy-Verhalten.
3. **Zwei Modi hinter globaler App-Einstellung `DisplayMode` (Default EINK):**
   - **SMARTPHONE:** smooth, frei scrollbar, animierter Kapitelübergang.
   - **EINK:** kein Free-Scroll, keine Animation. „Blättern“ = Frame-Sprung
     (≈1 Bildschirmhöhe, kleine Überlappung) über **Tap-Zonen links/rechts**
     und Hardware-/Lautstärke-Tasten. Pro Frame ein GC-Full-Refresh.
   - `DisplayMode` ist bewusst **app-weit** modelliert (später auch für
     Animationen, Farbfilter/-korrektur etc. — Funktionen, die auf dem
     Smartphone keinen Sinn ergeben).

## Bausteine

### domain (rein, TDD)
- `model/DisplayMode.kt` — enum EINK | SMARTPHONE.
- `reader/WebtoonStrip.kt` — bildet globalen (kapitelübergreifenden) Seitenindex
  auf (Kapitel, Seite-im-Kapitel) ab. Reine Funktion → für Fortschritts-Push.
- `repository/SettingsRepository.kt` — `displayMode` Flow + `setDisplayMode`.

### data
- `RoomSettingsRepository` — `display_mode`-Key, Default „EINK“.

### source-komga
- `KomgaSource.seriesIdOf(bookRemoteId)` — Serie zu einem Buch auflösen
  (für Kapitel-Liste ohne Nav-Arg).

### eink-onyx
- `OnyxRefresher.fullRefreshNow(view)` — sofortiger GC-Refresh pro Frame.

### app
- `ReaderContent.Webtoon` — flache PageRef-Liste über alle Kapitel + Strip.
- `ReaderViewModel` — im WEBTOON-Modus alle Kapitel laden (parallel),
  Strip bauen, Fortschritt kapitel-genau pushen, `frameStep`-Flow für Tasten,
  `displayMode` exponieren.
- `ReaderRoute` — Webtoon-Content + displayMode durchreichen.
- `WebtoonReaderScreen` — zwei Modi (siehe oben), FillWidth fix.
- `SettingsViewModel`/`SettingsScreen`/`Strings` — DisplayMode-Auswahl.

## Verifikation
- domain Host-Tests für `WebtoonStrip` (TDD) + DisplayMode-Default.
- `./gradlew :app:compileDebugKotlin` grün.
- Wenn Emulator/Komga verfügbar: manueller Smoke gegen echte Webtoon-Serie.
