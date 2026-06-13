# Font-Plugins P1 — Generische Plugin-Discover-UX (Info-Modal + Vorschau-Bild)

> Datum: 2026-06-13 · Status: Design freigegeben · Phase: P1 von 3 (Font-Plugin-Vorhaben)

## Einordnung: das 3-Spec-Vorhaben

Ziel des Gesamtvorhabens: **Schriftarten als nutzer-installierbare Plugins**, mit guter
Discover-UX und sauberer Lizenz-Hygiene. Zu groß für einen Plan → drei Specs, je eigener
Spec→Plan→Bau, in dieser Reihenfolge:

- **P1 (dieses Dokument) — Generische Discover-UX:** Info-Button → Modal mit gerendertem
  README + generisches Vorschau-Bild. Font-unabhängig, gilt sofort für **alle** Plugin-Typen
  (Quellen, Presets, Sprachen, UI-Packs, später Fonts). Niedrigstes Risiko, Fundament.
- **P2 — Font-Plugin-Subsystem:** `PluginCategory.FONT` (additiv), Discovery, TTF aus
  Fremd-APK-Assets, `nativeAddFont`-JNI (crengine live, ohne Neustart), **SPDX-Lizenz-Gate**
  (Allowlist, harter Block), Settings-Integration (Plugin-Fonts in `NovelFonts` mergen,
  Picker, Live-Sample im echten Font).
- **P3 — 5 Fonts + Specimen-Generator:** Build-Script TTF→Specimen-PNG, fünf data-only
  Font-APKs (OFL-1.1), `repo.json`-Einträge mit `license`+`previewUrl`+`readmeUrl`,
  Provenance/NOTICE je Schrift. (Schriften + Upstreams stehen im Memory `font-plugins-research`.)

Dieses Dokument spezifiziert **nur P1**.

## Motivation

Heute zeigt der Plugins-Tab pro entdecktem Repo-Eintrag nur Name + Typ + Version + ABI + Repo
(`RepoRow` in `app/ui/plugins/PluginsScreen.kt`). Ein Nutzer kann **vor** der Installation nicht
sehen, **was** ein Plugin ist oder — bei Fonts besonders wichtig — **wie eine Schrift aussieht**.

