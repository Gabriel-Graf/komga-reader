# Phase 1 · Plan 4b/… — render-core (MuPDF) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: subagent-driven-development / executing-plans. Checkbox steps.

**Goal:** Ein `:render-core`-Android-Modul, das die Domain-Naht `Document`/`DocumentFactory` mit MuPDF implementiert: Bytes eines cbz/cbr/pdf/epub → gerenderte Seite (`RenderedPage`, ARGB-Pixel). Verifiziert durch einen **Instrumented-Test auf dem Emulator**, der echte Fixtures aller drei Formate rendert.

**Architecture:** MuPDF kommt als offizieller Prebuilt-AAR `com.artifex.mupdf:fitz:1.27.1` (von `https://maven.ghostscript.com`, AGPL, alle ABIs, NDK-fertig — kein eigener NDK-Build). `MupdfDocument` implementiert das Android-freie `Document`-Interface aus `:domain`: rendert via `AndroidDrawDevice.drawPage(page, dpi) → android.graphics.Bitmap`, extrahiert die Pixel in ein `IntArray` und gibt sie als `RenderedPage` zurück — so bleibt das Domain-Modell frei von `Bitmap`. Die Engine-Eignung ist bereits durch den Host-Spike (Plan 1.2) bewiesen; hier geht es nur um die Android-Integration.

**Tech Stack:** Android-Library (AGP 8.7.2) · MuPDF fitz 1.27.1 · `:domain` · androidx.test (Instrumented).

## Render-API (MuPDF fitz, verifiziert)
- `com.artifex.mupdf.fitz.Document.openDocument(bytes: ByteArray, magic: String)` — `magic` = Dateiendungs-Hint (`.cbz`/`.cbr`/`.pdf`/`.epub`).
- `doc.countPages(): Int`, `doc.loadPage(i): Page`, `page.getBounds(): Rect`.
- `com.artifex.mupdf.fitz.android.AndroidDrawDevice.drawPage(page, dpi: Float, rotate: Int): Bitmap` (ARGB_8888). `dpi = 72 * zoom` (72dpi = 1:1).
- Alle nativen Objekte (`Document`, `Page`) brauchen explizites `destroy()`.

## Emulator
AVD `eink_test` (Android 14, x86_64) ist gebootet (`adb devices` → `emulator-5554 device`). Instrumented-Tests laufen via `./gradlew :render-core:connectedDebugAndroidTest`.

---

### Task 0: Modul `:render-core` + MuPDF-Repo/Dependency

**Files:** Modify `settings.gradle.kts`, `gradle/libs.versions.toml`; Create `render-core/build.gradle.kts`, `render-core/src/main/AndroidManifest.xml`.

- [ ] **Step 1: MuPDF-Repo in settings.gradle.kts**

In `settings.gradle.kts`, im `dependencyResolutionManagement { repositories { ... } }`-Block ergänzen (vor `google()`):
```kotlin
        maven { url = uri("https://maven.ghostscript.com") }
```
Und `include(":render-core")` ergänzen.

- [ ] **Step 2: Version-Catalog**

In `gradle/libs.versions.toml` unter `[versions]`: `mupdf = "1.27.1"`, `androidxTestRunner = "1.6.2"`, `androidxTestExt = "1.2.1"`.
Unter `[libraries]`:
```toml
mupdf-fitz = { module = "com.artifex.mupdf:fitz", version.ref = "mupdf" }
androidx-test-runner = { module = "androidx.test:runner", version.ref = "androidxTestRunner" }
androidx-test-ext-junit = { module = "androidx.test.ext:junit", version.ref = "androidxTestExt" }
```

- [ ] **Step 3: Modul-Build**

Create `render-core/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.komgareader.render"
    compileSdk = 34
    defaultConfig {
        minSdk = 28
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":domain"))
    implementation(libs.mupdf.fitz)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
```

Add `android-library` plugin to `gradle/libs.versions.toml` `[plugins]` if missing:
```toml
android-library = { id = "com.android.library", version.ref = "agp" }
```

