# Font-Plugins P3 — Fünf OFL-Schriften Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fünf freie OFL-1.1-Lese-Schriften als installierbare data-only Font-Plugins im Distributions-Repo veröffentlichen — mit Specimen-Vorschau, README und Lizenz-Hygiene, lauffähig über den in P1/P2 gebauten Discover-/Install-/Picker-Pfad.

**Architecture:** Alle Deliverables liegen im **Distributions-Repo** `KomgaReaderPlugins` (`/home/gabriel/Documents/Projekte/KomgaReaderPlugins`, GitHub `Gabriel-Graf/KomgaReaderPlugins`): fünf data-only Gradle-Module (je ein Font-APK), ein Python+Pillow Specimen-Generator, `repo.json`-Einträge + `FONTS-PROVENANCE.md`. **Keine** Änderung am App-Repo `komga-reader` außer dieser Spec/Plan-Doku — P2 lieferte den ganzen App-Code (FONT-Kategorie, Lizenz-Gate, Picker, Live-Sample).

**Tech Stack:** Android `com.android.application` data-only Module (`android:hasCode="false"`), Gradle, debug-Signatur (Standard-Key), Python 3 + Pillow 10.2 (Specimen), fontTools 4.46 (VF→STATIC-Instanzierung-Fallback), `fc-scan` (Familienname-Verifikation), `aapt2` (APK-Sanity), Emulator `emulator-5554` (E2E).

**Spec:** `docs/superpowers/specs/2026-06-14-font-plugins-p3-fonts-design.md` (im App-Repo).

**Konvention:** Code/Kommentare/Doku **Englisch**; Commit-Messages **Deutsch** mit echten Umlauten, je endend mit `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`. Bash-Oneliner (keine Newlines im Befehl).

**Distributions-Repo-Pfad (alle KomgaReaderPlugins-Pfade relativ dazu):** `/home/gabriel/Documents/Projekte/KomgaReaderPlugins` — Variable `$R` in den Tasks.

**Die fünf Schriften** (Familienname je per `fc-scan` zur Build-Zeit verifizieren — Pflicht, sonst wählt crengine nicht):

| id | label / android:label | family (erwartet) | Paket | RFN | Upstream |
|---|---|---|---|---|---|
| `ebgaramond` | EB Garamond | EB Garamond | `com.komgareader.font.ebgaramond` | nein | google/fonts `ofl/ebgaramond` |
| `lora` | Lora | Lora | `com.komgareader.font.lora` | ja | github.com/cyrealtype/Lora-Cyrillic (Release v3.021) |
| `merriweather` | Merriweather | Merriweather | `com.komgareader.font.merriweather` | ja | github.com/SorkinType/Merriweather |
| `sourceserif` | Source Serif 4 | Source Serif 4 | `com.komgareader.font.sourceserif` | ja | github.com/adobe-fonts/source-serif (Release 4.005R, STATIC TTF) |
| `atkinson` | Atkinson Hyperlegible Next | Atkinson Hyperlegible Next | `com.komgareader.font.atkinson` | nein (Marke) | github.com/googlefonts/atkinson-hyperlegible-next |

---

## Task 1: Distributions-Repo — Branch + ausstehende WIP isolieren

`KomgaReaderPlugins` hat **vorab vorhandene, nicht-committete Fremd-WIP**: die Aurora→„Tablet-UI"-Umbenennung (0.1.0→0.1.1) + Hinzufügen „Smartphone-UI" in `repo.json`, plus `.gitignore`-Ergänzung (YOLO-ONNX-Ignore) und das gelöschte alte `plugins/komga-ui-pack-aurora-0.1.0.apk`. Die passenden neuen APKs (`komga-ui-pack-aurora-0.1.1.apk`, `komga-ui-pack-sample-0.1.0.apk`) liegen **bereits** in `plugins/` → die WIP ist kohärent + fertig, nur nicht committet. Damit die Font-Commits sauber bleiben, wird diese WIP zuerst als **eigener** Commit abgeschlossen, dann der Font-Branch darauf gesetzt.

**Files:**
- `$R` (KomgaReaderPlugins working tree)

- [ ] **Step 1: Stand sichten**

Run: `cd /home/gabriel/Documents/Projekte/KomgaReaderPlugins && git status --short && git branch --show-current`
Expected: auf `main`; `M .gitignore`, `M repo.json`, `D plugins/komga-ui-pack-aurora-0.1.0.apk` (ggf. weitere `??`-Einträge). Notiere alle untracked/modifizierten Pfade.