P1 schließt diese Lücke generisch: ein Info-Button öffnet ein Modal, das das README des Plugins
formatiert rendert (inkl. eingebetteter Bilder) und ein optionales Vorschau-Bild zeigt. Weil
das Vorschau-Bild als generisches Feld liegt, profitieren UI-Packs später ohne Zusatzarbeit
(„evtl. später auch bei UI-Packs Bilder" — User).

## Ziele

1. `RepoPluginEntry` trägt drei **optionale, vorwärtskompatible** Metadaten-Felder
   (`previewUrl`, `readmeUrl`, `license`) — generisch für alle Plugin-Typen.
2. Ein **ℹ-Info-Button** links neben dem Download/Install-Control in jeder **Discover-Zeile**.
3. Ein **Plugin-Info-Modal**, das zeigt: Kopf (Name/Typ/Version/SPDX-Lizenz), optionales
   Vorschau-Bild, gerendertes README (mit Remote-Bildern → ein im README eingebettetes
   Font-Specimen erscheint automatisch). Fallback auf das vorhandene `description`-Feld, wenn
   kein README vorhanden/ladbar.
4. README-Markdown rendern über eine **AGPL-kompatible, compose-native** Lib mit Remote-Bild-
   Support; E-Ink-Bewegungs-Invariante **host-erzwungen** (keine Lade-Animation).
5. Lizenz-/Dokumentations-Pflichten der neuen Lib erfüllt (`NOTICE` + README).

## Nicht-Ziele (bewusst ausgeklammert)

- Font-Kategorie, Font-Settings, Live-Font-Sample → **P2**.
- Lizenz-**Enforcement** (Allowlist, Install-**Block**) → **P2**. P1 **zeigt** die Lizenz nur an.
- Specimen-PNG-Generierung, die 5 Font-APKs, `repo.json`-Inhalte → **P3**.
- Info-Button für **installierte** Plugins: installierte kommen aus dem `PackageManager`
  (`DiscoveredPlugin`/`DiscoveredDataPlugin`), tragen keine README/Preview-URL → kein README
  verfügbar. Bleibt draußen (YAGNI); nur Discover-Zeilen (`RepoRow`).
- Eigene README-Bild-Caching-Strategie jenseits des bestehenden Coil-`ImageLoader`.

## Architektur

### Abhängigkeit: Markdown-Renderer

`com.mikepenz:multiplatform-markdown-renderer` **0.41.0** (stabil; **nicht** 0.42.0-b01 beta).
Recherche-verifiziert (LICENSE-Datei + Maven-POM geöffnet, Workflow 2026-06-13, Details im
Memory `font-plugins-research`):

- **Apache-2.0** — one-way **AGPL-3.0-or-later-kompatibel** (FSF-bestätigt: Apache-2.0 →
  GPLv3/AGPLv3). Trailing-MIT-Block (Erik Hellman) für geforkte Teile, ebenfalls kompatibel.
- **Pure Jetpack Compose** (kein TextView/Markwon-View-Interop).
- **Coil-2-Variante** verwenden (App ist auf **Coil 2.7.0**, nicht Coil 3 — sonst zweiter
  Coil-Stack). Module:
  - `com.mikepenz:multiplatform-markdown-renderer-android:0.41.0`
  - `com.mikepenz:multiplatform-markdown-renderer-m3:0.41.0` (Material3-Theming)
  - `com.mikepenz:multiplatform-markdown-renderer-coil2:0.41.0` (Remote-Bilder)
  Version in `gradle/libs.versions.toml` zentral pflegen.
- Remote-Bilder: `Markdown(text, imageTransformer = <coil2-Transformer>)`; der App-`ImageLoader`
  aus `app/di/AppModule.kt` wird übergeben (geteilter Cache; `crossfade(false)` für E-Ink).
- **E-Ink-Invariante (Pflicht):** Default `markdownAnimations()` ruft `animateContentSize()`
  beim Bild-Laden → verletzt `animation-gating.md`. Opt-out über `LocalEinkMode`:
  `animations = markdownAnimations(animateTextSize = { mod -> if (eink) mod else mod.animateContentSize() })`.
  Auf E-Ink wird der Modifier unverändert zurückgegeben → keine Bewegung. Die exakte
  Signatur/Klassennamen des coil2-Transformers + `markdownAnimations` werden zur Implementierung
  gegen die 0.41.0-Quelle fixiert (Plan-Aufgabe).

### Datenvertrag (Modul `:data`)

`data/.../plugin/repo/RepoModels.kt` — `RepoPluginEntry` um drei Felder erweitern, alle mit
Default `""` (toleranter Parse; `parseRepoIndex` ignoriert unbekannte Felder bereits):

```kotlin
@Serializable
data class RepoPluginEntry(
    val packageName: String = "",
    val name: String = "",
    val description: String = "",
    val type: String = "source",
    val abiVersion: Int = 0,
    val versionCode: Long = 0,
    val versionName: String = "",
    val apkUrl: String = "",
    val fingerprint: String = "",
    val previewUrl: String = "",   // NEU — generisches Vorschau-Bild (alle Typen)
    val readmeUrl: String = "",    // NEU — README.md zum Rendern
    val license: String = "",      // NEU — SPDX (P1: nur Anzeige; P2: Allowlist/Block)
)
```

- `parseRepoIndex` bleibt unverändert (Pflichtfelder-Filter unberührt; die drei neuen Felder
  sind optional). `previewUrl`/`readmeUrl` werden — wenn relativ — wie `apkUrl` über
  `resolveApkUrl(repoUrl, …)` gegen die Repo-Basis aufgelöst (gemeinsame Auflösung
  wiederverwenden, nicht duplizieren — `shared-structure-before-variants.md`).
- Die Felder fließen unverändert durch `BrowsableEntry` → `BrowserRow` (beide tragen schon
  `entry`/`item.entry`, also kein Strukturumbau — die UI liest `row.item.entry.previewUrl` etc.).

### README-Fetch (Modul `:data`)

`data/.../plugin/repo/PluginRepoClient.kt` — Schwester zu `fetchIndex`:

```kotlin
/** Lädt beliebigen Text (README-Markdown); null bei Netz-/HTTP-Fehler. */
suspend fun fetchText(url: String): String?
```

Reines I/O auf `Dispatchers.IO`, identisches Fehlerverhalten wie `fetchIndex` (null bei
Fehler/nicht-2xx). Kein Parsen.

### State (Modul `:app`)

`app/ui/plugins/PluginsViewModel.kt` — Info-Modal-Zustand:

- `infoFor: StateFlow<BrowserRow?>` — welche Zeile das Modal zeigt (null = zu).
- `readmeState: StateFlow<ReadmeState>` — `Loading | Loaded(text) | Empty` (Empty = kein
  README/Fehler → Fallback auf `description`).
- `openInfo(row)` setzt `infoFor`, startet (falls `readmeUrl` nicht leer) `fetchText` und füllt
  `readmeState`; sonst `Empty`. `closeInfo()` räumt beides ab.

### UI (Modul `:app`)

`app/ui/plugins/PluginsScreen.kt`:

1. `RepoRow` bekommt `onInfo: () -> Unit`. Ein **ℹ-`IconButton`** wird **vor** dem
   Install/Update/Status-Block gerendert (links davon). Glyphe: `AppIcons.Info` (existiert in der
   Icon-Registry verifizieren; sonst `IconKey` + Mapping in `DefaultIconPack` ergänzen, siehe
   `eink-design-language.md` / Icon-System). Flacher 1.5px-Stil unverändert.
2. Ein neues `PluginInfoModal` (privates Composable, `EinkInfoDialog`-Basis — die etablierte
   Dialog-Naht, `eink-design-language.md`):
   - **Kopf:** Name (`entry.name`), Typ-Label (vorhandenes `typeLabel`-Mapping), `v{versionName}`,
     und — falls `entry.license` nicht leer — die SPDX-Lizenz als gedämpfte Zeile.
   - **Vorschau-Bild:** falls `entry.previewUrl` nicht leer → `FilteredImage`/`AsyncImage` mit dem
     App-`ImageLoader`, `crossfade(false)`. Fehlt/Fehler → weglassen (kein Platzhalter-Spuk).
   - **Body:** `readmeState` → `Loading` = `LoadingIndicator`; `Loaded` = gerendertes Markdown
     (Lib, Remote-Bilder an, E-Ink-Anim aus); `Empty` = `entry.description` als einfacher Text
     (oder ein „Keine Beschreibung"-Hinweis, falls auch `description` leer).
3. `PluginsScreen` verdrahtet `onInfo = { viewModel.openInfo(row) }` und rendert
   `PluginInfoModal` an `infoFor`.

### i18n (`app/i18n/Strings.kt`, de+en, Compile-Parität)

Neue Keys: `pluginInfo` (Button-Label/ContentDescription), `pluginInfoTitle` (optional), 
`pluginInfoLicense` (Label „Lizenz"), `pluginInfoNoReadme`/`pluginInfoLoadingReadme`. Echte
Umlaute.

## Datenfluss

```
repo.json (P3 füllt previewUrl/readmeUrl/license)
  → PluginRepoClient.fetchIndex → parseRepoIndex (RepoPluginEntry inkl. 3 neue Felder)
  → mergeRepoEntries → BrowsableEntry → BrowserRow
  → PluginsScreen RepoRow [ℹ]  --onInfo-->  PluginsViewModel.openInfo(row)
       │                                        ├─ readmeUrl leer? → readmeState=Empty
       │                                        └─ sonst PluginRepoClient.fetchText(readmeUrl)
       │                                              → Loaded(text) | Empty (Fehler)
       └─ PluginInfoModal(infoFor, readmeState)
            Kopf(name/typ/version/license) + previewUrl-Bild + Markdown(readme) / description-Fallback
```

## Fehlerbehandlung

- `readmeUrl` leer **oder** `fetchText` liefert null → `Empty` → `description` zeigen (oder
  Hinweistext, wenn auch leer). Nie Spinner-Hänger.
- `previewUrl` leer **oder** Bild-Laden scheitert (Coil) → Bild-Sektion weglassen.
- Unbekannte/zukünftige `repo.json`-Felder → von `Json { ignoreUnknownKeys = true }` ignoriert
  (Bestand).
- Kein Netz beim Modal-Öffnen → README `Empty`, Bild fehlt → Modal zeigt nur Kopf + ggf.
  `description`. Kein Crash.

## E-Ink-Invarianten (host-erzwungen)

- **Keine Bewegung:** `markdownAnimations`-Opt-out über `LocalEinkMode` (s. o.); Coil
  `crossfade(false)`. Verifizieren, dass die Lib sonst keine Motion einbringt (Recherche: tut
  sie nicht — reiner Text/Bild-Renderer).
- Flacher Onyx-Look: Modal über `EinkInfoDialog` (1.5px-Border, kein Schatten), Icons via
  `AppIcons`. Konform `eink-design-language.md`.
- Akzent/Theme über Tokens (kein Hardcode).

## Lizenz- & Doku-Pflichten (Teil von P1)

- `NOTICE`: Eintrag „multiplatform-markdown-renderer (Markdown-Render im Plugin-Info-Modal) —
  Apache-2.0, Mike Penz" + den eingebetteten MIT-Hinweis (Erik Hellman 2021) + Coil-Transitiv
  (Apache-2.0, falls nicht schon gelistet) — mit Repo-URL.
- `README.md`: Bundled-/Dependency-Lizenzzeile um die Lib ergänzen; **Link zum Lib-Repo**
  (`https://github.com/mikepenz/multiplatform-markdown-renderer`). (User-Wunsch: README verlinkt
  die einzelnen Projekte.)
- `komga-doc-sync`: betroffene Rules/Docs im selben Commit nachziehen, soweit P1 sie berührt
  (Plugin-Discover-UX). `data-provenance.md` betrifft P1 nicht (keine Datenquelle), greift in P3.

## Tests

- **Unit, pure (`:data`):** `RepoIndexParserTest` erweitern — `parseRepoIndex` mit gesetzten
  `previewUrl`/`readmeUrl`/`license` **und** mit fehlenden (Default `""`); relative
  `previewUrl`/`readmeUrl` werden korrekt gegen die Repo-Basis aufgelöst.
- **ViewModel-Logik:** `openInfo` mit leerem `readmeUrl` → `Empty` ohne Netz-Call; mit gesetztem
  → `fetchText` aufgerufen, `Loaded`/`Empty` je Ergebnis (mit Fake-Client).
- **E-Ink-Gating:** Verdrahtung der `markdownAnimations`-Opt-out gegen `LocalEinkMode` belegt
  (Unit/Preview oder kleiner UI-Test — der Pfad muss existieren).
- **E2E/manuell:** Test-`repo.json`-Fixture (MockWebServer, Muster `PluginRepoClientTest`) mit
  einem Eintrag inkl. `readmeUrl`+`previewUrl` → Info-Modal zeigt Bild + gerendertes README
  (Emulator `eink_test`, Boox-Maße). Beweis per Screenshot.

## Risiken / offene Punkte

- **Exakte 0.41.0-API** (coil2-Transformer-Klasse, `markdownAnimations`-Signatur) muss zur
  Implementierung gegen die Quelle fixiert werden — Plan-Aufgabe, kein Design-Risiko.
- Lib zieht Coil-2-Transitiv: passt zum App-Stack (kein neuer Coil). Method-Count minimal.
- Markdown-Render auf großem README: README bewusst klein halten (Font-Plugins: Name +
  Specimen-Bild + 2–3 Zeilen). Kein Paging nötig (Modal scrollt).

## Abgrenzung zur Folge

P2 baut **auf** den `license`-Feldern auf (Allowlist/Block) und nutzt `previewUrl`/`readmeUrl`
für die Font-Discover-Zeilen unverändert weiter. P3 **füllt** die Felder in den realen
`repo.json`-Einträgen + liefert die Specimen-PNGs (die im README eingebettet via P1 erscheinen).
