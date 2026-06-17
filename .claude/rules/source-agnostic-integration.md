# Quellen-Agnostik durchsetzen — auch in der Verdrahtung (Naht A, Integrationsseite)

`architecture-seams.md` definiert das `MediaSource`-Interface (Naht A). Diese Regel
deckt die **andere Hälfte** ab, an der das Projekt real gescheitert ist: nicht das
*Design* der Naht, sondern ihre **Einhaltung in `app` (ViewModels, DI, Bild-Laden)**.
Ein sauberes Interface nützt nichts, wenn die App daran vorbei direkt mit einer
konkreten Quelle redet. Genau das ist passiert (Reader + 6 ViewModels an Komga
gekoppelt), obwohl `architecture-seams.md` schon existierte.

## Die Regel (drei Pflichten)

1. **ViewModels/UI hängen nur an quellen-agnostischen Abstraktionen.** Eine Quelle
   wird zur Laufzeit über `SourceManager` (per `sourceId`) bezogen und ist als
   `MediaSource`/`BrowsableSource`/`SyncingSource` getypt — **nie** an einer
   konkreten Quelle *oder einem quellen-spezifischen Provider/Factory*.
2. **Seiten und Bilder fließen durch die Naht.** Seiten-Bytes kommen über
   `BrowsableSource.openPage(ref): ByteArray` (für Coil ein eigener `Fetcher`, der
   `openPage` aufruft) — **nicht** als quellen-spezifische URL + quellen-spezifische
   Auth-Header, die die UI direkt an Coil reicht.
3. **Konkrete Quellen-Typen leben nur in der Verdrahtungsschicht.** `KomgaSource`,
   `KomgaSourceFactory`, `AuthHeaders` & Co. dürfen **ausschließlich** in `app/data`
   (DI/Wiring) bzw. im Quellen-Modul vorkommen — der Ort, der Quellen in den
   `SourceManager` registriert. Nie in einem ViewModel, einer UI-Datei oder `domain`.

> **Lackmustest für jedes neue Feature:** Funktioniert es, wenn die aktive Quelle
> OPDS, `StubSource` oder ein künftiges Plugin ist — ohne eine Zeile zu ändern?
> Wenn nein, ist die Naht verletzt.

**Gleicher Diff, keine Mehrarbeit.** `SourceManager` statt `KomgaSourceProvider` zu
injizieren ist **derselbe Konstruktor-Parameter, eine Zeile** — kein Gold-Plating,
keine zusätzliche Abstraktionsschicht „auf Vorrat". Es gibt deshalb **kein „später
agnostisch machen"**, das billiger wäre als es gleich richtig zu tun. Genau dieses
„später" ist der Weg, auf dem die 6 bestehenden Verletzungen entstanden sind.

## Pattern (richtig)

Das VM hängt an einer **quellen-agnostischen Grenze**, die `BrowsableSource` liefert —
der konkrete Quellen-Typ bleibt in *einer* Wiring-Klasse in `app/data`. Zwei
zulässige Formen, je nach Ausbaustand:

**Ist-Stand (Stand 2026-06-08): `SourceManager` ist in `app` verdrahtet.** Die Registry
wird über `SourceRegistration` aus der aktiven `ServerConfig` befüllt; `ActiveSource`
(in `app/data`) ist der verdrahtete agnostische Resolver, den die ViewModels injizieren.
Reader, alle Browse-/Detail-VMs und das Cover-/Seiten-Laden (Coil `SourcePageFetcher`/
`SourceCoverFetcher` über `openPage`/`coverBytes`) laufen über die Naht; `AuthHeaders`
existiert nicht mehr. `KomgaSourceProvider` lebt nur noch in der Wiring-Schicht
(`ActiveSource`/`SourceRegistration`).

