# Gemeinsame Struktur vor der N-ten Variante extrahieren

Bevor eine **weitere Variante** eines bereits mehrfach existierenden Bausteins
dazukommt — ein vierter Reader, eine dritte Quelle, ein fünfter Dialog, ein
weiterer Viewer — wird **zuerst geprüft, was die bestehenden Varianten teilen**,
und ob dieses Geteilte schon an *einer* Stelle lebt oder als Duplikat über die
Varianten verstreut ist. Liegt es verstreut, wird die gemeinsame Schicht
**extrahiert, bevor** die neue Variante gebaut wird — nicht danach.

Das ist **Planungs**-Arbeit, kein Nachgedanke: Wer eine N-te Variante plant, macht
die Extraktion zu einem **expliziten Task im Plan**, der *vor* der neuen Variante
steht.

## Warum

Wird die N-te Variante neben N-1 leicht unterschiedliche Kopien gesetzt, driften
sie auseinander. In diesem Projekt ist das nicht nur Stil: Tap-Zonen-,
Overlay- und **Refresh-Logik** viermal leicht anders verdrahtet führt auf E-Ink
zu **sichtbar inkonsistentem Refresh-Verhalten** (Ghosting, falsche
partial/full-Promotion). Die Naht-Architektur (`architecture-seams.md`) kapselt
die *Variabilität* hinter Interfaces — diese Regel ergänzt sie um die andere
Richtung: das *Gemeinsame* gehört ebenso an genau eine Stelle.

## Vorgehen beim Planen

1. **Bestandsaufnahme:** Welche Varianten gibt es schon? Was tun sie alle gleich
   (Zustand, Mechanik, Lifecycle, UI-Gerüst)?
2. **Wo lebt das Gemeinsame?** An einer Stelle (gut) oder N-fach kopiert (Schuld)?
   Teil-geteilt (ein Composable geteilt, der State aber nicht) ist auch Schuld.
3. **Dünne Schicht extrahieren — zuerst:** ein gemeinsames Interface für den
   geteilten Zustand/Vertrag **und/oder** ein Basis-Composable/Scaffold für das
   geteilte Gerüst. Verhaltens-erhaltender Refactor mit den bestehenden Tests als
   Netz.
4. **Dann** die neue Variante *auf* dieser Schicht bauen — nicht als Parallel-Linie.

## Maß halten (YAGNI)

Die Schicht bleibt **dünn**: nur extrahieren, was wirklich geteilt ist. Keine
spekulative Basisklasse für hypothetische Zukunft, keine tiefe Vererbung —
Komposition/Delegation bevorzugen (siehe globale `functional-programming`- und
`clean-code`-Regeln, DRY + Beck's Simple Design). Drei ehrlich verschiedene Zeilen
sind besser als eine erzwungene Abstraktion. Aber dieselbe Logik N-mal *gleich*
ist Duplikation und wird extrahiert.

## Symptome (Trigger)

- „Jeder X-Screen/Handler verdrahtet Y selbst."
- Ein VM schert aus der geteilten Basis aus (eigenes VM ohne gemeinsames Interface).
- Beim Copy-Paste der (N-1)-ten Variante als Start der N-ten.
- Geteiltes Composable, aber jeder Aufrufer baut State/Lifecycle drumherum neu.

## Bezug

Ergänzt `architecture-seams.md` (Variabilität kapseln) um die Gegenrichtung
(Gemeinsames zentralisieren). Gehört zu [[project-komga-eink-reader]].
Referenz-Anlass: Reader-Chrome-Vereinheitlichung (`ReaderChromeState` +
`ReaderScaffold`) vor dem vierten Reader (NOVEL), siehe Plan
`docs/superpowers/plans/2026-06-07-novel-reflow-reader.md` Phase 3.5.
