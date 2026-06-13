# UI-Packs — Externe Skins für den Komga Reader

Ein **UI-Pack** ist ein installierbares Android-APK, das rein deklarativ Teile der App-Oberfläche
ersetzt — ohne eine Zeile Code. Der Host rendert alles; das APK liefert nur ein JSON-Asset.

---

## 1. Was ein UI-Pack ist — und was nicht

### Was es ist

- Ein **data-only APK** (`android:hasCode="false"`) mit einem einzigen Asset: `assets/ui_pack.json`.
- Der Deskriptor ersetzt deklarativ bis zu drei Dinge: das **Navigation-Skelett** (Shell), die
  **Icon-Zuordnungen** und das **Theme** (Farben, Radien, Typo).
- Der **Host rendert alles**. Das Pack liefert Daten, keine Compose-Lambdas.

### Was NICHT geht

| Nein | Warum |
|---|---|
| Kein Compose-/Kotlin-Code im Pack | Plugin-Code mit Host-Rechten würde bei einem Absturz die ganze App reißen und E-Ink-Invarianten aushebeln. |
| Keine Font-Dateien | Typo-Tuning ist auf Gewichte und Tracking beschränkt (Zahlen, keine TTF/OTF). |
| Farb-Override gilt nicht im E-Ink-Modus | Auf mono/Kaleido E-Ink erzwingt der Host das monochrome Theme (`DisplayBehavior.allowsAccentColor = false`) — externe Farben werden ignoriert. Radien, Typo, Shell und Icons gelten immer. |
| Keine beliebigen Material-Rollen | Nur die acht exponierten Rollen (s. u.) — alle anderen leitet der Host konservativ ab. |

---

## 2. AndroidManifest

Kopiere das Sample-Modul aus `plugin/komga-ui-pack-sample/` und passe `namespace`, `applicationId`
und `android:label` an. Der Rest des Manifests bleibt **verbatim**:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application android:label="Mein UI-Pack" android:hasCode="false">
        <meta-data
            android:name="com.komgareader.plugin.DATA_CATEGORY"
            android:value="UI_PACK" />
        <meta-data
            android:name="com.komgareader.plugin.DATA_ASSET"
            android:value="ui_pack.json" />
        <meta-data
            android:name="com.komgareader.plugin.ABI_VERSION"
            android:value="2" />
    </application>
