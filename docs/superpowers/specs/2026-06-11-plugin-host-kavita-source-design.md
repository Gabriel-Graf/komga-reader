# Plugin-Fundament + Kavita-Quellen-Plugin — Design

> Datum: 2026-06-11 · Phase 4 (Runtime-Plugin-Loader) · erstes Subsystem
> Status: Design genehmigt, Implementierung folgt.

## Zweck & Abgrenzung

Phase 4 (`roadmap-and-invariants.md`) ist der Runtime-Plugin-Loader. Statt alle vier
gewünschten Plugin-Arten (modulare UI, KI-Guided-Reader, neuer Server, Settings-per-Plugin)
auf einmal zu bauen, baut dieses Subsystem **das Fundament + den ersten echten Konsumenten**:

1. **Plugin-Loader** (`plugin-host`) + **ABI-Vertrag** (`plugin-api` erweitert) — entdeckt,
   prüft und lädt Plugin-APKs zur Laufzeit.
2. **Kavita-Quellen-Plugin** als erstes separates APK — Plugin-Typ (a) Quellen, der in
   `big-picture-and-goals.md` als „der designte Pfad" festgelegte erste Konsument.

Begründung der Wahl (Kavita zuerst, nicht Color-Presets): Quellen-Plugins üben die **größte
ABI-Fläche** (`BrowsableSource` + `SyncingSource` + Config-Schema + Identität/`SourceId`) und
decken Architekturlücken am schnellsten auf. `source-agnostic-integration.md` ist bereits
vollständig verdrahtet (Voraussetzung), und Kavita ist neben Komga der in der Community
beliebteste selbstgehostete Manga-/Comic-/Buch-Server mit REST-API.

**Bewusst NICHT in diesem Subsystem** (eigene spätere Spec→Plan→Bau-Runden):
- Modulare-UI-Pack-Lader (Typ b) — riskantester, zuletzt.
- KI-Guided-Reader (Sonderfall b / Reader-Engine + externer API-Call).
- Vollständiges Settings-per-Plugin (Typ c erweitert) — das **generische Config-Schema** hier
  ist dessen Keim, der Rest folgt.

## Festgelegte Entscheidungen (aus dem Brainstorming)

| Frage | Entscheidung |
|---|---|
| Erstes Subsystem | Loader + Kavita-Quelle (a) — größte ABI-Fläche, findet Lücken |
| Repo-Struktur | `plugin/` (gitignored) im Host-Repo; **jedes Plugin eigenes Git-Repo + eigenes Gradle-Projekt**. Host stellt `plugin-api` als lokales Maven-Artefakt bereit. |
| ABI-Grenze | `plugin-api` **re-exportiert die Naht-Typen** (`api(project(":source-api"))`); Plugin linkt nur `plugin-api` (compileOnly). Wir committen uns, die ABI-relevanten `domain`-Modelle stabil zu halten. |
| Verifikation | **Docker-Kavita** (analog `local-test-komga`), geseedet mit Inhalt, den die Test-Komga *nicht* hat → beweist Multi-Source mit verschiedenem Content. |
| Config-Beschaffung | **Generisches Config-Schema jetzt** (Plugin deklariert Felder, Host rendert Form) — dient direkt Ziel #4 (Settings-per-Plugin). |
| Speicherung Plugin-Config | `ServerConfig.extras: Map<String,String>` — ein Plugin-Server ist ein normaler Server-Listen-Eintrag (max. Wiederverwendung von Add/Remove/CollectionSync). |

Loader-Mechanik übernimmt die bereits in `big-picture-and-goals.md` festgelegten Plugin-Plan-
Entscheidungen 1–5 (eigenes `plugin-host`-Modul, ABI-Gate als 2 Ints, separates APK via
`PackageManager`-Entdeckung + `createPackageContext`-Classloader, `packageName`-Identität,
`SourceId`-Namespace). Diese werden hier nicht neu hergeleitet.

## Ist-Stand (verifiziert, keine Phantome)

- `plugin-api` (pure JVM) enthält heute **nur** `PluginAbi` (VERSION=1, MIN_SUPPORTED=1) und
  `ColorPresetSpec`. Hängt an `:domain`. Kein Loader, kein `SourcePlugin`.
- `SourceKind.PLUGIN` existiert bereits (`domain/model/SourceKind.kt`).
- Naht A vollständig: `BrowsableSource`/`SyncingSource` in `source-api` (pkg
  `com.komgareader.domain.source`), geben `domain.model`-Typen zurück. `SourceManager.register`,
  `SourceId.of(name, kind, config)`, `StubSource` vorhanden.