- [ ] **Step 2: Branch für die Font-Arbeit anlegen** (nimmt die WIP im Working Tree mit — das ist ok, sie wird in Step 3 zuerst committet)

Run: `cd /home/gabriel/Documents/Projekte/KomgaReaderPlugins && git checkout -b feat/font-plugins`
Expected: „Switched to a new branch 'feat/font-plugins'".

- [ ] **Step 3: Ausstehende Aurora-WIP als eigenen Cleanup-Commit abschließen**

Nur die schon vorhandenen WIP-Pfade stagen (die zur Aurora-Umbenennung gehören) — KEINE Font-Dateien (gibt es noch nicht). 
Run: `cd /home/gabriel/Documents/Projekte/KomgaReaderPlugins && git add -A && git status --short`
Prüfe, dass nur die Aurora-Rename-bezogenen Pfade (`.gitignore`, `repo.json`, das gelöschte/neue ui-pack-APK) gestaged sind. Falls weitere unerwartete Pfade auftauchen, im Report melden, NICHT committen, BLOCKED.
Run: `git commit -m "chore(repo): Aurora→Tablet-UI-Umbenennung + Smartphone-UI-Eintrag abschließen (ausstehende WIP)$(printf '\n\nCo-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>')"`
Expected: Commit erstellt; `git status --short` danach **leer**.

- [ ] **Step 4: Verifizieren, dass das Repo baut (Baseline vor Font-Arbeit)**

Run: `cd /home/gabriel/Documents/Projekte/KomgaReaderPlugins && ./gradlew projects -q 2>&1 | tail -20`
Expected: Liste der bestehenden Module (`:komga-lang-es` etc.), kein Fehler. (Kein Voll-Build nötig.)

Kein weiterer Commit in diesem Task (nur der Cleanup-Commit aus Step 3).

---

## Task 2: Specimen-PNG-Generator (Python + Pillow) + Smoke-Test

Ein deterministisches Script, das aus einer TTF + Anzeigename ein Vorschau-PNG (englisches Pangram in der echten Schrift, schwarz auf weiß) rendert.

**Files:**
- Create: `$R/tools/make_specimen.py`
- Create: `$R/tools/test_make_specimen.py`
- Create: `$R/tools/requirements.txt`

- [ ] **Step 1: requirements.txt**

Create `$R/tools/requirements.txt`:
```
Pillow==10.2.0
```

- [ ] **Step 2: Failing test schreiben**

Create `$R/tools/test_make_specimen.py`:
```python
"""Smoke test for the specimen generator (deterministic, non-blank, expected size)."""
import os
import subprocess
import tempfile
from PIL import Image

# A TTF guaranteed present on this machine (used only by the test).
SYSTEM_TTF = "/usr/share/fonts/truetype/ebgaramond/EBGaramond12-Regular.ttf"
SCRIPT = os.path.join(os.path.dirname(__file__), "make_specimen.py")


def _render(out_path):
    subprocess.run(
        ["python3", SCRIPT, SYSTEM_TTF, "EB Garamond", out_path],
        check=True,
    )


def test_produces_non_blank_png_of_expected_size():
    with tempfile.TemporaryDirectory() as d:
        out = os.path.join(d, "spec.png")
        _render(out)
        assert os.path.exists(out)
        img = Image.open(out).convert("L")
        assert img.size == (1000, 420)  # WIDTH x HEIGHT from make_specimen
        # Not entirely white: at least some dark pixels were drawn (glyphs rendered).
        extrema = img.getextrema()  # (min, max)
        assert extrema[0] < 64, f"expected some dark pixels, got extrema={extrema}"


def test_is_deterministic():
    with tempfile.TemporaryDirectory() as d:
        a, b = os.path.join(d, "a.png"), os.path.join(d, "b.png")
        _render(a)
        _render(b)
        assert open(a, "rb").read() == open(b, "rb").read(), "specimen must be byte-stable"
```

- [ ] **Step 3: Test laufen lassen, Fehlschlag prüfen**

Run: `cd /home/gabriel/Documents/Projekte/KomgaReaderPlugins/tools && python3 -m pytest test_make_specimen.py -q`
Expected: FAIL (make_specimen.py existiert nicht / kein Output-PNG). Falls `pytest` fehlt: `python3 -m pytest` → `pip install --user pytest` oder Test direkt via `python3 test_make_specimen.py` mit einem `if __name__=="__main__"`-Runner; bevorzugt pytest.

- [ ] **Step 4: Generator implementieren**

