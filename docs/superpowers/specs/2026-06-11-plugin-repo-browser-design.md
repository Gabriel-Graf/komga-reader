# Plugin-Repo-Browser + APK-Download/Install (Slice P2) — Design

> Datum: 2026-06-11 · Phase 4, Subsystem 2 (Slice **P2**) · Branch (folgt): `feat/plugin-repo-browser`
> Status: Design genehmigt (Brainstorming), Implementierung folgt (Plan → Bau).
> Voraussetzung: Slice P1 (Plugin-Tab + data-only Color-Preset-Plugin) ist auf `master` gemergt.

## Zweck & Abgrenzung

P1 hat den **Plugins-Tab** für *installierte* Plugins gebaut (Quellen + data-only Presets, ⚙/🗑) und
das Screen-`+` als Platzhalter („Repos folgen") gelassen. **P2 macht das `+` echt:** Plugins
**finden → herunterladen → installieren**, mit Update-Erkennung.

**Slice P2 (diese Spec) deckt ab:**
1. **Repo-Verwaltung:** ein **offizielles Default-Repo** (Konstante, abschaltbar) + vom Nutzer
   hinzugefügte Repo-URLs (hinzufügen/entfernen).
2. **Browse + Suche:** je Repo wird ein JSON-Index (`repo.json`) geladen, alle Einträge gemerged,
   durchsuchbar gelistet (Name/Beschreibung), mit Typ-Label (Quelle/Preset) + Versions-/ABI-Info.
3. **Installieren:** APK herunterladen (HTTPS) → **Signatur-Fingerprint gegen den Index-Eintrag
   verifizieren** → `PackageInstaller`-Session → OS-Installationsdialog.
4. **Update-Erkennung:** je Eintrag `installState` (`NOT_INSTALLED` / `INSTALLED` / `UPDATE_AVAILABLE`)
   aus Index-`versionCode` vs. installiertem Paket; Update = Re-Install über denselben Pfad.

**Bewusst NICHT in P2 (späterer Slice / YAGNI):** periodischer/Start-Auto-Refresh des Index + Badge am
Tab; Plugin-Icons/Screenshots im Index; Bewertungen; Signierung des Index selbst (nur die einzelnen
APK-Fingerprints werden verifiziert); In-App-Update ohne OS-Dialog (technisch nicht erlaubt).

## Festgelegte Entscheidungen (Brainstorming)

| Frage | Entscheidung |
|---|---|
| Repo-Modell | **Ein offizielles Default-Repo + optional eigene URLs.** Offizielles Repo = Code-Konstante mit Enable-Toggle (nicht als DB-Zeile geseedet → robust gegen URL-Tausch); eigene Repos in Room-Tabelle. |
| Install-Trust | **Index-Fingerprint + Verify vor dem Install.** Index nennt pro Plugin den Cert-SHA-256; App lädt APK, liest dessen Signaturzertifikat (`getPackageArchiveInfo`), vergleicht (Doppelpunkt-/Case-normalisiert) gegen den Index-Wert; nur bei Match → `PackageInstaller`. Mismatch → Abbruch, Datei löschen, nie installieren. Bestehender TOFU-Pin bei Quellen-Plugins (P1) bleibt zusätzlich. |
| Umfang | **Browse + Install + Update-Erkennung.** Kein Auto-Refresh/Badge (späterer Slice). |
| Install-Mechanik | `PackageInstaller`-Session + `REQUEST_INSTALL_PACKAGES`; das OS zeigt den Installationsdialog (kein stiller Install möglich/erwünscht). |
| UI-Ort | Screen-`+` im Plugins-Tab → **gepushte Route** `RepoBrowserScreen` (Repo-Verwaltung + Suche + Liste). Kein Modal (zu eng). |
| Offizielle Repo-URL | Wird vom Nutzer am Ende geliefert (eigenes GitHub-Repo mit den ersten Komga-Reader-Plugins). Bis dahin **Platzhalter-Konstante** `PluginRepoDefaults.OFFICIAL_URL`; E2E gegen ein **lokales Test-Repo**. |

## Ist-Stand (verifiziert — keine Phantome)

- **P1 (master):** `PluginsScreen`/`PluginsViewModel` (Plugins-Tab), `planPluginPrune`, `PluginSourceFlow`
  (TOFU/Config-Modals). Das Screen-`+` ist heute nur der Text `pluginTabReposHint` — **kein** Repo-Code.
- **`plugin-host`:** `PluginSignature.sha256(certBytes): String` (lowercase-Hex, **ohne Trenner**) +
  `matches(pinned, actual)` (case-/whitespace-tolerant, **nicht** doppelpunkt-tolerant) — der P2-Verify
  normalisiert Doppelpunkte selbst. `AbiGate.isCompatible(abi)`, `PluginAbi.{VERSION,MIN_SUPPORTED}`.
  `PluginHost.discoverPlugins()`/`discoverColorPresetPlugins()` (Re-Scan zeigt Installiertes im Tab).
- **`data`:** hat `kotlinx.serialization.json` + coroutines, aber **kein OkHttp** → muss ergänzt werden
  (`okhttp` ist im Version-Catalog vorhanden, `mockwebserver` schon als androidTest). Room-DB-Version
  **16** → P2-Migration **16→17**. Settings-KV-Muster vorhanden (`SettingsRepository`).
- **App-Manifest:** hat `INTERNET` + `QUERY_ALL_PACKAGES`; **`REQUEST_INSTALL_PACKAGES` fehlt** → ergänzen.
- **Navigation:** Tab-/Route-Muster vorhanden (P1 push-Routen, z.B. Reader→Settings). Der Browser ist
  eine neue gepushte Route über dem Plugins-Tab.

## Architektur & Modulschnitt

```
Plugins-Tab  ──"+"──▶  RepoBrowserScreen (gepushte Route)
                         RepoBrowserViewModel
                           ├─ RepoStore (data)            → eigene Repo-URLs (Room: plugin_repos) + Offiziell-Toggle (Settings-KV)
                           ├─ PluginRepoClient (data)     → OkHttp: repo.json laden + APK in Cache downloaden
                           └─ PluginInstaller (app)       → APK-Cert lesen → Fingerprint-Verify → PackageInstaller-Session

Reine, unit-getestete Bausteine (in :data, JVM-Unit-Tests):
  • parseRepoIndex(json): RepoIndex?                          (kotlinx.serialization, null bei kaputt)
  • resolveApkUrl(repoUrl, entry.apkUrl): String             (absolut ODER relativ zur repo.json-Basis)
  • mergeRepoEntries(perRepo): List<BrowsableEntry>          (dedup nach packageName, höchste versionCode gewinnt, Repo-Herkunft behalten)
  • installState(entryVersionCode, installedVersionCode?): InstallState
  • normalizeFingerprint(s): String  +  fingerprintMatches(indexFp, apkCertSha)  (Doppelpunkt/Whitespace/Case)
```

| Modul | Änderung |
|---|---|
| `data` | + `RepoIndex`/`RepoPluginEntry`/`PluginKind`/`InstallState` (pure Modelle); + `parseRepoIndex`/`resolveApkUrl`/`mergeRepoEntries`/`installState`/`fingerprintMatches` (pure); + `PluginRepoClient` (OkHttp Fetch+Download, `implementation(libs.okhttp...)`); + `plugin_repos`-Tabelle + Room-**Migration 16→17** + `RepoStore` (eigene Repos CRUD); + `PluginRepoDefaults.OFFICIAL_URL` (Platzhalter-Konstante). |
| `domain` | **keine** Änderung (bleibt netz-/plugin-frei). |
| `app` | + `RepoBrowserScreen`/`RepoBrowserViewModel`; + `PluginInstaller` (PackageInstaller-Session + APK-Cert via `getPackageArchiveInfo` → `PluginSignature.sha256` → `fingerprintMatches`); `+`-Route in der Plugin-Tab-Navigation; i18n; Manifest `REQUEST_INSTALL_PACKAGES` + ggf. `FileProvider`/Cache-Pfad. DI für `PluginRepoClient`/`RepoStore`/`PluginInstaller`. |

> **Naht-Treue:** `domain` bleibt unberührt. Networking (`PluginRepoClient`) und Install
> (`PluginInstaller`) leben in der Daten-/App-Schicht; die puren Parse-/Versions-/Fingerprint-Funktionen
> sind frei von Android/Netz und einzeln testbar (functional core). Reuse von `PluginSignature` (plugin-host)
> für den APK-Cert-Digest — `app` hängt bereits an `plugin-host`.

## `repo.json`-Format (was ein Repo liefern muss)

```json
{
  "name": "Komga Reader Official",
  "plugins": [
    {
      "packageName": "com.komgareader.preset.kindle",
      "name": "Kindle E-Ink Presets",
      "description": "Gedämpfte E-Ink-Farbprofile",
      "type": "preset",                          // "source" | "preset" (nur Label/Filter)
      "abiVersion": 1,
      "versionCode": 1,
      "versionName": "1.0",
      "apkUrl": "plugins/kindle-presets-1.0.apk", // absolut ODER relativ zur repo.json-Basis
      "fingerprint": "3a:7b:..."                  // Cert-SHA-256, Doppelpunkte/Case egal
    }
  ]
}
```

- Unbekannte Felder werden ignoriert (`ignoreUnknownKeys = true`) → vorwärtskompatibel.
- `description` optional (Default leer). `type` unbekannt → als „source" behandeln (konservativ).
- Ein Eintrag ohne `packageName`/`apkUrl`/`fingerprint`/`versionCode` ist ungültig → übersprungen
  (gültige Einträge bleiben), analog `parsePresetSpecs` aus P1.

## Datenfluss — Install (eine Zeile → Button)

1. **Laden:** je aktivem Repo (offiziell falls Toggle an + alle `plugin_repos`) `PluginRepoClient.fetchIndex(url)`
   → `parseRepoIndex`. Fehlende/kaputte Repos werden übersprungen (Fehler je Repo angezeigt, blockiert
   die anderen nicht).
2. **Mergen + Zustand:** `mergeRepoEntries` dedupt nach `packageName` (höchste `versionCode`); je Eintrag
   `installState(entry.versionCode, pm.getPackageInfo(pkg)?.longVersionCode)`. ABI außerhalb der Spanne
   → Eintrag als **„inkompatibel"** markiert (kein Button).
3. **Tap Installieren/Update:** `PluginRepoClient.download(resolveApkUrl(repoUrl, apkUrl))` → Datei im
   App-Cache (`cacheDir/plugin-repo/<pkg>-<versionCode>.apk`).
4. **Verify:** `PluginInstaller` liest das Signaturzertifikat des geladenen APK
   (`packageManager.getPackageArchiveInfo(path, GET_SIGNING_CERTIFICATES)` → `apkContentsSigners.first()`
   → `PluginSignature.sha256(cert.toByteArray())`) und vergleicht via `fingerprintMatches` gegen
   `entry.fingerprint`. **Mismatch → Abbruch, Datei löschen, Fehler anzeigen, NICHT installieren.**
5. **Install:** Match → `PackageInstaller`-Session (MODE_FULL_INSTALL): APK-Bytes schreiben → `commit`
   mit `IntentSender` (Status-Receiver). Das OS zeigt seinen Installationsdialog; der Nutzer bestätigt.
6. **Nachlauf:** kein verlässliches In-Process-Ergebnis nötig — der bestehende Plugin-Tab entdeckt das
   neue Plugin beim `onResume`-Re-Scan; der Browser ruft beim Wieder-Sichtwerden `refresh()` und
   aktualisiert `installState` (genau wie P1 den Uninstall-Prune über `onResume` macht).

## Persistenz / Migration

- **`plugin_repos`** (eigene Repos): `id` (PK autoincrement), `url` TEXT NOT NULL (unique), `name` TEXT
  (optional, aus dem Index gefüllt). **Migration 16→17:** reines `CREATE TABLE` (keine Bestandsdaten,
  kein `ALTER`) — exakt das vom Entity erzeugte Schema, damit Rooms Validierung sauber läuft und
  `fallbackToDestructiveMigration` NICHT greift. `@Database(version=17)` bumpen, in
  `DataModule.addMigrations(...)` registrieren.
- **Offizielles Repo:** **nicht** in der Tabelle — Code-Konstante `PluginRepoDefaults.OFFICIAL_URL` +
  Settings-KV `official_repo_enabled` (Default `true`). Der Browser merged „offiziell (falls an) + alle
  `plugin_repos`". So bleibt der spätere URL-Tausch ein Ein-Zeilen-Edit ohne Migrations-/Seed-Falle.
- Migrations-androidTest: leere v16-DB → öffnen mit 16→17 → `plugin_repos` existiert, leer; Bestand unberührt.

## UI — `RepoBrowserScreen` (E-Ink-Designsprache)

Gepushte Route, eigener Scaffold-Titel („Plugins entdecken"). Flach, 1.5px-Border, Lucide via `AppIcons`,
keine Animation, `EinkModal` für Dialoge, alle Texte über `LocalStrings` (DE+EN).

- **Kopf:** Suchfeld (filtert die gemergte Liste nach Name/Beschreibung) + ein Aktions-Icon „Repos
  verwalten" (öffnet `EinkModal`: Offiziell-Toggle, Liste eigener Repos mit 🗑, „Repo hinzufügen"
  = URL-Eingabe). „Aktualisieren"-Aktion lädt die Indizes neu.
- **Liste:** je Eintrag eine Zeile (wie P1 `PluginRow`): Name + „Typ · v{versionName} · ABI {n} · {Repo-Name}",
  rechts ein Zustands-Button:
  - `NOT_INSTALLED` → **Installieren** (Download-Icon)
  - `UPDATE_AVAILABLE` → **Update** (Update-Icon)
  - `INSTALLED` → Label „Installiert" (kein Button; Verwaltung/Uninstall im Plugin-Tab)
  - inkompatibel → Label „Inkompatibel" (gedämpft, kein Button)
- **Lade-/Fehlerzustände:** `LoadingIndicator` (E-Ink: statischer Text) beim Index-Laden; je Repo, das
  fehlschlägt, ein dezenter Hinweis; Download-/Verify-Fehler als `EinkInfoDialog`.
- **Leerstaat:** „Keine Plugins gefunden" wenn alle Repos leer/aus.

## Fehlerbehandlung

| Fall | Verhalten |
|---|---|
| Repo-Index nicht erreichbar / kein JSON | Repo übersprungen, dezenter Fehlerhinweis; andere Repos laden weiter. |
| Eintrag unvollständig/ungültig | übersprungen (gültige bleiben). |
| ABI inkompatibel | Eintrag sichtbar, als „inkompatibel" markiert, nicht installierbar. |
| APK-Download fehlgeschlagen | Fehlerdialog, Cache-Datei aufräumen, kein Install. |
| **Fingerprint-Mismatch** | **Harter Abbruch**, Datei löschen, deutlicher Sicherheitsfehler, nie an PackageInstaller geben. |
| `REQUEST_INSTALL_PACKAGES` nicht gewährt | OS leitet zur Berechtigungs-Seite; nach Rückkehr erneut versuchbar. |
| Nutzer bricht OS-Dialog ab | kein Zustandswechsel; Eintrag bleibt `NOT_INSTALLED`/`UPDATE_AVAILABLE`. |

## Tests

- **`data` (Unit, JVM):** `parseRepoIndex` (gültig / leer / kaputt / unbekannte Felder / Eintrag fehlt
  Pflichtfeld), `resolveApkUrl` (absolut / relativ / mit/ohne Slash), `mergeRepoEntries` (dedup nach
  höchster versionCode, Repo-Herkunft), `installState` (alle drei Zustände, null=installiert-nicht),
  `fingerprintMatches`/`normalizeFingerprint` (Doppelpunkt/Case/Whitespace). `PluginRepoClient` gegen
  `MockWebServer` (Fetch-OK, 404, Timeout, Download-Bytes).
- **`data` (Migrations-androidTest):** v16→v17 legt `plugin_repos` an, Bestand unberührt.
- **`app` (Unit):** `PluginInstaller`-Fingerprint-Gate (pure-Teil) — Verify lehnt Mismatch ab.
- **E2E (Emulator):** lokaler HTTP-Server (`python -m http.server`) liefert `repo.json` + das
  **Kindle-Preset-APK** aus P1 (`plugin/komga-eink-preset-kindle`); `OFFICIAL_URL` → lokale URL.
  Browser zeigt den Eintrag → Installieren → Fingerprint-Verify → OS-Dialog bestätigen → Plugin-Tab
  zeigt es → Preset importierbar (P1-Pfad). Update-Fall: versionCode im Index erhöhen + neues APK →
  Zeile zeigt „Update". Negativ: falscher Fingerprint im Index → Install bricht ab.

## Baureihenfolge

1. `data`: pure Modelle + `parseRepoIndex`/`resolveApkUrl`/`mergeRepoEntries`/`installState`/`fingerprintMatches` (TDD).
2. `data`: `plugin_repos` + Migration 16→17 + `RepoStore` + `PluginRepoDefaults.OFFICIAL_URL` (+ Migrations-Test).
3. `data`: `PluginRepoClient` (OkHttp Fetch+Download) gegen MockWebServer (TDD); okhttp-Dep ergänzen.
4. `app`: `PluginInstaller` (APK-Cert lesen → `fingerprintMatches` → PackageInstaller); Manifest `REQUEST_INSTALL_PACKAGES`.
5. `app`: `RepoBrowserViewModel` (Indizes laden/mergen/Zustand, Install/Update anstoßen, Repo-Verwaltung).
6. `app`: `RepoBrowserScreen` + `+`-Route + i18n + Repos-Verwalten-Modal.
7. E2E gegen lokales Test-Repo (inkl. Update- und Fingerprint-Mismatch-Fall).

## Risiken

- **`PackageInstaller`-Session-Callback:** der Commit-`IntentSender`-Status ist asynchron + nicht immer
  zuverlässig über Prozess-Grenzen — daher Zustands-Aktualisierung über `onResume`-Re-Scan (wie P1), nicht
  über das Session-Ergebnis allein.
- **APK-Cert-Lesen vor Install:** `getPackageArchiveInfo(..., GET_SIGNING_CERTIFICATES)` muss die
  Signatur einer noch-nicht-installierten Datei liefern — auf dem Ziel-API (min 28) verifizieren.
- **Relative `apkUrl`-Auflösung:** sauber gegen die `repo.json`-Basis (GitHub-Raw-Pfade) — Unit-getestet.
- **`REQUEST_INSTALL_PACKAGES` + Play-Policy:** für Sideload-/F-Droid-Vertrieb ok; bei späterer
  Play-Distribution ist die Permission deklarationspflichtig (dokumentieren).

## Bezug

Setzt P1 (`plugin-host`, Plugin-Tab) + das Mihon-Trust-Modell (OS installiert, App pinnt Cert) voraus.
Domain-Regeln: `architecture-seams.md` (Naht A, domain bleibt netzfrei), `source-agnostic-integration.md`
(konkrete Typen nur in Wiring), `eink-design-language.md` + `animation-gating.md` (UI),
`room-migration-destructive-pitfall.md` (Migration 16→17). Memory: [[plugin-host-kavita]],
[[room-migration-destructive-pitfall]]. Schließt das Plugin-Typ-(a)/(c)-Vertriebsbild ab; UI-Plugins (b)
bleiben separater Pfad.
