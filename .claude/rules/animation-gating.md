# Animationen: immer über die E-Ink-Einstellung gaten

Das Zielgerät ist E-Ink (Onyx Boox): jede Bewegung erzeugt Ghosting und einen sichtbar
ruckelnden Teil-Refresh. Deshalb gilt **ausnahmslos**: **jede** Animation wird über den
Anzeige-Modus gegatet und hat eine **sofortige E-Ink-Alternative**. Eine ungegatete
Animation ist ein Bug, kein Stilfehler.

## Die Regel

- **Single Source of Truth:** `LocalEinkMode.current` (`app/ui/components/LoadingIndicator.kt`,
  in `MainActivity` aus der Display-Mode-Einstellung gesetzt; Default `true` = E-Ink).
- **E-Ink (`true`):** **keine** Bewegung — sofortiger Zustandswechsel. Kein Fade, kein Slide,
  kein Scale, kein Spinner, kein animiertes Auf-/Zuklappen.
- **Smartphone (`false`):** Animation erlaubt, aber dezent (siehe `eink-design-language`).
- **Es gibt keine Animation ohne diese Verzweigung.** Wer animiert, liefert beide Pfade.

## Muster

```kotlin
val eink = LocalEinkMode.current
AnimatedVisibility(
    visible = expanded,
    enter = if (eink) EnterTransition.None else expandVertically(expandFrom = Alignment.Top),
    exit  = if (eink) ExitTransition.None  else shrinkVertically(shrinkTowards = Alignment.Top),
) { /* … */ }
```

- **Auf-/Zuklappen:** `expandVertically`/`shrinkVertically` (nur Höhe, vertikal von oben) —
  **nie** das Default-`expandIn`/`shrinkOut`, das diagonal in die Ecke schrumpft.
- **Lade-Anzeige:** immer `LoadingIndicator` nutzen (E-Ink: statischer „Lädt…"-Text,
  Smartphone: Spinner) — nie direkt `CircularProgressIndicator`.
- **`animate*AsState` / `Animatable`:** Dauer auf `0` setzen bzw. den Zielwert direkt
  schreiben, wenn `eink` — oder ganz überspringen.
- **`Crossfade`, `AnimatedContent`:** im E-Ink-Pfad durch direktes `if`/`when`-Rendern ersetzen.

## Bezug

Konkretisiert „Keine Animationen" aus `eink-design-language.md` (verbindlich) und das
Teil-Update-Anti-Pattern aus dem `komga-eink-ui-polish`-Skill. Gehört zu [[project-komga-eink-reader]].
