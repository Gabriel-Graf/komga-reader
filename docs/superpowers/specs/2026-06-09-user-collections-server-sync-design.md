# User-Collections mit server-agnostischem Teil-Sync — Design

**Datum:** 2026-06-09
**Module:** `source-api` (Naht A erweitern), `domain` (Modell + Repo-Interface), `source-komga`
(Impl + Endpunkte), `data` (Room + Sync-Engine), `app` (UI, ViewModels)
**Status:** Genehmigt (Brainstorming), bereit für Plan

## Ziel

Die App kann bisher nur **Server-Inhalte konsumieren** (Libraries/Container in „Gruppen"
verlinken). Dieses Feature ergänzt die **umgekehrte Richtung**: Der Nutzer legt selbst eine
**Collection** an, benennt sie, sammelt darin handverlesene Werke — und diese werden
**server-agnostisch zum Server gesynct**, sodass sie auf anderen Geräten erscheinen.

Kernentscheidungen (aus dem Brainstorming):

1. **Lokal-first, Server best-effort.** Eine Collection lebt immer lokal und funktioniert mit
   *jeder* Quelle. Kann die Quelle Collections schreiben (Komga-Admin), wird zusätzlich
   gesynct; sonst bleibt sie lokal — mit sichtbarem **Badge** und einem **read-only
   Erklär-Popup** beim Antippen.
2. **Zwei Granularitäten, ein Konzept.** Eine Collection sammelt entweder **ganze Serien**
   oder **einzelne Bücher/Kapitel** — unterschieden durch `memberKind`, **nicht** durch zwei
   parallele Linien (Regel `shared-structure-before-variants.md`). Server-Mapping intern:
   `SERIES → /api/v1/collections`, `BOOK → /api/v1/readlists`.
3. **Eigenes Konzept neben „Gruppen".** Collections sind getrennt von Shelves (die ganze
   Container verlinken). Sie teilen sich UI-Bausteine (Tiles, Dialog), haben aber eigenen
   Datenpfad + eigene Sync-Naht. Mental-Modell wie Komga selbst (Libraries vs Collections).