- Wiring-Punkt: `SourceRegistration.build(config)` in `app/data` mapt `ServerConfig.kind` →
  konkrete Quelle (heute Komga/OPDS). Hier hängt sich der PLUGIN-Zweig ein.
- `ServerConfig` (domain) ist heute starr: name, baseUrl, apiKey?, username?, password?, kind, id.
- In-App-Plugin-Muster existiert für Color-Presets: `ColorPresetImporter` (`data`) mit ABI-Gate
  per `toProfileOrNull`. Dasselbe Gate-Muster nutzt der Loader.

## Architektur

```
app (Settings: Server-Liste, generische Config-Form) ─┐
                                                      ▼
  plugin-host (Loader, Android-Lib)
      │  discover (PackageManager + Metadata-Flag)
      │  abi-check (PluginAbi.MIN_SUPPORTED..VERSION)
      │  load (createPackageContext(...).classLoader, Parent=Host)
      │  instantiate Entry-Class → SourcePlugin
      ▼
  SourceRegistration.build(): SourceKind.PLUGIN → pluginHost.sourceFor(config)
      ▼
  SourceManager.register(BrowsableSource)   ◀── ab hier alles agnostisch (bestehende Naht)

  Kavita-APK (plugin/komga-kavita-source/, eigenes Repo)
      implementiert SourcePlugin + BrowsableSource (+ SyncingSource)
      linkt plugin-api compileOnly · eigenes Retrofit/OkHttp/serialization
```

### Modul-Schnitt

| Modul | Änderung | Hängt an | Darf NICHT an |
|---|---|---|---|
| `plugin-api` | + `SourcePlugin`, `PluginMetadata`, `ConfigSchema`/`ConfigField`; `api(project(":source-api"))` | `domain`, `source-api` | Android, Netz, UI |
| `plugin-host` (**neu**, Android-Lib) | Loader, ABI-Gate, Classloader, Instanziierung | `plugin-api`, `source-api`, Android-`Context` | UI, konkrete Quellen |
| `domain` | `ServerConfig.extras: Map<String,String>` | — | unverändert sonst |
| `data` | Room: `extras` als JSON-Spalte + Recreate-Table-Migration | `domain` | — |
| `app` | generische Config-Form aus `ConfigSchema`; `SourceRegistration` PLUGIN-Zweig; DI für `plugin-host` | alles (Shell) | konkrete Plugin-Typen außerhalb Wiring |

## ABI-Vertrag (`plugin-api` erweitert)

```kotlin
// Entry-Point, den ein Quellen-Plugin-APK implementiert.
interface SourcePlugin {
    val metadata: PluginMetadata
    fun configSchema(): ConfigSchema
    // Erzeugt die laufende Quelle aus den vom Nutzer eingegebenen Config-Werten.
    // Rückgabe MUSS BrowsableSource sein; SyncingSource optional zusätzlich implementiert.
    fun create(config: Map<String, String>): BrowsableSource
}

data class PluginMetadata(
    val displayName: String,       // angezeigter Quellen-Name (Default vor Nutzer-Override)
    val kind: SourceKind = SourceKind.PLUGIN,
)

data class ConfigSchema(val fields: List<ConfigField>)

data class ConfigField(
    val key: String,               // Speicher-Schlüssel in ServerConfig.extras
    val label: String,             // angezeigtes Label (Host lokalisiert nicht — Plugin liefert)
    val type: FieldType,
    val required: Boolean = true,
    val default: String = "",
)

enum class FieldType { TEXT, SECRET, URL, BOOL }
```

- `plugin-api` macht `api(project(":source-api"))` → `BrowsableSource`/`SyncingSource` +
  ABI-relevante `domain`-Modelle (`Series`, `Book`, `PageRef`, `ReadProgress`, `SourceKind`)
  sind für das Plugin sichtbar, ohne dass das Plugin `source-api`/`domain` direkt linkt.
- **Stabilitäts-Commitment:** Die durch `BrowsableSource`/`SyncingSource` erreichbaren
  `domain`-Modelle sind ab jetzt ABI-Oberfläche. Änderungen daran = potenzieller ABI-Bruch →
  nur additiv mit Defaults, sonst `PluginAbi.VERSION` bumpen.
- **Classloader-Regel:** Plugin linkt `plugin-api` **compileOnly**; die Vertragsklassen kommen
  zur Laufzeit vom Host-Classloader (Parent). Plugin-APK packt sie NICHT ein → kein
  `ClassCastException` durch doppelte Interface-Klassen.