Create `$R/tools/make_specimen.py`:
```python
#!/usr/bin/env python3
"""Render a deterministic font specimen PNG: the font name + an English pangram,
in the font's own face, black on white (E-Ink friendly, maximum contrast).

Usage: make_specimen.py <ttf-path> <display-name> <out-png>

Deterministic: no date/random; identical TTF + name -> byte-stable PNG.
The specimen text is English Basic-Latin only (A-Z a-z 0-9) so every bundled
font renders it without .notdef/tofu.
"""
import sys
from PIL import Image, ImageDraw, ImageFont

WIDTH, HEIGHT = 1000, 420
MARGIN = 48
BG, FG = 255, 0  # white bg, black text (grayscale 'L')
PANGRAM = "The quick brown fox jumps over the lazy dog"
ALPHA = "ABCDEFGHIJKLM NOPQRSTUVWXYZ"
ALPHA2 = "abcdefghijklm nopqrstuvwxyz  0123456789"


def render(ttf_path: str, display_name: str, out_png: str) -> None:
    img = Image.new("L", (WIDTH, HEIGHT), BG)
    draw = ImageDraw.Draw(img)
    name_font = ImageFont.truetype(ttf_path, 72)
    body_font = ImageFont.truetype(ttf_path, 40)
    small_font = ImageFont.truetype(ttf_path, 34)
    y = MARGIN
    draw.text((MARGIN, y), display_name, font=name_font, fill=FG)
    y += 96
    draw.text((MARGIN, y), PANGRAM, font=body_font, fill=FG)
    y += 64
    draw.text((MARGIN, y), ALPHA, font=small_font, fill=FG)
    y += 50
    draw.text((MARGIN, y), ALPHA2, font=small_font, fill=FG)
    img.save(out_png, "PNG", optimize=True)


if __name__ == "__main__":
    if len(sys.argv) != 4:
        sys.exit("usage: make_specimen.py <ttf-path> <display-name> <out-png>")
    render(sys.argv[1], sys.argv[2], sys.argv[3])
```

- [ ] **Step 5: Test laufen lassen, grün prüfen**

Run: `cd /home/gabriel/Documents/Projekte/KomgaReaderPlugins/tools && python3 -m pytest test_make_specimen.py -q`
Expected: PASS (2 Tests). Falls die Determinismus-Prüfung fehlschlägt (PNG-Metadaten mit Zeitstempel): `img.save(..., pnginfo=...)` ist hier nicht gesetzt; Pillow schreibt standardmäßig keinen Zeitstempel-Chunk → sollte byte-stabil sein. Falls doch nicht, im Report melden.

- [ ] **Step 6: Commit**

