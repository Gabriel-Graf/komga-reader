# Bidirektionaler Collections-Sync (Push + Pull)

**Datum:** 2026-06-10
**Status:** Design freigegeben
**Branch:** `feat/collections-bidirectional-sync`

## Problem

Der Sammlungen-Sync ist heute **einseitig (nur Push)**. App-Änderungen werden zum Server
geschoben (`CollectionSyncManager.push()` nach jeder Member-Änderung / per `syncNow`), aber
es gibt **keinen produktiven Pull**:

1. `CollectionSyncManager.refresh()` wird **nie** aus dem Produktionscode aufgerufen (nur Tests).
2. `refresh()` ist für das Kern-Szenario **strukturell ungeeignet**: es iteriert über
   `collection.members.map { it.sourceId }` — also nur über Quellen, in denen lokal **bereits
   Mitglieder** existieren. Eine Sammlung, die **lokal noch gar nicht existiert** (Erstverbindung:
   Server hat bereits Sammlungen), wird **nie entdeckt**. `refresh()` kann nur bekannte Sammlungen
   mergen, keine neuen **discovern**.

**Gewünscht:** Echter bidirektionaler Sync. Beim Erstverbinden eines Servers (Server hat schon
Sammlungen) erscheinen diese in der App. Laufend bei jedem Sync wird **beidseitig** abgeglichen —
auch Änderungen, die der Nutzer offline direkt am Server gemacht hat.

## Ziel-Verhalten

- **Discovery (Pull):** Server-Sammlungen, die lokal fehlen, werden lokal angelegt.
- **Merge bei Member-Änderung:** Geänderte Sammlungen werden per **Last-Write-Wins** abgeglichen.
- **Push:** Lokale Änderungen gehen weiterhin zum Server (unverändert).
- **Löschungen vom Server:** Mit Nutzer-Bestätigung lokal nachvollziehen (Modal).

## Architektur-Einordnung

Folgt den Projekt-Invarianten:

- **Naht A (Quellen):** `RemoteCollection` wird um einen UTC-Zeitstempel erweitert — Pflichtteil
  des Quellen-Vertrags. Jede `CollectionSyncSource` liefert ihn.
- **Domain bleibt pur:** Die gesamte Sync-**Entscheidungslogik** (anlegen / pushen /
  pull-überschreiben / verschwunden) liegt in einer **reinen** Domain-Funktion `planCollectionSync`.
  Voll unit-testbar, kein I/O.
- **`app` ist die Shell:** `CollectionSyncManager.fullSync()` ist die dünne I/O-Hülle (Server
  listen, Planner rufen, Plan ausführen). Quellen-Auflösung agnostisch über `ActiveSource`.
- **Geräteklassen-Bewusstsein:** Sync-Trigger sind nach Geräteklasse gegated (E-Ink sparsam,
  LCD aggressiver) — kein binäres `isEink` zementiert.
- **E-Ink-Designsprache:** Das Bestätigungs-Modal nutzt `BaseDialog`, `AppIcons`, flach/Border.

## Komponenten

### 1. Naht-Erweiterung (UTC-Zeitstempel)

#### `RemoteCollection` (`source-api`)

```kotlin
data class RemoteCollection(
    val remoteId: String,
    val name: String,
    val memberRemoteIds: List<String>,
    val updatedAt: Long,   // UTC epoch millis (GMT), niemals zonenbehaftet
)
```

- **Einheit überall:** UTC-Epoch-Millis (absoluter Instant, zeitzonenfrei).
- **Komga-Mapping:** `lastModifiedDate` (ISO-8601, trägt `Z`/Offset) → UTC-Millis über
  `Instant.parse(...)` bzw. `OffsetDateTime.parse(...).toInstant().toEpochMilli()`.
  **Niemals `LocalDateTime.parse`** (verliert den Offset → Sync-Bug).
  Helper: `KomgaMapper.parseIsoUtcMillis(s: String): Long`.
