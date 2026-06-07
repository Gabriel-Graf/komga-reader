# E-Ink-Designsprache (Pflicht)

Zielgerät ist ein **E-Ink-Display** (Onyx Boox Go Color 7 Gen2): langsamer Refresh, Ghosting,
gedämpfte Kaleido-Farben, kein flüssiges Animieren. Die Designsprache ist daher **technische
Notwendigkeit, kein Geschmack**. Jede UI-Arbeit folgt ihr (Spec §8).

## Die Regeln

- **Tiefe über Border, nicht Schatten:** 1.5px-Border (`MaterialTheme.colorScheme.outline`),
  runde Ecken 8px. Keine Elevation, keine Verläufe, keine Ripple — Material3 flach.
- **Keine Animationen:** sofortige State-Wechsel + gezielter Refresh statt Übergänge/Fades/Slides.
  (`AnimatedVisibility` für simples Auf-/Zuklappen ist ok, aber kein Bewegungs-Schnickschnack.)
- **Maximaler Kontrast, monochrom:** weißer Hintergrund / schwarze Schrift (Dark-Mode invertiert).
  **Keine Akzentfarbe** — Akzent = solides Schwarz bzw. invertiert Weiß. Theme: `app/ui/theme/Theme.kt`.
- **Icons:** **Lucide** (gleichmäßiger Outline-Strich, als `ImageVector` generiert mit E-Ink-Stroke
  2.5px) — monochrom, E-Ink-kräftig. Zentrale Registry `app/ui/icons/AppIcons.kt` (SSOT), generiert
  via `tools/icons`; nie Material-Icons, nie Streu-Icons. Details: `komga-eink-ui`-Skill, Sektion „Icon-System".
- **Aktions-Icons beschriftet:** im Boox-nativen Look tragen Aktionen Text-Labels, nicht nur Glyphen.
- **`BaseDialog`:** ein Composable als Basis **aller** Dialoge — sticky Header/Footer, scrollender Body,
  Hardware-Back = abbrechen. **Max. ein Dialog** gleichzeitig über dem Main-View.
- **Reader-Chrome:** Tap Mitte = Bars ein/aus, sonst immersiv. Hardware-Tasten + Tap-Zonen blättern.
- **Refresh bewusst steuern:** sichtbare Zustandswechsel über `OnyxRefresher`/`EinkController`
  laufen lassen (partial beim Blättern, full nach N Partials / bei Bildwechsel) — nicht blind invalidieren.
  (Ein geräteunabhängiger `RefreshScheduler` ist *Soll*, noch nicht gebaut — siehe `architecture-seams.md`.)

## Layout-Hinweis aus der Praxis

Ausgewogenheit zählt auf 7": Karten nicht künstlich klein halten (z. B. Hero-Karte mit großem Cover,
das den Titel trägt), granulare Metadaten weglassen, die niemand auf E-Ink scannt. Auf echter
Boox-Geometrie verifizieren — der Emulator `eink_test` ist auf **1264×1680 @ 300dpi** gestellt
(= reale Boox-Maße, siehe [[local-test-komga]]).

## i18n-Kopplung

Sichtbarer Text **immer** über `i18n` (typsichere Keys, DE+EN, Compile-Zeit-Parität). Echte
Umlaute/ß. Roh-Werte von Quellen (Status `ONGOING` etc.) lokalisiert anzeigen, nie durchreichen.

> Hinweis: Es gibt einen `komga-eink-ui`-Skill (Onyx-Look) und einen laufenden UI-Refactor im Branch
> `feat/eink-ui-refactor` (siehe [[eink-ui-refactor-worktree]]). Bei UI-Arbeit dort/diesen Skill
> konsultieren — diese Regel ist die verbindliche Kurzfassung der Spec.

Gehört zu [[project-komga-eink-reader]].