```bash
cd /home/gabriel/Documents/Projekte/KomgaReaderPlugins && git add tools/make_specimen.py tools/test_make_specimen.py tools/requirements.txt && git commit -m "feat(tools): deterministischer Specimen-PNG-Generator (Pillow) + Smoke-Test

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 3: Die fünf STATIC-TTFs beschaffen + Familiennamen verifizieren

Pro Schrift die **STATIC Regular**-TTF + die OFL-Lizenz beschaffen, in einen Staging-Ordner legen, Familienname per `fc-scan` verifizieren und notieren. (Die Module entstehen in Task 4; hier nur Beschaffung + Verifikation, damit Task 4 die exakten Familiennamen kennt.)

**Files:**
- Create: `$R/.fonts-staging/<id>/<Font>.ttf` und `$R/.fonts-staging/<id>/OFL.txt` (temporär; `.fonts-staging/` in `.gitignore` aufnehmen — NICHT committen)
- Modify: `$R/.gitignore` (Zeile `.fonts-staging/`)
- Create: `$R/.fonts-staging/families.txt` (Notiz: id → verifizierter Familienname + Quelle/Pin)

- [ ] **Step 1: Staging gitignoren**

In `$R/.gitignore` ergänzen: `.fonts-staging/`. (Verhindert versehentliches Committen der rohen Downloads; die finalen TTFs landen in Task 4 in den Modul-Assets.)

- [ ] **Step 2: TTFs + OFL beschaffen (pro Schrift)**

Für jede der fünf Schriften die **STATIC Regular**-TTF holen (curl von der Upstream-`raw.githubusercontent.com`-URL des gepinnten Commits/Release-Assets). Konkrete Bezugsregeln:
- **EB Garamond:** schon auf dem Host vorhanden — `cp /usr/share/fonts/truetype/ebgaramond/EBGaramond12-Regular.ttf .fonts-staging/ebgaramond/EBGaramond.ttf`. OFL: aus dem google/fonts-Repo `ofl/ebgaramond/OFL.txt` (curl). Quelle/Pin notieren (Paket-Version der Distro + google/fonts-Pfad).
- **Lora:** github.com/cyrealtype/Lora-Cyrillic, Release **v3.021** — STATIC `fonts/ttf/Lora-Regular.ttf` (Release-Asset oder Tag-Pfad). OFL `OFL.txt` aus dem Repo-Root.
- **Merriweather:** github.com/SorkinType/Merriweather — STATIC `fonts/ttf/Merriweather-Regular.ttf` am HEAD-Commit (Commit-SHA pinnen, da keine Releases). OFL `OFL.txt`.
- **Source Serif 4:** github.com/adobe-fonts/source-serif, Release **4.005R** — STATIC `TTF/SourceSerif4-Regular.ttf` (NICHT die VF/CFF2). OFL `LICENSE.md` (Adobe nutzt OFL-Volltext).
- **Atkinson Hyperlegible Next:** github.com/googlefonts/atkinson-hyperlegible-next — STATIC Regular-TTF (`fonts/ttf/AtkinsonHyperlegibleNext-Regular.ttf` o. ä.) am HEAD-Commit (SHA pinnen). OFL `OFL.txt`.

Wenn ein Upstream **nur** eine Variable Font liefert (kein STATIC im Repo): die Regular-Instanz mit fontTools erzeugen — `python3 -m fontTools.varLib.instancer <VF>.ttf wght=400 -o <Font>.ttf` — und das im Provenance vermerken (Task 6). Ablegen unter `.fonts-staging/<id>/<Font>.ttf` (Dateiname schlicht, z. B. `EBGaramond.ttf`, `Lora.ttf`, `Merriweather.ttf`, `SourceSerif4.ttf`, `AtkinsonHyperlegibleNext.ttf`).

Wenn eine URL nicht auflösbar ist (404), NICHT raten — die exakte Pfad-/Release-Struktur des Repos per `curl -sL https://api.github.com/repos/<owner>/<repo>/releases` bzw. `.../git/trees/<branch>?recursive=1` ermitteln, die STATIC-Regular-TTF finden, ziehen, und die finale URL+SHA notieren. Falls eine Schrift gar nicht beschaffbar ist, im Report BLOCKED mit Details.

- [ ] **Step 3: Familiennamen verifizieren (Pflicht)**

Für jede TTF: `fc-scan --format '%{family}\n' .fonts-staging/<id>/<Font>.ttf`. Den **primären** Familiennamen (vor dem ersten Komma) festhalten. Schreibe `$R/.fonts-staging/families.txt` mit je Zeile `<id> | <verifizierter Familienname> | <Quelle-URL> | <Commit/Release-Pin> | <instanziert? ja/nein>`.
Erwartete primäre Familiennamen (abgleichen — bei Abweichung gilt der `fc-scan`-Wert, und Task 4 nutzt DIESEN): `EB Garamond`, `Lora`, `Merriweather`, `Source Serif 4`, `Atkinson Hyperlegible Next`.
Außerdem je TTF prüfen, dass sie **statisch** ist (keine `fvar`-Achsen): `python3 -c "from fontTools.ttLib import TTFont; import sys; f=TTFont(sys.argv[1]); print('VARIABLE' if 'fvar' in f else 'static')" .fonts-staging/<id>/<Font>.ttf` → muss `static` sein (sonst in Step 2 instanzieren).

- [ ] **Step 4: Commit (nur .gitignore + die families.txt-Notiz wird NICHT committet, da unter .fonts-staging/)**

Nur die `.gitignore`-Änderung committen (die Staging-Inhalte bleiben ungetrackt):
```bash
cd /home/gabriel/Documents/Projekte/KomgaReaderPlugins && git add .gitignore && git commit -m "chore(repo): .fonts-staging/ ignorieren (rohe Font-Downloads, nicht versioniert)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```
Report MUSS den Inhalt von `families.txt` (die fünf verifizierten Familiennamen + Pins) enthalten — Task 4 braucht sie.

---

## Task 4: Die fünf Font-Module scaffolden

Pro Schrift ein data-only Gradle-Modul (Vorlage `komga-reader-preset-eink`), mit der in Task 3 verifizierten Familie. Die TTF + OFL aus `.fonts-staging/` in die Modul-Assets kopieren (DIESE werden committet — kleine TTFs).

