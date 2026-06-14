# Font-Plugins P3 — Fünf OFL-Schriften + Specimen-Generator + repo.json

> Datum: 2026-06-14 · Status: Design freigegeben · Phase: P3 von 3 (Font-Plugin-Vorhaben)

## Einordnung: das 3-Spec-Vorhaben

- **P1 (fertig, auf main):** generische Discover-UX (Info-Modal mit gerendertem README + Vorschau-Bild;
  `RepoPluginEntry` trägt `previewUrl`/`readmeUrl`/`license`).
- **P2 (fertig, auf main):** Font-Subsystem — `PluginCategory.FONT`, crengine-Live-Registrierung
  (`nativeAddFont`), `extractFontAsset`, harter SPDX-Lizenz-Gate (`FontLicensePolicy`, Allowlist),
  Picker-Merge + Live-Sample.
- **P3 (dieses Dokument):** die fünf realen Schriften als installierbare data-only Font-Plugins
  veröffentlichen — mit Vorschau-Bild + README im Info-Modal und Lizenz-Hygiene. **Keine
  App-Code-Änderung**: P3 nutzt nur die in P2 gebaute Kategorie + den Gate und die in P1 gebauten
  `repo.json`-Felder.

## Motivation

P2 hat das Subsystem gebaut und mit einem manuell erzeugten Test-APK (EB Garamond) verifiziert, aber
es gibt **keine** offiziell verteilten Font-Plugins. P3 schließt das: fünf kuratierte, freie
Lese-Schriften, gebaut + signiert + im offiziellen Distributions-Repo `KomgaReaderPlugins` indexiert,
sodass ein Nutzer sie über den Plugins-Tab → Repo-Browser entdecken (Vorschau-Bild + README),
installieren (Lizenz-Gate erlaubt OFL-1.1) und im Novel-Reader auswählen kann.

## Ziele

1. Fünf data-only Font-APKs (je eine OFL-1.1-Schrift), gebaut im Multi-Modul-Gradle-Projekt
   `KomgaReaderPlugins`, debug-signiert (Standard-Fingerprint), in `plugins/` abgelegt.
2. Ein **Specimen-PNG-Generator** (TTF → Vorschaubild) — deterministisch, E-Ink-tauglich, je Schrift
   ein PNG unter `plugins/specimens/`.
3. `repo.json`-Einträge je Schrift mit `type:"font"`, `license:"OFL-1.1"`, `previewUrl` (Specimen),
   `readmeUrl` (Text-README), `fingerprint`, `apkUrl`, `versionCode`/`versionName`.
4. **Lizenz-Hygiene:** OFL.txt je Modul mitgeliefert; `FONTS-PROVENANCE.md` (Quelle/URL/SPDX/Datum/
   Commit-Pin/Familienname je Schrift) gemäß `data-provenance.md`; RFN-Schriften behalten den
   Originalnamen (kein Derivat → keine Umbenennungspflicht).
5. E2E: mindestens eine Schrift über den echten Repo-Browser-Pfad installieren und im Reader rendern
   (Emulator `emulator-5554`).

## Nicht-Ziele

- Keine Änderung am App-Repo `komga-reader` außer Spec/Plan-Doku (P2 lieferte den ganzen Code).
- Keine Variable Fonts (Android/Compose-VF-Achsen fragil) — nur STATIC-Instanzen.
- Keine Mehr-Face-Familien (Bold/Italic als eigene Faces) — eine Familie = eine Regular-TTF in P3
  (das `FontSpec`/Picker-Modell ist additiv erweiterbar, aber YAGNI hier).
- Keine UI-Typografie (App-Schrift) — Lese-Schriften für den NOVEL-/crengine-Reader.

## Architektur

Alles im Distributions-Repo `KomgaReaderPlugins` (lokaler Klon
`/home/gabriel/Documents/Projekte/KomgaReaderPlugins`, GitHub `Gabriel-Graf/KomgaReaderPlugins`).
Spec/Plan liegen im App-Repo `komga-reader/docs/superpowers/` (Konsistenz mit P1/P2).

### A) Fünf Font-Module (ein APK pro Schrift)

Granularität: **ein APK pro Schrift** (5 Module), analog `komga-lang-{es,fr,it}` — der Nutzer
installiert Schriften einzeln. Jedes Modul folgt der Vorlage `komga-reader-preset-eink`:

