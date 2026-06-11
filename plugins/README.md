# `/plugins/` — externe Plugin-Repos

Jedes Plugin ist ein **eigenes Git-Repo** unter `plugins/<name>/`, im Haupt-Repo **gitignored**
(wie das Kavita-Quellen-Plugin). Eingecheckt bleiben nur diese `README.md` und das Template
`_template-data/`.

## Plugin-Typen

| Typ | Manifest-Deklaration | Code? | Referenz |
|---|---|---|---|
| Quelle (a) | `com.komgareader.plugin.SOURCE` (Entry-Klasse) + `ABI_VERSION` | ja | Kavita-Plugin |
| Color-Preset (c) | `DATA_CATEGORY=COLOR_PRESET` + `DATA_ASSET` + `ABI_VERSION` | nein | `_template-data/` |
| Reader-Preset (c) | `DATA_CATEGORY=READER_PRESET` + `DATA_ASSET` + `ABI_VERSION` | nein | `_template-data/` |
| Sprache (c) | `DATA_CATEGORY=LANGUAGE` + `DATA_ASSET` + `ABI_VERSION` | nein | `_template-data/` |

Data-only Plugins (c) tragen **keinen Code** — nur ein JSON-Asset + Manifest-Metadaten. Der Host
liest das Asset via `createPackageContext(pkg, 0)` (nur Ressourcen, kein Classloader/TOFU).

## Ein data-only Plugin bauen

1. `_template-data/` in ein neues Repo `plugins/<name>/` kopieren.
2. In `AndroidManifest.xml` die `DATA_CATEGORY` setzen, `applicationId`/Label anpassen.
3. `assets/data.json` mit der Nutzlast der Kategorie füllen (Schema siehe Spec 2).
4. APK bauen, auf dem Gerät installieren (`adb install -r …`).
5. In der App: Plugins-Tab → entdeckt das Plugin automatisch (Kategorie-spezifische Heimat-UI).

ABI: aktuell `2` (`PluginAbi.VERSION`). Plugin muss `ABI_VERSION` in `[1, 2]` deklarieren.