### ABI-Gate

`PluginAbi.VERSION`/`MIN_SUPPORTED` (existiert) bleibt das Gate. Plugin-Manifest deklariert
seine `abiVersion` als Metadata. Außerhalb `MIN_SUPPORTED..VERSION` → „inkompatibel", nie
instanziiert (gleiches Muster wie `ColorPresetImporter.toProfileOrNull`).

## Loader-Flow (`plugin-host`)

1. **Entdeckung:** `PackageManager.getInstalledPackages(GET_META_DATA)` → APKs mit
   Metadata-Schlüssel `com.komgareader.plugin.SOURCE` (Wert = vollqualifizierter Entry-Class-Name)
   und `com.komgareader.plugin.ABI_VERSION` (Int).
2. **ABI-Check:** `abiVersion ∈ MIN_SUPPORTED..VERSION`, sonst überspringen + loggen.
3. **Laden:** `context.createPackageContext(pkg, CONTEXT_INCLUDE_CODE or CONTEXT_IGNORE_SECURITY)
   .classLoader` → `loadClass(entry)` → `newInstance()` → cast auf `SourcePlugin`. Parent =
   Host-Classloader (implizit über `createPackageContext`). Kein `DexClassLoader`.
4. **Identität:** `SourceId.of(metadata.displayName, SourceKind.PLUGIN, "$packageName/$configHash")`
   mit `configHash` = stabiler Hash der Config-Werte. Jeder DB-Satz trägt diese `sourceId` →
   Uninstall fällt sauber auf `StubSource`, kein Schema-Change.
5. **Instanziierung pro Quelle:** `sourceFor(config)` ruft `plugin.create(config.extras)` →
   `BrowsableSource` (ggf. zusätzlich `SyncingSource`).

`plugin-host` exponiert:
- `discoverPlugins(): List<DiscoveredPlugin>` (packageName, metadata, configSchema) für die
  Settings-„Server hinzufügen"-Liste.
- `sourceFor(config: ServerConfig): BrowsableSource?` für `SourceRegistration`.

## Config & Speicherung

- `ServerConfig` bekommt `extras: Map<String,String> = emptyMap()`.
- Room: zwei **nullable** Spalten `extrasCiphertext`/`extrasIv` (Keystore-verschlüsselter
  JSON-Blob). Migration via `ALTER TABLE ADD COLUMN` — korrekt + nicht-destruktiv für nullable
  Spalten ohne DEFAULT (exakt das Muster der bestehenden `MIGRATION_4_5`). Die Memory-Falle
  `room-migration-destructive-pitfall` betrifft nur **NON-NULL + DEFAULT** ohne passendes
  `@ColumnInfo` — trifft hier nicht zu, daher KEIN Recreate-Table nötig. Migrations-Test gegen
  echte Upgrade-DB (nicht inMemory).
- **„Server hinzufügen" generisch:** Wählt der Nutzer ein entdecktes Plugin, rendert der Host die
  Form aus `plugin.configSchema()` (Feldtyp → Compose-Control: TEXT/URL→Textfeld, SECRET→
  maskiert, BOOL→Toggle). Eingaben → `ServerConfig.extras`. **Kein Kavita-Spezialcode in `app`.**
  E-Ink-Designsprache + `i18n` (Rahmen/Buttons; Feld-Labels liefert das Plugin).
- **Wiring:** `SourceRegistration.build()` neuer Zweig
  `SourceKind.PLUGIN -> pluginHost.sourceFor(config)`. Konkreter Plugin-Typ bleibt in der
  Wiring-Schicht — `source-agnostic-integration.md` (kein quellen-spezifischer Typ im VM) gewahrt.

## Kavita-Plugin (`plugin/komga-kavita-source/`, eigenes Repo)

- Android-APK, eigenes Gradle-Projekt + Git. Linkt `plugin-api` **compileOnly** (aus lokalem
  Maven). Eigene Deps: Retrofit/OkHttp + kotlinx.serialization (gepackt ins APK).
- Implementiert `SourcePlugin` + `BrowsableSource` + `SyncingSource` (Kavita kann Progress).
- **Auth:** `configSchema` = { `url` (URL, required), `apiKey` (SECRET, required) }. Flow:
  `x-api-key` → `POST /api/Plugin/authenticate` → JWT → `Authorization: Bearer <token>` für
  Folge-Requests (verifiziert, Quelle unten). Token-Refresh bei Ablauf.