Create `render-core/src/main/AndroidManifest.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest />
```

- [ ] **Step 4: Build verifizieren** — `./gradlew :render-core:assembleDebug` → BUILD SUCCESSFUL (lädt den 20 MB AAR).

- [ ] **Step 5: Commit** — `git add settings.gradle.kts gradle/libs.versions.toml render-core/build.gradle.kts render-core/src/main/AndroidManifest.xml && git commit -m "build: :render-core mit MuPDF fitz 1.27.1 (Prebuilt-AAR)"`

---

### Task 1: Domain — `DocumentFactory.open` um Format-Hint erweitern

Das Domain-Interface braucht den Format-Hint, damit der Renderer cbz vs. epub (beide ZIP) unterscheiden kann.

**Files:** Modify `domain/src/main/kotlin/com/komgareader/domain/render/Document.kt`.

- [ ] **Step 1: Signatur ändern**

In `Document.kt` die `DocumentFactory` ersetzen:
```kotlin
/** Öffnet ein Dokument aus rohen Bytes. [formatHint] = Dateiendung (z.B. ".cbz"). */
interface DocumentFactory {
    fun open(bytes: ByteArray, formatHint: String): Document
}
```

- [ ] **Step 2: Domain-Tests grün halten** — `./gradlew :domain:test` → 17 grün (keine Implementierung betroffen). Commit: `git add domain/src/main/kotlin/com/komgareader/domain/render/Document.kt && git commit -m "refactor(domain): DocumentFactory.open mit Format-Hint"`

---

### Task 2: MupdfDocument + MupdfDocumentFactory

**Files:** Create `render-core/src/main/kotlin/com/komgareader/render/mupdf/MupdfDocument.kt`.

- [ ] **Step 1: Implementieren**

Create `MupdfDocument.kt`:
```kotlin
package com.komgareader.render.mupdf

import com.artifex.mupdf.fitz.android.AndroidDrawDevice
import com.komgareader.domain.render.Document
import com.komgareader.domain.render.DocumentFactory
import com.komgareader.domain.render.PageSize
import com.komgareader.domain.render.RenderedPage
import com.artifex.mupdf.fitz.Document as FitzDocument

/**
 * MuPDF-Implementierung der Render-Naht. Hält ein natives Fitz-Dokument; rendert
 * Seiten in eine Android-Bitmap und extrahiert die Pixel in ein [RenderedPage],
 * damit das Domain-Modell Android-frei bleibt. Nicht thread-sicher — pro Lesefluss
 * eine Instanz; [close] gibt die nativen Ressourcen frei.
 */
class MupdfDocument(bytes: ByteArray, formatHint: String) : Document {

    private val doc: FitzDocument = FitzDocument.openDocument(bytes, formatHint)

    override fun pageCount(): Int = doc.countPages()

    override fun pageSize(index: Int): PageSize {
        val page = doc.loadPage(index)
        try {
            val b = page.bounds
            return PageSize(width = (b.x1 - b.x0).toInt(), height = (b.y1 - b.y0).toInt())
        } finally {
            page.destroy()
        }
    }

    override fun renderPage(index: Int, zoom: Float, rotation: Int): RenderedPage {
        val page = doc.loadPage(index)
        try {
            val bitmap = AndroidDrawDevice.drawPage(page, 72f * zoom, rotation)
            try {
                val w = bitmap.width
                val h = bitmap.height
                val pixels = IntArray(w * h)
                bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
                return RenderedPage(width = w, height = h, pixels = pixels)
            } finally {
                bitmap.recycle()
            }
        } finally {
            page.destroy()
        }
    }

    override fun close() = doc.destroy()
}

/** Öffnet MuPDF-Dokumente aus Bytes. */
class MupdfDocumentFactory : DocumentFactory {
    override fun open(bytes: ByteArray, formatHint: String): Document =
        MupdfDocument(bytes, formatHint)
}
```

- [ ] **Step 2: Kompilieren** — `./gradlew :render-core:compileDebugKotlin` → SUCCESSFUL. Commit: `git add render-core/src/main/kotlin/ && git commit -m "feat(render): MupdfDocument + Factory (Document-Naht)"`