</manifest>
```

Pflichtfelder:

| Meta-Daten-Key | Wert |
|---|---|
| `DATA_CATEGORY` | `UI_PACK` (fest) |
| `DATA_ASSET` | `ui_pack.json` (Asset-Pfad relativ zu `assets/`) |
| `ABI_VERSION` | `2` (aktuelle ABI-Version) |

---

## 3. Schema-Referenz

Alle drei Sektionen sind **optional** (ein Pack darf eine, zwei oder alle drei liefern).
Fehlt eine Sektion → Host-Default bleibt. Fehlende Felder innerhalb einer Sektion ebenfalls
→ tolerant verworfen, nie Crash.

### 3.1 `shell` — Navigation-Skelett

```json
"shell": {
  "navStyle": "FLOATING_NAV"
}
```

| Feld | Typ | Mögliche Werte | Bedeutung |
|---|---|---|---|
| `navStyle` | String | `BOTTOM_BAR`, `DRAWER`, `FLOATING_NAV` | Überschreibt den gerätegröße-basierten Form-Faktor-Default. Fehlt → Form-Faktor-Heuristik bleibt. |

### 3.2 `icons` — Icon-Glyphen remappen

```json
"icons": {
  "Settings": "Palette",
  "Reader":   "Bookmark"
}
```

Das ist eine **Map von IconKey-Name zu IconKey-Name** (beide müssen gültige `IconKey`-Enum-Werte sein).
Bedeutung: „Rendere die Semantik des linken Schlüssels mit dem Glyphen des rechten."

Ungültige Schlüssel oder Werte werden schweigend verworfen. Ein Teil-Pack muss nicht alle Keys belegen —
fehlende Keys fallen auf den Default zurück.

Vollständige Liste der verfügbaren `IconKey`-Namen:

```
Close, Back, Forward, Check, Plus, Minus,
ChevronRight, ChevronDown, ChevronUp,
Home, Search, Refresh, Edit, Settings, Delete,
Download, Local, Cloud, Info, Filter, Overflow, Stop,
GridView, LargeGridView, ListView,
Bookmark, BookmarkFilled,
Library, Groups, Plugins,
Contrast, Palette, Reader, Language, Connection,
ReaderMode, PanelMode, Typography, TableOfContents,
AlignLeft, AlignJustify
```

### 3.3 `theme` — Farben, Radien, Typo

Alle Felder optional. Fehlende Farb-Rollen bleiben beim Material-Default des jeweiligen Modus.

#### Strukturfelder

| Feld | Typ | Wertebereich | Bedeutung |
|---|---|---|---|
| `cornerRadius` | Int (dp) | 0–32 | Eckradius der Cards. Gilt immer (invariant-neutral). |
| `elevation` | Boolean | `true` / `false` | Schatten/Elevation an (`true`) oder flach (`false`). |
| `typography.headlineWeight` | Int | 100–900 | Schriftgewicht für Überschriften. |
| `typography.titleWeight` | Int | 100–900 | Schriftgewicht für Titel. |
| `typography.headlineTrackingEm` | Float | z. B. `-0.02` | Letter-Spacing der Überschriften in em. |

#### Farb-Rollen (`light` und/oder `dark`)

Beide Blöcke sind optional. Fehlt ein Modus → der Host spiegelt den anderen (nur-light Pack gilt
auch als dark-Fallback und umgekehrt). Fehlen beide → keine Farb-Übernahme (tokenOverride-Pfad
für flaches `accent`/`cornerRadius` bleibt).

Alle Farben als `#RRGGBB`-Hex-String, alle optional:

| Rolle | Bedeutung |
|---|---|
| `background` | Hintergrundfarbe der ganzen App |
| `surface` | Karten- und Sheet-Oberfläche |
| `navDock` | Fläche der Bottom-Navigation-Bar |
| `accent` | Primärfarbe / Akzent (Buttons, aktive Elemente) |
| `onAccent` | Text/Icons auf dem Akzent-Hintergrund |
| `onBackground` | Text/Icons auf dem Hintergrund |
| `onSurfaceVariant` | Sekundärer Text auf Oberflächen |
| `outline` | Rahmen, Trennlinien |

> **E-Ink-Hinweis:** Die gesamte Farb-Sektion (`light`/`dark`) wird vom Host nur angewandt, wenn
> `DisplayBehavior.allowsAccentColor` — also im Smartphone-/LCD-Modus. Auf mono und Kaleido E-Ink
> ignoriert der Host externe Farben und bleibt beim geräteklassen-eigenen mono Pack. Radien, Typo,
> Shell und Icons gelten immer.

---

## 4. Vollständiges Beispiel — Aurora UI-Pack