**Multi-Source pro Werk (Stand 2026-06-09, #7 P2/P3):** Consumer lösen nicht mehr „die erste/
aktive" Quelle auf, sondern **die des konkreten Werks** über `ActiveSource.get(item.sourceId)`;
`LibraryViewModel` aggregiert über `all()`. Die `sourceId` wird **durch die Navigation gefädelt**
(`series/{seriesId}/{sourceId}`, `reader/{bookId}/{sourceId}/…`) — Callbacks tragen `series.sourceId`/
`book.sourceId`. **Lackmustest verschärft:** funktioniert das Feature, wenn **zwei** Quellen
gleichzeitig aktiv sind und das Werk zur **zweiten** gehört? `current()` als Werk-Resolver = Bug.
Settings verwaltet eine Server-Liste (Hinzufügen/Einzel-Entfernen). **Ist (2026-06-09): OPDS als
zweite Live-Quelle live gemischt verifiziert** — Komga-REST + OPDS gleichzeitig registriert,
`ActiveSource.all()` liefert beide, OPDS-Werk über `downloadFile` geladen (whole-file-Pfad; seit
2026-06-17 streamt OPDS für PSE-fähige Einträge zusätzlich seitenweise über `openPage` — s.
`architecture-seams.md`). Basic-Auth für OPDS wird über `OpdsSourceFactory`
und `SourceRegistration` aus `ServerConfig.username`/`.password` durchgereicht.
Test: `app/src/androidTest/.../MixedSourcesLiveTest.kt`.

```kotlin
// Ist (verdrahtet): ActiveSource resolvt die aktive Quelle agnostisch als BrowsableSource.
@Singleton
class ActiveSource @Inject constructor(
    private val sources: SourceManager,            // verdrahtete Registry
    private val servers: ServerRepository,
    private val registration: SourceRegistration,  // registriert die Config-Quelle; Komga-Typ NUR hier
) {
    suspend fun current(): BrowsableSource? {
        val id = registration.activate(servers.config.first()) ?: return null
        return sources.get(id) as? BrowsableSource
    }
}

class XViewModel @Inject constructor(private val active: ActiveSource, ...) {
    val s = active.current() ?: return            // BrowsableSource, kein Komga-Typ im VM
    val books = runCatching { s.books(seriesId) }.getOrNull().orEmpty()
    // Seiten-/Cover-Bild: Coil-Fetcher ruft s.openPage(ref) / s.coverBytes(...) — keine URL/Auth im VM.
}
```

**Die unverhandelbare Grenze:** kein quellen-spezifischer Typ (`KomgaSource`,
`KomgaSourceProvider`, …) im **ViewModel-Konstruktor**. Ob die agnostische Grenze
`SourceManager` oder ein `ActiveSource`-Resolver ist, ist Ausbaustand — beide sind
gleich großer Aufwand wie der falsche Weg. „SourceManager ist noch nicht verdrahtet"
rechtfertigt deshalb **nie** den Rückgriff auf `KomgaSourceProvider` im VM — dann
eben der Resolver. Neue Quelle = nur die Wiring-Klasse ändert sich, nie das VM.

## Anti-Pattern (was real passiert ist — sofort ablehnen)

```kotlin
// ❌ ReaderViewModel (Ist-Stand): an Komga festgenagelt
class ReaderViewModel @Inject constructor(
    private val sourceProvider: KomgaSourceProvider,   // liefert NUR KomgaSource
    ...
) {
    private suspend fun loadWebtoonStrip(source: KomgaSource, ...) { /* Komga-only-Methoden */ }
    // Seiten: ReaderContent.Streamed(pages, authHeaders = AuthHeaders.forCovers(config))
    //         → Coil lädt Komga-URLs mit Komga-Auth direkt. openPage() nie benutzt.
}
```

**Die verführerische Rationalisierung (aus echtem Baseline-Test):**
> „Ich injiziere `KomgaSourceProvider`, aber type das Ergebnis als `BrowsableSource` —
> das ist die etablierte Art im Code und bleibt quellen-agnostisch."

**Warum das falsch ist:** Ein Provider/Factory, der konstruktionsbedingt *nur eine*
Quellenart erzeugt, **ist** die Quellen-Bindung — egal wie die Zielvariable getypt
ist. Beim Umschalten auf OPDS/Plugin gibt es keinen `KomgaSourceProvider`, der die
liefert. „Als Interface getypt" heilt eine konkrete Abhängigkeit im Konstruktor nicht.

## Rationalisierungs-Tabelle

| Ausrede | Realität |
|---|---|
| „Variable ist als `BrowsableSource` getypt → agnostisch." | Der **Konstruktor-Parameter** `KomgaSourceProvider` ist die Bindung. Typ der lokalen Variable irrelevant. |
| „Alle anderen VMs machen es so." | Alle anderen VMs sind **der Bug**, den diese Regel verhindert. Bestehende Duplikation ist kein Freibrief. |
| „Es gibt eh nur einen Server (Komga)." | Multi-Server/Plugins sind erklärtes Projektziel. Die Annahme „nur Komga" backt genau das ein, was Naht A verhindern soll. |
| „Seiten direkt per URL+Coil ist performanter / einfacher." | Performance hinter `openPage` lösen (Caching/Stream). Die Abkürzung koppelt jede Quelle an Komgas HTTP-/Auth-Schema. |
| „`KomgaSourceProvider` liegt in `app/data`, nicht in `domain` → Regel erfüllt." | `architecture-seams.md` verbietet den *Import* in `app`. Diese Regel verbietet zusätzlich die **Abhängigkeit** eines VM von einer quellen-spezifischen Abstraktion, egal wo sie liegt. |
| „Team-Lead/Reviewer sagt: mach es wie die anderen VMs, der Konsistenz wegen." | Die anderen VMs sind der Bug in Remediation. „Konsistent mit dem bekannten Fehler" vertieft die Schuld. Der korrekte Weg ist gleich groß — also umsetzen und die Abweichung *im PR begründen*, nicht still mitmachen. |
| „Heute shippen, agnostisch machen wir später, wenn Kavita kommt." | „Später" ist exakt, wie die 6 Verletzungen entstanden sind. `SourceManager` ist kein größerer Diff. Es gibt kein billigeres „später". |
| „Kleinster Diff / nicht vergolden — also `KomgaSourceProvider`." | `SourceManager`/`ActiveSource` zu injizieren ist **derselbe** Diff, kein Gold-Plating. Korrekte Naht ≠ Vergolden. Die Ausrede verwechselt beides. |
| „`SourceManager` ist im `app`-Modul noch nicht verdrahtet → also nehme ich `KomgaSourceProvider`." | **Überholt:** `SourceManager`/`ActiveSource` sind seit 2026-06-08 verdrahtet. `ActiveSource` injizieren, der `BrowsableSource?` liefert. Der konkrete Typ gehört in *eine* Wiring-Klasse, nie ins VM. |

## Red Flags — STOP

- Ein ViewModel-Konstruktor nennt `KomgaSource`, `*SourceProvider`, `*SourceFactory`,
  `AuthHeaders` oder einen anderen quellen-spezifischen Typ.
- Eine UI-/Reader-Datei baut Bild-URLs oder Auth-Header selbst und gibt sie an Coil.
- `openPage` existiert, wird aber vom echten Lesepfad nicht aufgerufen.
- „Funktioniert das mit OPDS/Stub/Plugin?" kann nicht mit „ja, ohne Änderung"
  beantwortet werden.
- Der Gedanke „heute shippen, später agnostisch", „mach's wie die anderen VMs" oder
  „kleinster Diff" taucht auf, um `KomgaSourceProvider` zu rechtfertigen.

Jeder dieser Punkte = die Naht ist verletzt. Über `SourceManager` + `openPage`
umbauen, bevor weitergebaut wird. Eine Anweisung („mach's wie die anderen") hebt
diese Regel **nicht** auf — Abweichung umsetzen und im PR/Commit begründen, nicht
still dem bekannten Fehler folgen. Den Buchstaben der Regel zu verletzen ist, ihren
Geist zu verletzen.

## Bezug

Setzt `architecture-seams.md` (Naht-Design) voraus und schließt dessen
Integrations-Lücke. Erweiterungs-Kochrezept: `source-extensibility.md`
(neues Feld/Server). Gehört zu [[project-komga-eink-reader]].