---

### Task 3: Instrumented-E2E auf dem Emulator (rendert alle 3 Formate)

**Files:**
- Create: `tools/spikes/mupdf/make_fixtures.py`
- Create (generiert): `render-core/src/androidTest/assets/sample.cbz`, `sample.pdf`, `sample.epub`
- Create: `render-core/src/androidTest/kotlin/com/komgareader/render/mupdf/MupdfRenderInstrumentedTest.kt`

- [ ] **Step 1: Fixture-Generator**

Create `tools/spikes/mupdf/make_fixtures.py` — schreibt die drei Test-Dateien in ein Zielverzeichnis. Wiederverwende die Bild-/CBZ-/PDF-/EPUB-Erzeugung aus `render_spike.py` (gleiche Logik), aber ohne Rendern/Löschen. Signatur: `python3 make_fixtures.py <zielordner>` erzeugt `sample.cbz`, `sample.pdf`, `sample.epub`.
```python
#!/usr/bin/env python3
"""Erzeugt CBZ/PDF/EPUB-Test-Fixtures in <zielordner> (für Instrumented-Tests)."""
import io, sys, zipfile
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

PAGE_W, PAGE_H = 800, 1200


def _page(label: str, n: int) -> Image.Image:
    img = Image.new("RGB", (PAGE_W, PAGE_H), "white")
    d = ImageDraw.Draw(img)
    d.rectangle([10, 10, PAGE_W - 10, PAGE_H - 10], outline="black", width=6)
    d.rectangle([40, 40, PAGE_W - 40, 160], fill="black")
    for i in range(6):
        y = 220 + i * 150
        d.rectangle([60, y, PAGE_W - 60, y + 90], fill=(20, 20, 20))
    d.text((60, 90), f"{label} Seite {n}", fill="white", font=ImageFont.load_default())
    return img


def make_cbz(p: Path):
    with zipfile.ZipFile(p, "w", zipfile.ZIP_DEFLATED) as z:
        for n in (1, 2):
            buf = io.BytesIO(); _page("CBZ", n).save(buf, "PNG"); z.writestr(f"p{n:02d}.png", buf.getvalue())


def make_pdf(p: Path):
    _page("PDF", 1).save(p, "PDF", save_all=True, append_images=[_page("PDF", 2)], resolution=72.0)


def make_epub(p: Path):
    container = '<?xml version="1.0"?>\n<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>'
    opf = '<?xml version="1.0" encoding="utf-8"?>\n<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="b"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="b">urn:uuid:fx</dc:identifier><dc:title>FX</dc:title><dc:language>de</dc:language></metadata><manifest><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/><item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="c1"/></spine></package>'
    nav = '<?xml version="1.0" encoding="utf-8"?>\n<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>n</title></head><body><nav epub:type="toc"><ol><li><a href="c1.xhtml">K1</a></li></ol></nav></body></html>'
    para = ("Die Sonne ging über dem zerfallenen Turm auf. " * 14)
    c1 = f'<?xml version="1.0" encoding="utf-8"?>\n<html xmlns="http://www.w3.org/1999/xhtml"><head><title>K1</title></head><body><h1>Kapitel 1</h1><p>{para}</p><p>{para}</p></body></html>'
    with zipfile.ZipFile(p, "w") as z:
        zi = zipfile.ZipInfo("mimetype"); zi.compress_type = zipfile.ZIP_STORED
        z.writestr(zi, "application/epub+zip")
        z.writestr("META-INF/container.xml", container)
        z.writestr("OEBPS/content.opf", opf)
        z.writestr("OEBPS/nav.xhtml", nav)
        z.writestr("OEBPS/c1.xhtml", c1)


def main() -> int:
    out = Path(sys.argv[1]); out.mkdir(parents=True, exist_ok=True)
    make_cbz(out / "sample.cbz"); make_pdf(out / "sample.pdf"); make_epub(out / "sample.epub")
    print(f"Fixtures in {out}: {[p.name for p in out.iterdir()]}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
```