**Files je `<id>` in {ebgaramond, lora, merriweather, sourceserif, atkinson}:**
- Create: `$R/komga-font-<id>/build.gradle.kts`
- Create: `$R/komga-font-<id>/src/main/AndroidManifest.xml`
- Create: `$R/komga-font-<id>/src/main/assets/fonts/<Font>.ttf` (aus Staging)
- Create: `$R/komga-font-<id>/src/main/assets/fonts.json`
- Create: `$R/komga-font-<id>/src/main/assets/OFL.txt` (aus Staging)
- Create: `$R/komga-font-<id>/README.md`
- Modify: `$R/settings.gradle.kts` (fünf `include`)

- [ ] **Step 1: settings.gradle.kts erweitern**

Die `include(...)`-Zeile um die fünf Module ergänzen (vorhandene Einträge behalten):
```kotlin
include(":komga-lang-es", ":komga-lang-fr", ":komga-lang-it", ":komga-reader-preset-eink", ":komga-panel-model-yolo", ":komga-font-ebgaramond", ":komga-font-lora", ":komga-font-merriweather", ":komga-font-sourceserif", ":komga-font-atkinson")
```

- [ ] **Step 2: Pro Modul `build.gradle.kts`** (Werte je `<id>`/Paket aus der Tabelle oben)

Beispiel `komga-font-ebgaramond/build.gradle.kts` (für die anderen analog mit ihrem `applicationId`/`namespace`):
```kotlin
plugins {
    id("com.android.application")
}
android {
    namespace = "com.komgareader.font.ebgaramond"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.komgareader.font.ebgaramond"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }
}
```
Namespaces/applicationIds: `com.komgareader.font.{ebgaramond,lora,merriweather,sourceserif,atkinson}`.

- [ ] **Step 3: Pro Modul `AndroidManifest.xml`** (`android:label` = der Anzeigename)

Beispiel `komga-font-ebgaramond/src/main/AndroidManifest.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application android:label="EB Garamond" android:hasCode="false">
        <meta-data android:name="com.komgareader.plugin.DATA_CATEGORY" android:value="FONT" />
        <meta-data android:name="com.komgareader.plugin.DATA_ASSET" android:value="fonts.json" />
        <meta-data android:name="com.komgareader.plugin.ABI_VERSION" android:value="2" />
        <meta-data android:name="com.komgareader.plugin.LICENSE" android:value="OFL-1.1" />
    </application>
</manifest>
```
`android:label` je: „EB Garamond" / „Lora" / „Merriweather" / „Source Serif 4" / „Atkinson Hyperlegible Next".

- [ ] **Step 4: Pro Modul TTF + OFL kopieren + `fonts.json`**

TTF + OFL aus Staging: `cp .fonts-staging/<id>/<Font>.ttf komga-font-<id>/src/main/assets/fonts/<Font>.ttf` und `cp .fonts-staging/<id>/OFL.txt komga-font-<id>/src/main/assets/OFL.txt`.
`fonts.json` (die `family` ist der **in Task 3 per fc-scan verifizierte** Name; `asset` der reale Dateiname; `label` der Anzeigename):
```json
[ { "family": "EB Garamond", "label": "EB Garamond", "asset": "fonts/EBGaramond.ttf", "license": "OFL-1.1" } ]
```
(Analog je Schrift mit ihrem verifizierten `family`/`asset`/`label`.)

- [ ] **Step 5: Pro Modul `README.md`** (nur Text — kein Bild; das Specimen kommt über `previewUrl`)

Beispiel `komga-font-ebgaramond/README.md`:
```markdown
# EB Garamond

A classic Garamond revival serif, well suited to long-form reading.

- **License:** SIL Open Font License 1.1 (OFL-1.1)
- **Upstream:** https://github.com/octaviopardo/EBGaramond12
- **Family name:** EB Garamond

Installs as a reading font for the novel/reflow reader. On E-Ink, larger sizes
keep the fine serifs crisp.
```
(Analog je Schrift: Name, 1-Satz-Beschreibung, Lizenz OFL-1.1, Upstream-Link, Familienname, kurze E-Ink-Notiz. Bei RFN-Schriften — Lora/Merriweather/Source Serif — KEINE Umbenennung, Originalname.)

- [ ] **Step 6: Build-Check (alle fünf Module kompilieren/assemblen)**