```
komga-font-<id>/
  build.gradle.kts           # com.android.application; namespace/applicationId
                             #   com.komgareader.font.<id>; minSdk 28; targetSdk 34; vc 1 / 0.1.0
  src/main/AndroidManifest.xml
  src/main/assets/fonts/<Font>.ttf   # STATIC Regular instance
  src/main/assets/fonts.json         # [FontSpec]
  src/main/assets/OFL.txt            # license text (OFL-1.1 obligation)
  README.md                          # text only (name/designer/upstream/license/E-Ink note)
```

`settings.gradle.kts` bekommt die fünf neuen `include(":komga-font-<id>")`.

Manifest je Modul:
```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application android:label="<Font Name>" android:hasCode="false">
        <meta-data android:name="com.komgareader.plugin.DATA_CATEGORY" android:value="FONT" />
        <meta-data android:name="com.komgareader.plugin.DATA_ASSET"    android:value="fonts.json" />
        <meta-data android:name="com.komgareader.plugin.ABI_VERSION"   android:value="2" />
        <meta-data android:name="com.komgareader.plugin.LICENSE"       android:value="OFL-1.1" />
    </application>
</manifest>
```
ABI_VERSION bleibt **2** (FONT-Kategorie kam mit ABI 2; `PluginAbi.VERSION` ist inzwischen 3 wegen
PANEL_MODEL, MIN_SUPPORTED=1 → 2 liegt in Range 1..3, kompatibel).

`fonts.json` je Modul (ein Eintrag):
```json
[ { "family": "<exact FreeType family>", "label": "<Font Name>", "asset": "fonts/<Font>.ttf", "license": "OFL-1.1" } ]
```

Die fünf Schriften (Details/Upstreams: Memory `font-plugins-research`):

| id / Paket-Suffix | `android:label` / FontSpec.label | family (FreeType) | RFN | Upstream + Pin |
|---|---|---|---|---|
| `ebgaramond` | EB Garamond | EB Garamond | nein | google/fonts `ofl/ebgaramond` (Commit-Pin) |
| `lora` | Lora | Lora | **ja** | cyrealtype/Lora-Cyrillic Release v3.021 |
| `merriweather` | Merriweather | Merriweather | **ja** | SorkinType/Merriweather (Commit-Pin) |
| `sourceserif` | Source Serif 4 | Source Serif 4 | **ja** | adobe-fonts/source-serif Release 4.005R — **STATIC TTF**, nicht CFF2-VF |
| `atkinson` | Atkinson Hyperlegible Next | Atkinson Hyperlegible Next | nein (Marke™) | googlefonts/atkinson-hyperlegible-next (Commit-Pin) |

