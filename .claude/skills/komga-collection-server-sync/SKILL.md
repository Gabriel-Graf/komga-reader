---
name: komga-collection-server-sync
description: Use when touching how collections (or any per-source data) sync between the Komga-Reader app and a server — CollectionSyncManager, planCollectionSync, pullOnlySync/fullSync, server connect/disconnect triggers, removeSource, the vanished-modal, member titles, or LWW conflict handling. Also when "unifying"/refactoring the sync entry points or making server removal "cleaner".
---

# Komga Collections / Server-Sync — verbindliche Invarianten

Hält die Sync-Regeln fest, damit sie nicht versehentlich (oft als „DRY-Aufräumen" oder „sauberer
Schnitt" getarnt) gebrochen werden. Gehört zu [[project-komga-eink-reader]]; setzt
`architecture-seams.md` (Naht A) + `source-agnostic-integration.md` voraus.

## SyncCoordinator (Ist, 2026-06-11) — die zentrale Sync-/Discovery-Naht

`app/data/SyncCoordinator.kt` (@Singleton) bündelt **alle** Sync- und Discovery-Trigger.
Call-Sites melden nur das Ereignis; der Koordinator entscheidet die Aktion. Das Gating
(`aggressiveSyncAllowed`, jetzt in `app/data/SyncGating.kt`) liegt **im Koordinator** —
kein ViewModel muss es selbst prüfen.

| Vertrag | Aktion | Aufgerufen aus |
|---|---|---|
| `onAppStart()` | latch-geschützt (einmal/Launch): `fullSync` + lokaler Plugin-Scan + 1× Repo-Fetch | `MainActivity` (`LaunchedEffect`) |
| `onServerChanged()` | `pullOnlySync()` | `SettingsViewModel.saveServer`, `PluginsViewModel.addPluginSource` |
| `onManualReload()` | Repo-Fetch + lokaler Scan | PluginsViewModel „⟳ Reload" |
| `onCollectionsTabEntered()` | `fullSync()` nur wenn `aggressiveSyncAllowed` | `CollectionsViewModel.syncOnTabOpen` |

`CollectionSyncManager` bleibt der **Executor unter dem Koordinator** (unchanged): er kennt
die Sync-Richtungen (`pullOnlySync`/`fullSync`) und `planCollectionSync`. Früher verstreute
Einzel-Trigger (`syncOnceOnEnter`/`syncOnTabOpen` direkt in VMs, Server-Connect→direkter
`pullOnlySync`, Plugin-Add→direkter `pullOnlySync`) rufen jetzt den Koordinator. Die
Richtungs-Invarianten unten bleiben unverändert gültig.

## Die fünf Invarianten

### 1. Drei Sync-Events — NICHT eins. Sie sind keine Duplikation.
Jedes Event erlaubt eine **andere Richtung**. Das ist Absicht, kein Versehen:

| Event | Methode | Erlaubt |
|---|---|---|
| **Connect** (Server hinzufügen/bearbeiten, `SettingsViewModel.saveServer`) | `SyncCoordinator.onServerChanged()` → `CollectionSyncManager.pullOnlySync()` (`allowPush=false`) | **nur Pull** (Discovery + Server-Overwrite). **Nie Push, kein vanished.** |
| **Disconnect** (Server entfernen, `removeServer`) | `CollectionRepository.removeSource(sourceId)` | **nur lokales Löschen.** Nie Push, nie Pull, **nie** Server-seitig löschen. |
| **Steady-state** (App-Start-Tab, „Sync"-Button, LCD-Tab-Open) | `SyncCoordinator.onAppStart()`/`onCollectionsTabEntered()` → `fullSync()` (`allowPush=true`) | bidirektional: push + pull + Discovery + vanished. |

**Connect = nur Pull**, weil sonst lokale Sammlungen auf einen frisch verbundenen Server gedrückt/
dupliziert würden. Die `allowPush=false`-Trennung ist eine **explizite Garantie** — nicht „zufällig
sicher, weil `pushLocal` bei neuer Quelle leer ist".

### 2. Disconnect ≠ Server-Löschung. Vanish ≠ Disconnect.
- **Server entfernen** räumt nur **lokal** auf (`removeSource`): Member + Sync-Links der `sourceId`
  weg, dadurch leere + von der Quelle berührte Sammlungen ganz weg; multi-source-Sammlungen behalten
  die anderen Quellen. **Nichts geht an den Server.** Die Server-Sammlungen gehören dem Server/anderen
  Geräten.
- **Server-seitig löschen** (`deleteEverywhere` → `source.deleteCollection`) ist **ausschließlich** die
  explizite, nutzer-bestätigte Aktion „Sammlung inkl. Server löschen" (`delete(serverToo=true)`). Nie
  an Server-Entfernen koppeln.
- **Vanished** = eine **noch verbundene** Quelle hat eine früher synchrone Sammlung server-seitig
  verloren → `fullSync` gibt sie zurück → `EinkModal` mit Nutzer-Bestätigung („Hier behalten / Hier
  auch löschen"). Das ist ein anderer Pfad als Server-Entfernen (still).

### 3. LWW pro (Sammlung, Quelle)-Link, in UTC.
`planCollectionSync` vergleicht `remote.updatedAt` (Server `lastModifiedDate`, UTC-Epoch-Millis) gegen
`link.updatedAt` (lokaler Sync-Link): Server neuer → `pullOverwrite`, sonst → `pushLocal` (Gleichstand
→ lokal). Nach **jedem** Pull/Discovery wird `link.updatedAt` auf den Server-Zeitstempel re-ankert
(`writeLink(updatedAt = serverUpdatedAt)`). Whole-Subset-LWW (kein 3-Wege-Merge — die Quelle hat
Replace-Semantik, kein Basis-Snapshot). UTC-Parsing: `parseIsoUtcMillis` (no-offset = UTC), nie
`LocalDateTime.parse`.

### 4. Mitglieds-Identität = `(sourceId, remoteId)`. Keine interne ID.
`CollectionMember` trägt `sourceId` + `remoteId` (+ `title` nur zur Anzeige). Dieses Paar **ist** der
vollständige Link: Cover (`SourceCover(sourceId, remoteId)`), Öffnen (`series/{remoteId}/{sourceId}`)
und Cleanup (`removeSource` keyed auf `sourceId`) laufen alle darüber. **Kein internes DB-Id-Feld** im
Domain-Modell — das koppelte Navigation/Cover an lokalen DB-State und bräche die agnostische Naht.

### 5. Titel über die Quelle auflösen + Altbestand heilen.
Komgas `listCollections` liefert nur Member-**IDs**, keine Titel. Beim Sync den echten Titel über
`BrowsableSource.seriesDetail(remoteId).title` auflösen (Discovery + neue Pull-Member). **Heilung:**
bei jedem Sync für Member mit `title == remoteId` (Altbestand vor dem Fix) den echten Titel nachladen
und über **`updateMemberTitles`** schreiben — **niemals** über `setMembers` (das nullt
`remoteCollectionId` + setzt DIRTY → korrumpiert den Sync-Link).

## Rationalisierungs-Tabelle (sofort ablehnen)

| Ausrede | Realität |
|---|---|
| „Zwei Sync-Einstiege (`pullOnlySync`/`fullSync`) sind verwirrende Duplikation — vereinheitlichen." | Sie kodieren **verschiedene erlaubte Richtungen**. Connect darf nicht pushen. Vereinheitlichen bricht die Garantie. |
| „`fullSync` ist bei einer neuen Quelle eh pull-only, weil `pushLocal` leer ist." | Nicht garantiert (Namens-Kollision/Edge-Cases können pushen) und entfernt die **explizite** `allowPush=false`-Schranke — ein künftiger Change pusht dann beim Connect. Garantie muss explizit sein. |
| „Sauberer Schnitt: Server entfernen soll auch die Server-Sammlungen löschen, damit nichts dangling bleibt." | **Destruktiv.** Die Server-Sammlungen gehören dem Server/anderen Geräten. Entfernen = nur lokales Cleanup. Server-Löschen ist die separate, nutzer-bestätigte Aktion. |
| „Titel ist nur die ID — interne ID speichern und matchen." | `(sourceId, remoteId)` ist schon der Link. Titel über `seriesDetail` auflösen, kein zweites ID-Feld. |
| „Titel-Heilung über `setMembers`." | `setMembers` nullt `remoteCollectionId` + dirtied den Link. Titel-only über `updateMemberTitles`. |
| „Vanished-Modal beim Server-Entfernen anzeigen." | Server-Entfernen ist still (lokal). Modal ist nur für server-seitig **verschwundene** Sammlungen einer **verbundenen** Quelle. |

## Red Flags — STOP

- `fullSync()` / irgendein Push aus `saveServer`/dem Connect-Pfad.
- `source.deleteCollection`/`deleteEverywhere` an Server-Entfernen gekoppelt.
- Die `allowPush=false`/`pullOnlySync`-Unterscheidung „aufgeräumt"/entfernt.
- Internes ID-Feld an `CollectionMember`.
- Member-Titel über `setMembers` statt `updateMemberTitles` aktualisiert.
- „Server entfernt" und „Sammlung am Server verschwunden" als denselben Pfad behandelt.

Jeder Punkt = eine Invariante verletzt. Den Buchstaben zu verletzen ist, den Geist zu verletzen.

## Bezug
`architecture-seams.md` (Naht A: bidirektionaler Collections-Sync, `RemoteCollection.updatedAt` in
`domain/model`), `source-agnostic-integration.md` (Quellen-Auflösung über `ActiveSource`),
`eink-design-language.md` (Vanish-`EinkModal`). Domain-Geschwister: [[komga-viewer-type-resolution]],
[[komga-guided-comic-reader]], [[komga-eink-color-filter]].
</content>