4. **Kein Typ-Feld.** Nur `name` + `memberKind` + geordnete Mitglieder. Hält die
   deterministische Viewer-Auflösung (Invariante #4) unberührt — der Per-Werk-Viewer bleibt
   bei `Series.contentTypeOverride`, kein Mehrfach-Mitgliedschaft-Konflikt.
5. **Quellen-übergreifend mit Teil-Sync + Merge.** Eine Collection darf Werke aus **mehreren
   Quellen** mischen. Beim Sync wird in **jeder betroffenen sync-fähigen Quelle** dieselbe
   Collection (per Name) angelegt und **nur deren Subset** einsynchronisiert. Beim
   Pull/Refresh werden die Subsets aus N Quellen zur **kanonischen App-Liste gemerged**.

## Faktenlage Komga (verifiziert gegen `gotson/komga` master)

| Konzept | gruppiert | Mehrfach-Mitgliedschaft | anlegen/ändern |
|---|---|---|---|
| **Library** | Series | nein (1 Series → 1 Library, Dateisystem) | nur Admin |
| **Collection** | **Series** | **ja (N:M)** | **nur Admin** |
| **Read List** | **Books** | **ja (N:M)** | **nur Admin** |

- Mitgliedschaft wird per **Voll-Ersetzung** gesetzt: `PATCH` mit kompletter geordneter
  `seriesIds`/`bookIds`-Liste (deren Reihenfolge = manuelle Ordnung). Es gibt **kein**
  Add-one/Remove-one. → Passt exakt zu unserem „pro Quelle das Subset neu pushen".
- **Endpunkte:**
  - Collections: `GET/POST /api/v1/collections`, `GET/PATCH/DELETE /api/v1/collections/{id}`,
    `GET /api/v1/collections/{id}/series`. Create-Body
    `{ name, ordered, seriesIds[] }` (seriesIds `NotEmpty`, `UniqueElements`).
    Update-Body alle Felder **nullable** (weglassen = unverändert).
  - Read Lists: `GET/POST /api/v1/readlists`, `GET/PATCH/DELETE /api/v1/readlists/{id}`,
    `GET /api/v1/readlists/{id}/books`. Create-Body `{ name, summary, ordered, bookIds[] }`.
- **Kein Typ-/Content-Type-Feld** auf Collection/Read-List — nur `name` (+ Read-List
  `summary`) + `ordered` + Mitglieder.
- **Admin-Rolle** ist via `GET /api/v1/users/me` (Feld `roles`) **vorab** lesbar → echte
  Capability-Probe statt blind auf `403` zu laufen.
- **OPDS ist read-only** (nur `@GetMapping`): Collections/Read-Lists sind **browsebar**, aber
  **nicht** erstell-/änderbar. → OPDS implementiert die Schreib-Naht nicht.

## Ist-Zustand (relevante Nahtstellen)

- **Naht A** (`source-api/.../source/MediaSource.kt`): `MediaSource` (Basis),
  `BrowsableSource` (Lesen), `SyncingSource` (Fortschritt schreiben), `ContainerSource`
  (Container enumerieren). **Kein** Collection-Schreibpfad.
- **Schreib-Muster (Vorbild):** Lesefortschritt — `SyncingSource.pushProgress` +
  lokales `ReadProgressEntity(dirty)` → Push wenn online. Genau dieses Offline-first-Muster
  übernehmen wir.
- **Multi-Source verdrahtet:** `ActiveSource.get(sourceId)`/`all()`; jede Entität trägt
  `sourceId`; `SourceManager` Registry. `KomgaSource` implementiert
  `BrowsableSource, SyncingSource, ContainerSource`.
- **Shelf** (`domain/model/Shelf.kt`, `data` Room, UI „Gruppen") = lokale Gruppierung ganzer
  Container + `defaultContentType`. **Bleibt unangetastet** — Collections sind separat.
- **Series.remoteId / Book.remoteId** + `sourceId` sind die stabilen Mitglieder-Schlüssel.

## Architektur

### 1 — Domänen-Modell (`domain/model/UserCollection.kt`)

```kotlin
enum class CollectionKind { SERIES, BOOK }

/** Kanonische, quellen-übergreifende Vereinigung. Identität über Quellen hinweg = [name]. */
data class UserCollection(
    val id: Long,                          // lokal, autogen
    val name: String,
    val kind: CollectionKind,
    val members: List<CollectionMember>,   // geordnet (App hält die kanonische Ordnung)
)

data class CollectionMember(
    val sourceId: Long,
    val remoteId: String,                  // Series- bzw. Book-remoteId der Quelle
    val title: String,                     // denormalisiert → offline anzeigbar
)
```

Kein Typ-Feld. Die App-Liste ist die kanonische Vereinigung über alle Quellen.

### 2 — Naht A: optionales Capability-Interface (`source-api`)

Additiv, analog `ContainerSource` — Quellen implementieren nur, was sie können. **Keine**
quellenspezifischen Annahmen ins Interface (Plugin-Bereitschaft):

```kotlin
/** Eine vom Server gehaltene Collection/Read-List (eine Quelle). */
data class RemoteCollection(
    val remoteId: String,
    val name: String,
    val memberRemoteIds: List<String>,     // geordnet
)

interface CollectionSyncSource : MediaSource {
    suspend fun canWriteCollections(): Boolean                 // Komga: Rolle ADMIN?
    suspend fun listCollections(kind: CollectionKind): List<RemoteCollection>
    suspend fun createCollection(kind: CollectionKind, name: String, memberRemoteIds: List<String>): RemoteCollection
    suspend fun updateCollection(kind: CollectionKind, remoteId: String, name: String, memberRemoteIds: List<String>)
    suspend fun deleteCollection(kind: CollectionKind, remoteId: String)
}
```

- `memberRemoteIds` immer als **Voll-Liste** (deckt Komgas Replace-Semantik 1:1).
- `kind` wählt im Impl die Endpunkt-Familie (collections vs readlists).
- **Komga** implementiert es (Admin → schreibbar; Nicht-Admin → `canWriteCollections()=false`).
- **OPDS** implementiert es **nicht** → `ActiveSource.get(id) as? CollectionSyncSource == null`
  → diese Mitglieder bleiben lokal.

### 3 — Sync-Engine: Fan-out + Merge (`app`/`data`, imperative Shell)

`CollectionSyncManager` — der Kern. Hält **keine** HTTP-Details (die liegen im Quellen-Impl),
nur die quellen-agnostische Orchestrierung über `ActiveSource`.

**Push** (eine App-Collection → N Server):
1. Mitglieder nach `sourceId` gruppieren.
2. Pro `sourceId`: `active.get(sourceId) as? CollectionSyncSource`.
   - `null` oder `!canWriteCollections()` → Link-Status `UNSUPPORTED`/`FORBIDDEN`, Subset
     bleibt lokal.
   - sonst: existiert ein gespeicherter `remoteCollectionId`? → `updateCollection(...)`;
     sonst **Namens-Adoption** (s. u.) → ggf. `createCollection(...)`. Subset in kanonischer
     Reihenfolge pushen, `remoteCollectionId` + Status `SYNCED` speichern, `dirty` löschen.

**Pull/Refresh** (N Server → kanonische App-Liste):
1. Pro sync-fähiger Quelle die Server-Collection laden (per `remoteCollectionId`, sonst Name).
2. Deren `memberRemoteIds` zu `CollectionMember(sourceId, remoteId, title)` mappen.
3. **Merge:** Subsets aus N Quellen + lokale (nicht-sync-fähige) Mitglieder zur kanonischen
   Liste vereinen. Reihenfolge: App-Ordnung ist Wahrheit; neue Server-Mitglieder hinten
   angehängt.

**Pure, TDD-bare Funktionen** (in `domain`, keine I/O):
- `groupBySource(members) : Map<Long, List<CollectionMember>>`
- `mergeSubsets(canonical, perSourceRemote) : List<CollectionMember>`
- `deriveStatus(link, online, canWrite) : SyncStatus`

### 4 — Persistenz + Offline-first (`data`, Room)

Neue Tabellen (mit Migration; Recreate-Table-Muster beachten — siehe
[[room-migration-destructive-pitfall]]):

- `collections(id, name, kind)`
- `collection_members(collectionId, sourceId, remoteId, title, position)` —
  FK→collections, Index auf collectionId.
- `collection_sync_links(collectionId, sourceId, remoteCollectionId?, status, dirty, updatedAt)`
  — Status `SYNCED · DIRTY · LOCAL_ONLY · UNSUPPORTED · FORBIDDEN`.

`CollectionRepository` (domain-Interface, Room-Impl in `data`): beobachten, anlegen,
umbenennen, Mitglied hinzufügen/entfernen/umsortieren, löschen — markiert betroffene
Source-Links `dirty`. Push opportunistisch (online + erlaubt), wie der Fortschritt-Sync.

### 5 — UI (eigene Fläche, teilt Bausteine)

- **Collections-Übersicht** (eigener Screen): erreichbar als eigener Einstieg in der
  „Gruppen"-Region (Default: zweiter Tab/Umschalter neben den Shelf-Gruppen, „eigene Fläche").
  Tiles mit **Sync-Badge**. Badge-Tap → `EinkInfoDialog`
  (read-only, nur X): erklärt z. B. „Die hinterlegte OPDS-Quelle unterstützt keinen Sync →
  nur lokal, kein Abgleich zwischen Geräten." Bei gemischtem Status: pro Quelle eine Zeile.
- **Anlegen:** `EinkModal` — Name + `kind` (Serien/Bücher) wählen.
- **Hinzufügen:** Aktion „Zu Collection hinzufügen" aus `SeriesDetailScreen` (für SERIES) und
  aus der Buch-/Kapitel-Liste (für BOOK) → Auswahl-Sheet bestehender Collections + „neu".
- **Detail:** geordnete Mitglieder-Liste (umsortieren, entfernen), Sync-Status sichtbar,
  „jetzt syncen"-Aktion. E-Ink-Designsprache, Animation über `LocalDisplayBehavior` gegatet.

### 6 — Komga-Mapping (`source-komga`)

`KomgaApi` um collections/readlists-Endpunkte + `GET /users/me` erweitern. `KomgaSource`
implementiert zusätzlich `CollectionSyncSource`:
- `canWriteCollections()` → `me.roles.contains("ADMIN")` (gecacht pro Sitzung).
- `SERIES` → collections-Endpunkte, `BOOK` → readlists-Endpunkte (ein `when(kind)`).
- `403` aus Schreib-Calls defensiv → als `FORBIDDEN` an die Engine, nie Crash.
- Mapping in `KomgaMapper` (DTO↔`RemoteCollection`), Mapper-Unit-Tests gesetzt **und** leer.

### 7 — Kanten-Entscheidungen (festgelegt)

- **Namens-Kollision am Server:** Existiert beim ersten Push schon eine Server-Collection
  gleichen Namens → **adoptieren** (per Name verlinken + mergen), **nicht** blind neu anlegen
  (admin-global → sonst Dubletten). `remoteCollectionId` der adoptierten merken.
- **Löschen einer App-Collection:** fragt per Bestätigungs-`EinkModal`, ob die Server-
  Collections in den betroffenen Quellen **mitgelöscht** werden. **Default: ja, mit
  Bestätigung** (so genehmigt). Abbruch/Nein → nur lokal entkoppeln, Server-Objekte bleiben.
- **Einzelnes Werk entfernen:** Subset der betroffenen Quelle neu pushen (Replace).
- **Reihenfolge:** App hält die kanonische Ordnung; pro Quelle wird das geordnete Subset mit
  `ordered=true` gepusht.

## Tests

- **Pure (TDD zuerst):** `mergeSubsets`, `groupBySource`, `deriveStatus` — gesetzt **und**
  leer/Konflikt-Fälle (Werk in zwei Quellen, nicht-sync-fähige Quelle, Reorder).
- **Vertrag (MockWebServer):** Komga `CollectionSyncSource` — create/update/delete für
  SERIES (collections) **und** BOOK (readlists); `canWriteCollections()` Admin/Nicht-Admin;
  `403`-Pfad → `FORBIDDEN` ohne Crash.
- **Migration:** Room-Upgrade-Test auf echter DB-Instanz (kein inMemory-Falsch-Grün), siehe
  [[room-migration-destructive-pitfall]].
- **E2E:** gegen lokale Test-Komga (Admin-Key) — Collection (SERIES) anlegen, Werk rein,
  am Server via `GET /api/v1/collections` verifizieren; dann BOOK/Read-List analog.

## Nicht-Ziele (YAGNI)

- Kein Collection-**Typ**/ContentType (bewusst verworfen — Konflikt + Invariante #4).
- Kein Smart-/Auto-Sync von externen Anbietern (Kavita-Plus-Stil).
- Keine Änderung am bestehenden Shelf/„Gruppen"-Konzept.
- Kavita-Impl noch nicht (aber Interface bleibt quellen-agnostisch dafür offen).

## Bezug

Setzt `architecture-seams.md` (Naht A) + `source-agnostic-integration.md` (Integrationsseite)
voraus, erweitert sie um den **Schreib**-Pfad. Folgt `source-extensibility.md` (Kochrezept A:
Interface erweitern, jede Quelle füllt was sie kann). Gehört zu [[project-komga-eink-reader]].