Das Aurora-Pack reproduziert den In-Tree-`AuroraPack`-Look rein deklarativ
(„1→3-Beweis": In-Tree-Compose-Pack → Daten-APK liefert dasselbe).

**`assets/ui_pack.json`**:

```json
{
  "shell": {
    "navStyle": "FLOATING_NAV"
  },
  "theme": {
    "cornerRadius": 16,
    "elevation": true,
    "typography": {
      "headlineWeight": 700,
      "titleWeight": 700,
      "headlineTrackingEm": -0.02
    },
    "light": {
      "background":       "#CDD1D9",
      "surface":          "#C3C8D1",
      "navDock":          "#959CAA",
      "accent":           "#3D5AFE",
      "onAccent":         "#FFFFFF",
      "onBackground":     "#1A1D24",
      "onSurfaceVariant": "#3F4450",
      "outline":          "#B1B7C2"
    },
    "dark": {
      "background":       "#15171C",
      "surface":          "#1C1F26",
      "navDock":          "#1C1F26",
      "accent":           "#3D5AFE",
      "onAccent":         "#FFFFFF",
      "onBackground":     "#E9EAEE",
      "onSurfaceVariant": "#9296A0",
      "outline":          "#2E313A"
    }
  }
}
```

Dieses Pack setzt keine `icons`-Sektion — alle Icons bleiben beim Default.

---

## 5. Walkthrough: So baust du einen UI-Pack

### Schritt a — Modul kopieren

```bash
cp -r plugin/komga-ui-pack-sample plugin/komga-ui-pack-meinpack
```

Öffne `plugin/komga-ui-pack-meinpack/app/build.gradle.kts` und passe an:

```kotlin
android {
    namespace = "com.meinname.uipack.meinpack"
    defaultConfig {
        applicationId = "com.meinname.uipack.meinpack"
    }
}
```

Passe in `AndroidManifest.xml` das `android:label` an.

### Schritt b — `assets/ui_pack.json` schreiben

Lege `app/src/main/assets/ui_pack.json` an (das Verzeichnis existiert im Sample schon).
Trage nur die Sektionen ein, die dein Pack überschreiben soll — alle anderen weglassen.

### Schritt c — APK bauen

```bash
cd plugin/komga-ui-pack-meinpack
./gradlew assembleDebug
# APK liegt in: app/build/outputs/apk/debug/app-debug.apk
```

### Schritt d — Cert-Fingerprint holen

```bash
apksigner verify --print-certs app/build/outputs/apk/debug/app-debug.apk | grep "SHA-256"
```

Den ausgegebenen `SHA-256`-Wert (Doppelpunkt-getrennte Hex-Bytes) notieren — er wird im
Repo-Index als `fingerprint` eingetragen und vom Host beim Install geprüft (TOFU-Pinning).

### Schritt e — Distributions-Repo-Eintrag

Klone das Distributions-Repo (`Gabriel-Graf/KomgaReaderPlugins`) und:

1. Kopiere die APK nach `plugins/komga-ui-pack-meinpack-1.0.0.apk`.
2. Füge in `repo.json` unter `"plugins"` einen Eintrag hinzu:

```json
{
  "packageName": "com.meinname.uipack.meinpack",
  "name": "Mein UI-Pack",
  "description": "Kurzbeschreibung des Packs",
  "type": "ui_pack",
  "abiVersion": 2,
  "versionCode": 1,
  "versionName": "1.0.0",
  "apkUrl": "plugins/komga-ui-pack-meinpack-1.0.0.apk",
  "fingerprint": "XX:XX:XX:..."
}
```

3. Committe und pushe beides.

### Schritt f — Im App installieren und aktivieren

1. Öffne den **In-App-Repo-Browser** (Plugins-Tab → `+`).
2. Finde deinen Pack in der Liste → **Installieren** (OS-Installations-Dialog erscheint).
3. Nach der Installation: **Einstellungen → Darstellung → UI-Pack** → deinen Pack auswählen.
4. Der ausgewählte Pack ist sofort aktiv (Shell/Icons). Im Smartphone-Modus auch die Farben.

---

## 6. E-Ink-Hinweis

Farben und Elevation (`theme.light`/`theme.dark`, `theme.elevation`) werden vom Host
**nur auf Smartphone/LCD-Geräten** angewandt. Auf E-Ink-Geräten (mono und Kaleido Boox)
bleibt der geräteklassen-eigene monochrome Look — dieser Schutz ist host-erzwungen und
kann durch ein Pack nicht umgangen werden.

**Invariant-neutrale Felder** (gelten auf jedem Gerät):
- `shell.navStyle` — Navigation-Skelett
- `icons` — Icon-Glyphen
- `theme.cornerRadius` — Eckradius
- `theme.typography` — Schriftgewichte und Tracking