- [ ] **Step 2: Fixtures generieren**
Run: `python3 tools/spikes/mupdf/make_fixtures.py render-core/src/androidTest/assets`
Expected: erzeugt `sample.cbz`, `sample.pdf`, `sample.epub` im Asset-Ordner. (Diese Binärdateien werden mit eingecheckt.)

- [ ] **Step 3: Instrumented-Test**

Create `render-core/src/androidTest/kotlin/com/komgareader/render/mupdf/MupdfRenderInstrumentedTest.kt`:
```kotlin
package com.komgareader.render.mupdf

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * E2E auf dem Gerät: rendert je eine echte Fixture pro Format mit MuPDF und prüft,
 * dass Seite 0 plausible Maße hat und nicht leer ist (dunkle Pixel vorhanden).
 */
@RunWith(AndroidJUnit4::class)
class MupdfRenderInstrumentedTest {

    private val factory = MupdfDocumentFactory()

    private fun assetBytes(name: String): ByteArray =
        InstrumentationRegistry.getInstrumentation().context.assets.open(name).use { it.readBytes() }

    private fun renderFirstPage(asset: String, hint: String) {
        factory.open(assetBytes(asset), hint).use { doc ->
            assertTrue("Seitenzahl > 0 für $asset", doc.pageCount() > 0)
            val page = doc.renderPage(index = 0, zoom = 2f, rotation = 0)
            assertTrue("Breite > 100 für $asset", page.width > 100)
            assertTrue("Höhe > 100 für $asset", page.height > 100)
            val darkPixels = page.pixels.count { argb ->
                val r = (argb shr 16) and 0xff; val g = (argb shr 8) and 0xff; val b = argb and 0xff
                (r + g + b) / 3 < 80
            }
            assertTrue("nicht leer (dunkle Pixel) für $asset, war $darkPixels", darkPixels > 300)
        }
    }

    @Test fun rendert_cbz() = renderFirstPage("sample.cbz", ".cbz")
    @Test fun rendert_pdf() = renderFirstPage("sample.pdf", ".pdf")
    @Test fun rendert_epub() = renderFirstPage("sample.epub", ".epub")
}
```

- [ ] **Step 4: Instrumented-Test auf dem Emulator ausführen**
Run: `./gradlew :render-core:connectedDebugAndroidTest`
Expected: BUILD SUCCESSFUL, 3 Tests grün (cbz/pdf/epub gerendert). Ergebnis-HTML unter `render-core/build/reports/androidTests/connected/`.
Falls ein Test fehlschlägt: Logcat/Stacktrace prüfen — häufige Ursachen: falscher `formatHint`, fehlendes `libc++_shared` (sollte der AAR mitbringen), Asset nicht gefunden.

- [ ] **Step 5: Commit**
```bash
git add tools/spikes/mupdf/make_fixtures.py render-core/src/androidTest/
git commit -m "test(render): Instrumented-E2E rendert CBZ/PDF/EPUB auf Emulator"
```

---

## Self-Review-Notiz (Autor)
- **Spec-Abdeckung:** §6 Naht B (MuPDF→Bitmap, `Document`-Impl) → Tasks 0,2; Format-Hint für cbz/epub-Disambiguierung → Task 1; reale Geräte-Verifikation → Task 3.
- **Bewusst verschoben:** Thread-Sicherheit/Locking des nativen Kontexts (Reader nutzt eine Instanz pro Lesefluss; echtes Prefetch-Threading kommt mit dem PageCache in 1.4e), Reflow-Feintuning EPUB, RefreshScheduler-Anbindung (1.4e). `pageSize` liefert Punkt-Bounds gerundet — für Layout grob ausreichend, Feinheiten später.
- **Risiko/abgesichert:** MuPDF-Engine-Eignung bereits in 1.2 bewiesen; hier nur Android-Integration. AAR bringt `.so` für x86_64 (Emulator) und arm64-v8a (Boox) mit → läuft auf beidem.