Run: `cd /home/gabriel/Documents/Projekte/KomgaReaderPlugins && ./gradlew :komga-font-ebgaramond:assembleDebug :komga-font-lora:assembleDebug :komga-font-merriweather:assembleDebug :komga-font-sourceserif:assembleDebug :komga-font-atkinson:assembleDebug -q 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL; je `komga-font-<id>/build/outputs/apk/debug/komga-font-<id>-debug.apk` existiert.

- [ ] **Step 7: Commit**

```bash
cd /home/gabriel/Documents/Projekte/KomgaReaderPlugins && git add settings.gradle.kts komga-font-ebgaramond komga-font-lora komga-font-merriweather komga-font-sourceserif komga-font-atkinson && git commit -m "feat(fonts): 5 data-only Font-Module (EB Garamond, Lora, Merriweather, Source Serif 4, Atkinson Hyperlegible Next)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 5: APKs nach plugins/ + Specimens generieren

Die fünf Debug-APKs ins `plugins/`-Verzeichnis mit dem Namens-Schema des Index legen, und je Schrift ein Specimen-PNG erzeugen.

**Files:**
- Create: `$R/plugins/komga-font-<id>-0.1.0.apk` (×5)
- Create: `$R/plugins/specimens/<id>.png` (×5)

- [ ] **Step 1: APKs kopieren/umbenennen**

Run (je Schrift): `cp komga-font-<id>/build/outputs/apk/debug/komga-font-<id>-debug.apk plugins/komga-font-<id>-0.1.0.apk` für alle fünf ids.

- [ ] **Step 2: APK-Sanity (Meta-data + Fingerprint)**

Je APK: `$HOME/Android/Sdk/build-tools/35.0.0/aapt2 dump xmltree --file AndroidManifest.xml plugins/komga-font-<id>-0.1.0.apk | grep -iE "DATA_CATEGORY|FONT|fonts.json|OFL|hasCode"` → muss DATA_CATEGORY=FONT, DATA_ASSET=fonts.json, LICENSE=OFL-1.1, hasCode=false zeigen.
Cert-SHA-256 prüfen (muss dem Standard-Debug-Fingerprint entsprechen): `$HOME/Android/Sdk/build-tools/35.0.0/apksigner verify --print-certs plugins/komga-font-<id>-0.1.0.apk | grep -i "SHA-256"` → `f416a7f7...068da` (= `F4:16:A7:...:DA`). Notieren.

- [ ] **Step 3: Specimens erzeugen**

Run: `mkdir -p plugins/specimens` dann je Schrift: `python3 tools/make_specimen.py komga-font-<id>/src/main/assets/fonts/<Font>.ttf "<Anzeigename>" plugins/specimens/<id>.png`.
Prüfen: je PNG existiert, ist nicht leer, Maße 1000×420 (`python3 -c "from PIL import Image; print(Image.open('plugins/specimens/<id>.png').size)"`). Stichprobe ein PNG visuell ansehen (Read-Tool auf die Datei), dass die Schrift wirklich in ihrem Schnitt + lesbar gerendert ist.

- [ ] **Step 4: Commit**