- DTOs `CollectionDto` / `ReadListDto` um `lastModifiedDate: String` ergänzen.
- OPDS/Stub implementieren `CollectionSyncSource` **nicht** → unberührt.

#### Domain-`CollectionSyncLink` (`domain`)

Das in der Entity `collection_sync_links` bereits vorhandene `updatedAt` (lokale Änderungszeit,
ebenfalls UTC-Epoch-Millis via `System.currentTimeMillis()`) wird ins Domain-Modell exponiert:

```kotlin
data class CollectionSyncLink(
    val collectionId: Long,
    val sourceId: Long,
    val remoteCollectionId: String?,
    val status: SyncStatus,
    val dirty: Boolean,
    val updatedAt: Long,   // NEU: UTC epoch millis, lokale Änderungszeit
)
```

`RoomCollectionRepository`-Mapping + `updateSyncLink` ziehen `updatedAt` durch.

### 2. Reiner Domain-Planner (`domain/usecase/CollectionSyncPlan.kt`)

```kotlin
data class DiscoveredCollection(
    val sourceId: Long,
    val kind: CollectionKind,
    val remote: RemoteCollection,
)

data class PullOverwrite(
    val collectionId: Long,
    val sourceId: Long,
    val serverMemberRemoteIds: List<String>,
)

data class VanishedCollection(
    val collectionId: Long,
    val name: String,
)

data class SyncPlan(
    val createLocal: List<DiscoveredCollection>,   // Server hat, lokal fehlt → lokal anlegen
    val pushLocal: List<Long>,                     // collectionId: lokal gewinnt → push()
    val pullOverwrite: List<PullOverwrite>,        // Server gewinnt für diese Quelle
    val vanished: List<VanishedCollection>,        // war synced, am Server weg → Modal
)

fun planCollectionSync(
    local: List<UserCollection>,
    links: Map<Long, List<CollectionSyncLink>>,         // collectionId → links
    remotePerSource: Map<Long, List<RemoteCollection>>, // sourceId → Server-Liste (beide kind zusammengeführt)
): SyncPlan
```

> Anmerkung: `remotePerSource` enthält pro Quelle die zusammengeführte Liste beider `kind`
> (Collections = SERIES, Read-Lists = BOOK). Da `RemoteCollection` das `kind` nicht trägt, hält
> die Shell die Trennung beim Listen/Ausführen; der Planner matcht über `remoteId`/Name, was pro
> Quelle eindeutig ist. (Falls Namens-Kollision SERIES↔BOOK real wird: `kind` in `RemoteCollection`
> nachziehen — vorerst YAGNI.)

#### Logik (pro Quelle × kind)

1. **Match** lokal↔Server: zuerst `link.remoteCollectionId`, sonst Name (innerhalb `sourceId`).
2. **Beide vorhanden** → **LWW**: `link.updatedAt` (lokal) vs `remote.updatedAt` (Server).
   - Server **neuer** → `pullOverwrite` (Server-Subset gewinnt für diese Quelle).
   - Lokal **neuer oder gleich** → `pushLocal`.
3. **Nur Server** (kein lokaler Match) → `createLocal` (Discovery / Erstverbindung).
4. **Nur lokal, nie synced** (`remoteCollectionId == null`, LOCAL_ONLY) → `pushLocal` (am Server anlegen).
5. **Nur lokal, war synced** (`remoteCollectionId != null`, am Server weg) → `vanished`.

- **LWW-Granularität:** pro **(Sammlung, Quelle)-Link**. Jede Quelle trägt eigenes `updatedAt` +
  eigenes Member-Subset → bleibt multi-source-kohärent und nutzt `mergeSubsets`-Semantik weiter.
- Der **Pull-Overwrite** wird in der Shell via `mergeSubsets(canonical, mapOf(sourceId to serverIds), titleFor)`
  in die kanonische Liste eingespielt und über `repo.setMembers` persistiert; der Link wird danach
  auf `updatedAt = remote.updatedAt`, `dirty = false`, `SYNCED` gesetzt (verhindert Sync-Ping-Pong).