- **Mapper** Kavita-DTO→`domain` im Plugin-Modul (Series/Volume/Chapter→`Series`/`Book`,
  Seiten→`PageRef`, Reading-Progress→`ReadProgress`). Exakte Endpunkte beim Bau gegen die
  Kavita-OpenAPI pinnen: `raw.githubusercontent.com/Kareadita/Kavita/develop/openapi.json`.
- **Manifest:** Metadata `com.komgareader.plugin.SOURCE` = Entry-Class,
  `com.komgareader.plugin.ABI_VERSION` = 1.

## Tests

- **Plugin (im Plugin-Repo):** MockWebServer-Vertragstests gegen gemockte Kavita-Responses
  (Muster `source-komga`) — Auth-Flow, browse/search/books/pages/openPage/seriesDetail,
  Progress push/pull. Gesetzt **und** leer/null.
- **Host (`plugin-host`):** Unit-Tests fürs ABI-Gate (kompatibel/zu alt/zu neu), Discovery-
  Parsing der Metadata, `configHash`/`SourceId`-Determinismus. Form-Mapping `ConfigField`→Control
  pur testbar.
- **`data`:** Room-Migrations-Test (echte Upgrade-DB, `extras` erhalten/befüllbar).
- **E2E (Emulator):** Docker-Kavita, geseedet mit Inhalt, den die Test-Komga **nicht** hat.
  Erweitert `MixedSourcesLiveTest`: Komga + Kavita-Plugin gleichzeitig registriert,
  `ActiveSource.all()` liefert beide, Kavita-Werk über die Naht gelesen. Beweist gemischte
  Quellen mit unterscheidbarem Content.

## Dev-Workflow

- `plugin-api` → `publishToMavenLocal` (Maven-Group/Artifact/Version festlegen). Plugin-Repo
  konsumiert es als `compileOnly`-Artefakt aus `mavenLocal()`.
- Schleife: Plugin bauen → APK auf Emulator/Boox installieren → Host `discoverPlugins()` →
  in Settings hinzufügen → registriert.
- `plugin/` bleibt gitignored; Plugin-Repos sind unabhängig versioniert.

## Baureihenfolge

1. `plugin-api` erweitern (`SourcePlugin`/`ConfigSchema`/`PluginMetadata`, `api(source-api)`) +
   `publishToMavenLocal`.
2. `plugin-host` Loader + ABI-Gate (TDD: Gate/Discovery/Identität pur).
3. `ServerConfig.extras` + Room-Recreate-Migration + Migrations-Test.
4. Generische Config-Form in `app` (aus `ConfigSchema`).
5. `SourceRegistration` PLUGIN-Zweig + DI-Verdrahtung `plugin-host`.
6. Kavita-APK in eigenem Repo (SourcePlugin+BrowsableSource+SyncingSource, Mapper, MockWebServer).
7. Docker-Kavita seeden + E2E mixed-source; Memory dokumentieren.

## Risiken & offene Punkte

- **Classloader-Fallen:** Plugin darf `plugin-api`/`source-api`/`domain`-Klassen NICHT mit ins
  APK packen (`compileOnly`) — sonst doppelte Klassen → `ClassCastException`. Build-Verifikation
  nötig (APK-Inhalt prüfen).
- **`domain` als ABI:** Re-Export bindet `domain`-Modelle an die ABI. Disziplin (nur additiv)
  oder späterer DTO-Schnitt, falls `domain` zu volatil wird. Dokumentiert als Commitment oben.
- **Kavita-Auth-Details/Token-Ablauf:** exakte Endpunkte + Refresh gegen Live-API verifizieren.
- **`CONTEXT_IGNORE_SECURITY`:** nötig für `createPackageContext` fremder Pakete; Signatur/
  Integrität macht das OS beim Install (kein Download-`.dex`). In der Spec als bewusst notiert.

## Quellen

- Kavita-Auth (`x-api-key` → `/api/Plugin/authenticate` → JWT):
  [Kavita Wiki — API](https://wiki.kavitareader.com/guides/api/),
  [DeepWiki — Authentication](https://deepwiki.com/Kareadita/Kavita/3.2-authentication-and-authorization)
- Kavita OpenAPI: `https://raw.githubusercontent.com/Kareadita/Kavita/develop/openapi.json`

## Bezug

Setzt `architecture-seams.md` (Naht A) + `source-agnostic-integration.md` (Integration verdrahtet)
voraus. Umsetzung von `big-picture-and-goals.md` → Plugins (a) + Plugin-Plan-Entscheidungen 1–5.
Memory: `room-migration-destructive-pitfall`, `local-test-komga`, `architecture-source-agnostic-debt`.