```bash
cd /home/gabriel/Documents/Projekte/KomgaReaderPlugins && git add plugins/komga-font-*-0.1.0.apk plugins/specimens && git commit -m "build(fonts): 5 Font-APKs + Specimen-Vorschaubilder nach plugins/

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 6: repo.json-Einträge + FONTS-PROVENANCE.md + README

Die fünf Schriften im Index registrieren (mit previewUrl/readmeUrl/license), Provenance dokumentieren, Repo-README ergänzen.

**Files:**
- Modify: `$R/repo.json`
- Create: `$R/FONTS-PROVENANCE.md`
- Modify: `$R/README.md`

- [ ] **Step 1: repo.json — fünf Font-Einträge ergänzen**

In `repo.json` `plugins[]` fünf Objekte ergänzen (Komma-Hygiene beachten; gültiges JSON). Muster je Schrift (Werte einsetzen; `fingerprint` = der in Task 5 bestätigte Standard-Debug-SHA-256 `F4:16:A7:F7:44:DE:08:44:8F:E9:99:1C:AC:DB:2A:19:7E:14:82:DA:55:AE:2C:18:5F:EC:C6:24:C6:C0:68:DA`):
```json
{ "packageName": "com.komgareader.font.ebgaramond", "name": "EB Garamond", "description": "Klassische Garamond-Serifenschrift fürs Lesen", "type": "font", "abiVersion": 2, "versionCode": 1, "versionName": "0.1.0", "apkUrl": "plugins/komga-font-ebgaramond-0.1.0.apk", "fingerprint": "F4:16:A7:F7:44:DE:08:44:8F:E9:99:1C:AC:DB:2A:19:7E:14:82:DA:55:AE:2C:18:5F:EC:C6:24:C6:C0:68:DA", "previewUrl": "plugins/specimens/ebgaramond.png", "readmeUrl": "komga-font-ebgaramond/README.md", "license": "OFL-1.1" }
```
Analog für `lora` („Lora-Serifenschrift, moderater Kontrast"), `merriweather` („Merriweather-Serifenschrift, sehr gut lesbar"), `sourceserif` („Source Serif 4 von Adobe"), `atkinson` („Atkinson Hyperlegible Next — auf Lesbarkeit optimiert").
Validieren: `python3 -c "import json; json.load(open('repo.json'))"` → kein Fehler.

- [ ] **Step 2: FONTS-PROVENANCE.md** (data-provenance-Pflichtfelder je Schrift)

Create `$R/FONTS-PROVENANCE.md` mit einer Tabelle/Sektion je Schrift: Name · Upstream-URL (Permalink) · SPDX-Lizenz (OFL-1.1) · Umfang (eine Regular-STATIC-TTF) · Filter/Verarbeitung (STATIC-Auswahl bzw. `fontTools`-Instanzierung falls genutzt) · Erfassungsdatum (2026-06-14) · Commit/Release-Pin · verifizierter Familienname (aus `families.txt`) · Risk-Notiz (RFN bei Lora/Merriweather/Source Serif → kein Derivat/keine Umbenennung; OFL koexistiert mit AGPL, Font = separates Werk). Abschlusszeile `Letzte Komplettrevision: 2026-06-14 (P3 Font-Plugins)`.

- [ ] **Step 3: README.md — Font-Kategorie ergänzen**

In `$R/README.md` einen kurzen Abschnitt ergänzen, dass das Repo jetzt **Font-Plugins** (`type: font`, OFL-1.1, data-only) enthält und wie sie gebaut werden (`./gradlew :komga-font-<id>:assembleDebug`, `tools/make_specimen.py`). Englisch.

- [ ] **Step 4: Commit**

```bash
cd /home/gabriel/Documents/Projekte/KomgaReaderPlugins && git add repo.json FONTS-PROVENANCE.md README.md && git commit -m "docs(repo): 5 Font-Einträge in repo.json + FONTS-PROVENANCE + README

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 7: E2E auf dem Emulator (echter Repo-Browser-Pfad)

Beweisen, dass die ganze Kette läuft: Repo-Browser zeigt eine Font-Zeile mit Info-Modal (Vorschau + README), Install (Fingerprint + Lizenz-Gate OFL-1.1 erlaubt), Picker zeigt die Schrift mit Live-Sample, Novel-Reader rendert darin. **Nur `emulator-5554`** — physische Boox `db4c96d` NIE ohne explizites OK.

**Files:** keine (Verifikation).

- [ ] **Step 1: App von main auf den Emulator bringen**

Die aktuelle App (main = mit P1/P2) bauen + NUR auf den Emulator installieren (nie `installDebug` bei angeschlossener Boox):
Run: `cd /home/gabriel/Documents/Projekte/komga-reader-main && ./gradlew :app:assembleDebug -q && adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk`
(Bei versionCode-Downgrade-Block: `adb -s emulator-5554 uninstall com.komgareader.app` vorher.)

- [ ] **Step 2: Lokalen Repo-Server starten (kein GitHub-Push nötig)**

Run (Hintergrund): `cd /home/gabriel/Documents/Projekte/KomgaReaderPlugins && python3 -m http.server 8077 >/tmp/komga-repo-http.log 2>&1 &`
Emulator erreicht den Host über `10.0.2.2`. Repo-URL für die App: `http://10.0.2.2:8077/repo.json`.
Run: `curl -s http://localhost:8077/repo.json | python3 -c "import json,sys; d=json.load(sys.stdin); print([p['packageName'] for p in d['plugins'] if p['type']=='font'])"` → muss die fünf Font-Pakete listen.

- [ ] **Step 3: Repo in der App hinzufügen + Font-Zeile prüfen**