### 3. `CollectionSyncManager.fullSync()` (`app/data`, Shell)

```kotlin
suspend fun fullSync(): List<VanishedCollection>
```

Ablauf:
1. Alle lokalen Sammlungen + Links laden.
2. Über alle aktiven `CollectionSyncSource`-Quellen `listCollections(SERIES)` + `listCollections(BOOK)`
   ziehen (best-effort pro Quelle; eine offline → Rest läuft weiter), zu `remotePerSource` mergen.
3. `planCollectionSync(...)` → `SyncPlan`.
4. Plan ausführen:
   - `createLocal` → `repo.create(name, kind)` + `repo.setMembers(...)` (Member aus Server,
     Titel-Auflösung soweit möglich) + Link `SYNCED, dirty=false, updatedAt = remote.updatedAt`.
   - `pushLocal` → bestehendes `push(collection)` wiederverwenden (unverändert).
   - `pullOverwrite` → `mergeSubsets` + `repo.setMembers` + Link auf Server-Stand schreiben.
   - `vanished` → **nicht** automatisch löschen; als Rückgabe an die UI durchreichen.
5. `vanished` zurückgeben.

`push()`, `deleteEverywhere()` bleiben unverändert. `refresh()` wird durch `fullSync()` ersetzt
und **entfernt** (inkl. seines Tests, dessen Szenarien in `CollectionSyncPlanTest` aufgehen).

### 4. Trigger + Geräteklassen-Gating (`CollectionsViewModel`)

Neue Methode `fullSync()` ruft `CollectionSyncManager.fullSync()` und schiebt das Ergebnis in
einen `StateFlow<List<VanishedCollection>>`.

| Trigger | E-Ink | LCD/Smartphone |
|---|---|---|
| Server-Verbindung / App-Start (Collections-Tab erstmals sichtbar) | ✅ | ✅ |
| Manueller Sync-Button (`syncNow` → bidirektional) | ✅ | ✅ |
| Tab-Öffnen (jedes Mal) | ❌ | ✅ |
| Periodisch im Hintergrund | ❌ | ❌ (vorerst nicht — YAGNI) |

- **Gating-Quelle:** persistierte Display-Mode-Einstellung aus `SettingsRepository` (dieselbe, die
  `LocalEinkMode` speist) — **nicht** der Compose-`LocalEinkMode` (im VM nicht verfügbar). Abgeleitetes
  „darf aggressiver syncen"-Flag (≈ `allowsMotion`). Bewusst kein binäres `isEink` zementiert.