**Familienname-Kritikalität:** `FontSpec.family` muss exakt dem internen FreeType-Namen entsprechen
(crengine wählt darüber: `novelFontFamily` → `font.face.default`). Zur Build-Zeit je TTF per
`fc-scan --format '%{family}\n'` prüfen und in `fonts.json` exakt eintragen (Vorbild: P2-E2E
verifizierte „EB Garamond" so). RFN-Schriften behalten den Originalnamen → kein Derivat, keine
Umbenennung.

### B) Specimen-PNG-Generator

Tooling: **Python + Pillow**, Script `tools/make_specimen.py` in `KomgaReaderPlugins`.

- Eingabe: TTF-Pfad + Anzeigename + Ausgabepfad. Rendert auf weißem Grund, schwarze Schrift
  (E-Ink-tauglich, maximaler Kontrast): den **Schrift-Namen** (groß) + ein **englisches
  Pangram-Muster** („The quick brown fox jumps over the lazy dog") + Groß-/Kleinbuchstaben + Ziffern,
  in der **echten Schrift** (Pillow `ImageFont.truetype`). **Specimen-Text ist Englisch** (Standard-
  Schriftmuster-Konvention; vermeidet zugleich fehlende Sonderzeichen-Glyphen wie „ẞ").
- Größe so, dass das Info-Modal es lesbar zeigt; EB Garamonds feine Haarlinien → ausreichend große
  Punktgröße/Pixeldichte, kein Downscaling-Verwaschen.
- **Deterministisch:** kein Datum/Zufall im Bild; gleiche TTF → byte-stabiles PNG (reproduzierbar,
  diff-bar). Pillow-Version pinnen.
- Ausgabe `plugins/specimens/<id>.png` (committet — kleine PNGs, kein Binär-Riese wie ONNX).
- Ein `tools/make_all_specimens.sh` (oder ein Python-`main`) erzeugt alle fünf aus den Modul-TTFs.

### C) repo.json-Einträge + Doku

Je Schrift ein Objekt in `repo.json` `plugins[]` (URLs **relativ** zur Repo-Basis; die App löst sie
über `resolveRepoUrl`):
```json
{
  "packageName": "com.komgareader.font.<id>",
  "name": "<Font Name>",
  "description": "<kurze DE-Beschreibung, z. B. 'Serifenschrift fürs Lesen'>",
  "type": "font",
  "abiVersion": 2,
  "versionCode": 1,
  "versionName": "0.1.0",
  "apkUrl": "plugins/komga-font-<id>-0.1.0.apk",
  "fingerprint": "F4:16:A7:...:DA",   // Standard-Debug-Key (bekannt)
  "previewUrl": "plugins/specimens/<id>.png",
  "readmeUrl": "komga-font-<id>/README.md",
  "license": "OFL-1.1"
}
```

Aufteilung previewUrl vs. readmeUrl: `previewUrl` = Specimen-Bild; `README.md` = **nur Text**
(Schrift-Name, Designer/Upstream-Link, Lizenz OFL-1.1, kurze „gut für E-Ink"-Notiz). **Kein Bild im
README** — das Specimen kommt schon über `previewUrl` ins Modal; doppelte Einbettung vermeiden.
(Falls README-Bilder gewünscht wären, müssten sie absolute `raw.githubusercontent`-URLs sein, da der
Markdown-Renderer relative Bildpfade nicht gegen die Repo-Basis auflöst — bewusst weggelassen.)

Provenance: `FONTS-PROVENANCE.md` im Repo-Root mit je Schrift Name · URL (Permalink) · SPDX-Lizenz ·
Cap/Umfang (eine Regular-TTF) · Filter (STATIC-Auswahl) · Erfassungsdatum · Commit/Release-Pin ·
Familienname · Risk-Notiz (RFN bei Lora/Merriweather/Source Serif). OFL.txt je Modul = die
Volltext-Lizenz-Pflicht der OFL.

### D) Build & Verifikation

1. TTFs aus den Upstreams ziehen, **STATIC Regular** auswählen, Release/Commit pinnen, OFL.txt je
   Schrift mitziehen. Familienname je per `fc-scan` prüfen → `fonts.json`.
2. Module + `settings.gradle.kts` anlegen; je `./gradlew :komga-font-<id>:assembleDebug` (debug-
   signiert wie die anderen data-only Plugins → Standard-Fingerprint).
3. APKs nach `plugins/` kopieren (Namens-Schema `komga-font-<id>-0.1.0.apk`), Specimens generieren,
   `repo.json` + `FONTS-PROVENANCE.md` ergänzen.
4. **E2E auf `emulator-5554`** (nur Emulator; Boox `db4c96d` nie ohne OK): ein lokaler Repo-Server
   (oder die Default-`repo.json` nach Push) → Plugins-Tab → Repo-Browser zeigt die Font-Zeile mit
   ℹ-Info-Modal (Vorschau + README); Install (Fingerprint-Match) → Picker zeigt die Schrift mit
   Live-Sample → Novel-Reader rendert darin (Screenshot-Beleg). Mindestens eine Schrift voll; die
   anderen vier per Discovery/Install-Sichtprüfung.

## Datenfluss

```
Upstream-TTF (STATIC, gepinnt) ──> komga-font-<id>/src/main/assets/fonts/<Font>.ttf
                                    + fonts.json (family per fc-scan) + OFL.txt + README.md
  ./gradlew :komga-font-<id>:assembleDebug ──> plugins/komga-font-<id>-0.1.0.apk (debug-signiert)
  tools/make_specimen.py ──> plugins/specimens/<id>.png
  repo.json += { type:font, license:OFL-1.1, previewUrl, readmeUrl, fingerprint, apkUrl }
        │ (push KomgaReaderPlugins → GitHub raw)
        ▼
App (P1/P2, schon auf main): Repo-Browser lädt repo.json
  → Info-Modal: previewUrl-Bild + readmeUrl-Markdown + license-Anzeige
  → Install: Download → Fingerprint-Verify → [P2 Gate A: isLicenseAllowed("OFL-1.1")=true] → PackageInstaller
  → scanLocal: discoverDataPlugins(FONT) → [Gate B ok] → extractFontAsset → registerFont → allNovelFonts
  → Picker zeigt Schrift + Live-Sample → Reader rendert in der Familie
```

## Fehlerbehandlung / Fallstricke

- **Familienname-Mismatch:** häufigster Fehler — `fc-scan` ist Pflicht je Schrift; manche Familien
  haben Sub-Family-Suffixe (z. B. „EB Garamond 12"), der **primäre** Familienname zählt.
- **Variable Font erwischt:** Adobe Source Serif liefert auch CFF2-VF — **explizit die STATIC TTF**
  wählen (Release-Ordner `*/TTF/`), sonst rendert crengine sie evtl. nicht/falsch.
- **Specimen-Glyphen:** der englische Pangram-Text nutzt nur Basis-Latein (A–Z, a–z, 0–9) — bei jeder
  der fünf Schriften vollständig vorhanden, kein `.notdef`/Tofu-Risiko. (Deshalb Englisch statt eines
  deutschen Musters mit „ẞ".)
- **Fingerprint:** debug-signiert → Standard-Key-SHA-256 (in `data-plugin-distribution` dokumentiert);
  Repo-Install verifiziert dagegen. Kein Release-Key in P3.
- **Lizenz-Gate (P2):** OFL-1.1 ist auf der Allowlist → Install/Registrierung erlaubt; das ist der
  Beweis, dass der Gate die kuratierten Schriften durchlässt.

## E-Ink-Invarianten

P3 liefert **Daten** (TTFs, JSON, PNGs) — keine UI. Die E-Ink-Invarianten erzwingt der Host (P2):
Live-Sample ist statisch, Picker ohne Motion. Die Specimen-PNGs sind schwarz-auf-weiß, hoher
Kontrast, lesbar auf 1264×1680@300.

## Lizenz- & Doku-Pflichten

- OFL.txt je Modul (OFL-1.1-Pflicht: Lizenztext mitliefern, Copyright/Reserved-Font-Name-Vermerk
  behalten).
- `FONTS-PROVENANCE.md` (data-provenance) — im **selben Commit** wie die Schriften.
- `KomgaReaderPlugins/README.md` um die Font-Kategorie ergänzen.
- App-Repo `NOTICE`: die Schriften sind **nicht** in die App gebündelt (separate APKs) → kein
  App-`NOTICE`-Eintrag nötig; die Lizenzpflicht erfüllt das jeweilige Plugin-APK (OFL.txt im Asset).
- `komga-doc-sync` betrifft P3 nur am Rand (kein App-Code) — Memory `font-plugins-research`/
  `data-plugin-distribution` auf „P3 fertig" ziehen.

## Tests

- **Specimen-Generator:** ein kleiner Selbsttest/Smoke — Script gegen eine TTF laufen lassen, prüfen
  dass ein nicht-leeres PNG erwartete Maße entsteht und (Stichprobe) nicht rein weiß ist (Glyphen
  gerendert). Deterministisch: zweimal laufen → identische Bytes.
- **fonts.json-Konsistenz:** je Schrift `fc-scan`-Familienname == `fonts.json.family` (Build-Check/
  Script-Assert).
- **APK-Sanity:** `aapt2 dump xmltree` je APK → Meta-data (DATA_CATEGORY=FONT, LICENSE=OFL-1.1,
  hasCode=false) + Asset vorhanden.
- **E2E:** s. D) — Discovery→Info-Modal→Install→Picker→Reader auf dem Emulator, ≥1 Schrift voll.

## Abgrenzung / Folge

P3 schließt das Font-Plugin-Vorhaben ab. Spätere additive Möglichkeiten (kein Teil von P3): Mehr-Face-
Familien (Bold/Italic), weitere Schriften, ein Release-Signaturschlüssel statt Debug, Auto-Specimen im
CI. Der Picker/Gate/Discovery-Pfad (P2) bleibt unverändert.