App starten, Plugins-Tab → Repo-Settings (⚙) → Repo-URL `http://10.0.2.2:8077/repo.json` hinzufügen → Reload (⟳). Filter „Schriften". Eine Font-Zeile (z. B. „EB Garamond") muss in „Verfügbar" erscheinen. ℹ-Info-Modal öffnen → Vorschau-Specimen-Bild + gerendertes README + Lizenz „OFL-1.1" sichtbar (Screenshot `adb -s emulator-5554 exec-out screencap -p > /tmp/p3-info-modal.png`).
(UI-Navigation via `adb -s emulator-5554 shell uiautomator dump` + `input tap` wie in P2; Notification-Permission-Dialog ggf. mit „Don't allow" wegklicken.)

- [ ] **Step 4: Installieren + Picker + Reader**

Eine Schrift über das ℹ/Download-Control installieren (PackageInstaller-OS-Dialog bestätigen). Nach Install: Settings → Reader → Schriftart-Picker zeigt die Schrift mit **Live-Sample im echten Schnitt**; auswählen; einen NOVEL-Titel öffnen → Reflow rendert in der Schrift (Screenshots `/tmp/p3-picker.png`, `/tmp/p3-reader.png`). Lizenz-Gate-Beweis: OFL-1.1 wurde NICHT geblockt (Install + Registrierung erfolgten).
Wenn kein NOVEL-Inhalt verfügbar: mindestens Picker + Live-Sample zeigen; den Render-Schritt als „Picker zeigt Schrift, Reader-Render = P2-bewährt" vermerken.

- [ ] **Step 5: Aufräumen + Beleg**

HTTP-Server stoppen (`kill %1` bzw. den python-Prozess). Test-Font-Plugin vom Emulator deinstallieren (`adb -s emulator-5554 uninstall com.komgareader.font.<id>`). Screenshots als Beleg behalten. Bestätigen, dass `db4c96d` nie berührt wurde (`adb -s db4c96d shell pm list packages | grep komgareader.font` → leer). Kein Commit.

---

## Task 8: Doku-/Memory-Sync + Abschluss

**Files:**
- Modify (App-Repo Worktree `font-plugins-p3`): ggf. `docs/PROJECT-STATUS.md` (Font-Plugins jetzt verteilt), `README.md` falls es Plugin-Typen listet.
- Memory (über Memory-Tool, nicht im Repo): `font-plugins-research`, `data-plugin-distribution` auf „P3 fertig".

- [ ] **Step 1: komga-doc-sync prüfen (App-Repo)**

P3 änderte **keinen** App-Code, nur Distributions-Inhalte. Prüfen, ob eine App-Doku Plugin-Typen/Fonts auflistet, die „verteilte Font-Plugins existieren jetzt" erwähnen sollte (`docs/PROJECT-STATUS.md`). Falls ja, knapp ergänzen (Englisch). Falls nein, nichts tun. Im App-Repo-Worktree `/home/gabriel/Documents/Projekte/komga-reader/.claude/worktrees/font-plugins-p3` committen:
```bash
git -C /home/gabriel/Documents/Projekte/komga-reader/.claude/worktrees/font-plugins-p3 add -A && git -C /home/gabriel/Documents/Projekte/komga-reader/.claude/worktrees/font-plugins-p3 commit -m "docs: verteilte Font-Plugins (P3) erwähnen

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```
(Wenn keine App-Doku betroffen ist, diesen Commit überspringen.)

- [ ] **Step 2: Provenance-/Lizenz-Gegencheck**

Verifizieren: je Modul liegt `assets/OFL.txt` im APK (Task 5 aapt-Dump bzw. `unzip -l plugins/komga-font-<id>-0.1.0.apk | grep OFL`), `FONTS-PROVENANCE.md` deckt alle fünf ab, RFN-Schriften behalten Originalnamen. Im Report bestätigen.

- [ ] **Step 3: Verifikations-Notiz**

E2E-Ergebnis (Screenshots, Fingerprint-Match, Gate-erlaubt) zusammenfassen. Kein weiterer Commit.

---

## Abschluss

Nach Task 8: `superpowers:finishing-a-development-branch` für **beide** Repos getrennt:
- **KomgaReaderPlugins** (`feat/font-plugins`): Merge nach `main` + **Push** (GitHub) ist hier sinnvoll, damit `PluginRepoDefaults.OFFICIAL_URL` die Fonts ausliefert — Push aber nur mit explizitem User-OK (outward action). Bei der ausstehenden Aurora-WIP (Task 1) im Hinterkopf behalten, dass sie als eigener Commit drin ist.
- **komga-reader** (`feat/font-plugins-p3-fonts`): nur Spec/Plan(/ggf. Doku) — lokaler Merge nach `main` wie bei P2 (kein Push, `origin` ist die fremde Linie / Multi-Session). 
Memory-Updates (`font-plugins-research`, `data-plugin-distribution`) separat über das Memory-Tool.