- `syncNow` wird auf `fullSync` umgestellt. Der bisherige Kommentar („kein refresh, sonst Badge
  falsch") wird obsolet: `fullSync` schreibt Links **korrekt** (`dirty=false` + Server-`updatedAt`),
  statt blind DIRTY zu setzen.
- Das „erstmals sichtbar"-Trigger wird einmalig pro VM-Leben ausgelöst (Guard-Flag), damit
  Recompositions keinen Sync-Sturm erzeugen.

### 5. Vanished-Modal (E-Ink-konform)

Wenn `vanished` nicht leer → **ein** `BaseDialog`:
- Titel: „Am Server gelöscht". Body: Liste der betroffenen Sammlungsnamen.
- **[Hier auch löschen]** → je Sammlung `repo.delete(id)`.
- **[Hier behalten]** → Link auf `LOCAL_ONLY` / `dirty=true` → nächster Push legt sie am Server neu an.
- Hardware-Back = abbrechen (= behalten).
- Lucide-Icons via `AppIcons`, flach, 1.5px-Border, keine Animation — Designsprache-Pflicht.
- Max. **ein** Dialog gleichzeitig (Invariante).

Discovery (`createLocal`) + LWW-Pulls laufen **stumm** (kein Modal) — nur Löschungen brauchen
Bestätigung.

## Bekannte Grenzen (dokumentiert, nicht gelöst)

- **Uhr-Drift:** LWW vergleicht Geräte-Uhr (lokale Änderung) gegen Server-Uhr (`lastModifiedDate`),
  beide in UTC. Die **Zeitzonen**-Fehlerquelle ist durch UTC eliminiert. Es bleibt **echter
  Uhr-Drift** (Gerät vs. Server falsch gestellt, NTP nicht gelaufen) — bei starkem Drift kann die
  „falsche" Seite gewinnen. Tritt nur auf, wenn **dieselbe** Sammlung im engen Drift-Fenster auf
  **beiden** Seiten editiert wird → für einen Single-User-E-Ink-Reader praktisch nie. Echte Lösung
  (Vektor-Uhren/Tombstones) ist massiver Overkill und bewusst ausgeschlossen.
- **Periodischer Hintergrund-Sync:** bewusst nicht gebaut (Akku auf E-Ink). Sync läuft an
  Server-Connect/App-Start, manuell und (nur LCD) bei Tab-Öffnen.

## Tests

### Pure Unit (TDD, Kern) — `CollectionSyncPlanTest`
Je ein Test pro Pfad:
- Server-only → `createLocal` (Erstverbindung: lokal leer, Server hat 2 Sammlungen).
- Beide, Server `updatedAt` neuer → `pullOverwrite`; lokal neuer → `pushLocal`; gleich → `pushLocal`.
- Lokal-only nie synced → `pushLocal`; war synced + Server weg → `vanished`.
- Multi-Source: eine Sammlung über zwei Quellen, eine Quelle pullt, die andere pusht.
- Match per `remoteCollectionId` **und** Fallback per Name.

### Mapper-Unit
- `KomgaMapper.parseIsoUtcMillis` mit `Z` **und** explizitem Offset (`+02:00`) → identischer UTC-Wert.
- `toRemoteCollection` mappt `lastModifiedDate` korrekt (gesetzt) — Collection + Read-List.

### SyncManager (Fakes) — `CollectionSyncManagerTest`
- `fullSync` führt einen gemischten Plan korrekt aus, schreibt Links richtig, gibt `vanished` durch.
- Best-effort: eine Quelle wirft beim Listen → andere Quelle wird trotzdem gesynct.

### E2E gegen lokale Test-Komga
- Am Server via `POST /api/v1/collections` eine Sammlung anlegen, die lokal **nicht** existiert →
  App-Start/Sync → erscheint lokal (Discovery-Beweis).
- Lokal Member adden → Server hat ihn (Push).
- Server-seitig Member ändern, dann syncen → lokal übernommen (Pull/LWW).
- Server-seitig Sammlung löschen → Modal erscheint → „Hier auch löschen" entfernt lokal.

## Build-Reihenfolge

1. Naht-Erweiterung: `RemoteCollection.updatedAt` + Komga-DTOs/Mapper (`parseIsoUtcMillis`) + Tests.
2. Domain: `CollectionSyncLink.updatedAt` exponieren + Room-Mapping durchziehen.
3. Pure Planner `planCollectionSync` + `CollectionSyncPlanTest` (TDD, zuerst).
4. `CollectionSyncManager.fullSync()` (Shell) + Fake-Tests; `refresh()` entfernen.
5. `CollectionsViewModel.fullSync()` + Geräteklassen-Gating + `vanished`-StateFlow; `syncNow` umstellen.
6. Vanished-`BaseDialog` im Collections-Screen.
7. E2E gegen lokale Test-Komga; Emulator-Verifikation.

## Bezug

- `architecture-seams.md` (Naht A — `RemoteCollection`-Erweiterung im selben Commit nachziehen),
  `source-agnostic-integration.md` (Quellen-Auflösung über `ActiveSource`),
  `eink-design-language.md` + `animation-gating.md` (Modal),
  `big-picture-and-goals.md` (Geräteklassen-Gating statt binärem `isEink`),
  `roadmap-and-invariants.md` (TDD + E2E pro Feature).
</content>
</invoke>
