# Novel Auto-Hyphenation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the novel reader auto-select the hyphenation pattern from the EPUB's own language tag via a new "auto" setting value, instead of requiring a manual German/English choice.

**Architecture:** Surface the document language crengine already knows (`LVDocView::getLanguage()`) through a new JNI call → a default-`""` `ReflowableDocument.contentLanguage()`. A pure `resolveHyphenationLang(setting, docLang)` maps the new `"auto"` sentinel to a supported pattern (else off). `NovelReaderViewModel` feeds the document language into its existing `ReflowConfig` flow so "auto" resolves once the book is open. Settings gains an "Automatic" option.

**Tech Stack:** Kotlin, JNI/C++ (crengine-ng `cr3_bridge.cpp`), Jetpack Compose, Hilt, JUnit.

**Spec:** `docs/superpowers/specs/2026-06-14-novel-auto-hyphenation-design.md`

---

## Notes for the implementer

- crengine-ng's `LVDocView::getLanguage()` exists (`native/prefix/.../lvdocview.h`, reads `DOC_PROP_LANGUAGE` from EPUB `dc:language`/`xml:lang`) but is **not** wrapped in JNI today. Tasks 1-2 wrap it.
- Supported hyphenation patterns today (`ReflowCss.PATTERN_DICTS`): `de`, `en`. "auto" only ever resolves to one of these or off.
- The native JNI task requires the NDK build; it is verified by build + on-device, not by a JVM unit test.

---

## Task 1: JNI `nativeLanguage`

**Files:**
- Modify: `render-crengine/src/main/cpp/cr3_bridge.cpp` (near `nativeTitle`/`nativeAuthors`, ~lines 318-334)
- Modify: `render-crengine/src/main/kotlin/com/komgareader/render/crengine/CrengineNative.kt` (add `external fun`, near `nativeTitle`/`nativeAuthors` ~lines 76-79)

- [ ] **Step 1: Add the C++ JNI function**

Mirror the existing `nativeTitle` function exactly (same handle cast, same `lString32` → `jstring` conversion helper it uses). Add after `nativeAuthors`:

```cpp
extern "C" JNIEXPORT jstring JNICALL
Java_com_komgareader_render_crengine_CrengineNative_nativeLanguage(
        JNIEnv* env, jobject /*thiz*/, jlong handle) {
    auto* view = reinterpret_cast<LVDocView*>(handle);
    if (view == nullptr) return env->NewStringUTF("");
    lString32 lang = view->getLanguage();
    // Reuse the same UTF-8 conversion path as nativeTitle (e.g. UnicodeToUtf8 + NewStringUTF).
    return env->NewStringUTF(UnicodeToUtf8(lang).c_str());
}
```

Match the exact conversion idiom used by `nativeTitle` in this file (if it uses a helper like `toJavaString(env, ...)`, use that instead of the inline conversion above).

- [ ] **Step 2: Add the Kotlin external declaration** in `CrengineNative.kt`, next to `nativeTitle`:

```kotlin
external fun nativeLanguage(handle: Long): String
```

- [ ] **Step 3: Build the native module**

Run: `./gradlew :render-crengine:compileDebugKotlin :render-crengine:externalNativeBuildDebug`
Expected: BUILD SUCCESSFUL (JNI symbol compiles and links).

- [ ] **Step 4: Commit**

```bash
git add render-crengine/src/main/cpp/cr3_bridge.cpp render-crengine/src/main/kotlin/com/komgareader/render/crengine/CrengineNative.kt
git commit -m "feat(crengine): JNI nativeLanguage wrapping LVDocView::getLanguage"
```

---

## Task 2: `ReflowableDocument.contentLanguage()`

**Files:**
- Modify: `domain/src/main/kotlin/com/komgareader/domain/render/Document.kt` (`ReflowableDocument` interface, near `title()`/`authors()` ~lines 68-70)
- Modify: `render-crengine/src/main/kotlin/com/komgareader/render/crengine/CrengineDocument.kt` (implement, near `title()`/`authors()` ~line 96)

- [ ] **Step 1: Add the interface method with a safe default**

In `Document.kt`, inside `ReflowableDocument`:

```kotlin
    /** BCP-47 content language from the document metadata ("" if unknown). Engine-neutral default. */
    fun contentLanguage(): String = ""
```

The `= ""` default means non-crengine factories need no change (domain stays engine-free).

- [ ] **Step 2: Implement in `CrengineDocument`** (mirror `title()`):

```kotlin
    override fun contentLanguage(): String = CrengineNative.nativeLanguage(handle)
```

Use the same `handle` field name the existing `title()` uses.

- [ ] **Step 3: Compile**

Run: `./gradlew :domain:compileDebugKotlin :render-crengine:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add domain/src/main/kotlin/com/komgareader/domain/render/Document.kt render-crengine/src/main/kotlin/com/komgareader/render/crengine/CrengineDocument.kt
git commit -m "feat(render): expose document content language via ReflowableDocument"
```

---

## Task 3: Pure `resolveHyphenationLang`

**Files:**
- Create: `domain/src/main/kotlin/com/komgareader/domain/render/HyphenationResolver.kt`
- Test: `domain/src/test/kotlin/com/komgareader/domain/render/HyphenationResolverTest.kt` (create)

- [ ] **Step 1: Write the failing test**

```kotlin
package com.komgareader.domain.render

import org.junit.Assert.assertEquals
import org.junit.Test

class HyphenationResolverTest {
    @Test fun `auto with german document resolves to de`() =
        assertEquals("de", resolveHyphenationLang(setting = "auto", docLanguage = "de"))

    @Test fun `auto normalizes a region tag`() =
        assertEquals("de", resolveHyphenationLang(setting = "auto", docLanguage = "de-DE"))

    @Test fun `auto with english resolves to en`() =
        assertEquals("en", resolveHyphenationLang(setting = "auto", docLanguage = "en-US"))

    @Test fun `auto with unsupported language falls back to off`() =
        assertEquals("", resolveHyphenationLang(setting = "auto", docLanguage = "fr"))

    @Test fun `auto with unknown document language is off`() =
        assertEquals("", resolveHyphenationLang(setting = "auto", docLanguage = ""))

    @Test fun `explicit de wins regardless of document`() =
        assertEquals("de", resolveHyphenationLang(setting = "de", docLanguage = "en"))

    @Test fun `off stays off`() =
        assertEquals("", resolveHyphenationLang(setting = "", docLanguage = "de"))
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :domain:testDebugUnitTest --tests "com.komgareader.domain.render.HyphenationResolverTest"`
Expected: FAIL — `resolveHyphenationLang` unresolved.

- [ ] **Step 3: Implement**

```kotlin
package com.komgareader.domain.render

/** Hyphenation languages with a real pattern dictionary (mirrors render layer's PATTERN_DICTS). */
private val SUPPORTED_HYPHENATION = setOf("de", "en")

/** Sentinel meaning "derive the language from the document". */
const val HYPHENATION_AUTO = "auto"

/**
 * Resolves the effective hyphenation language. For [HYPHENATION_AUTO], normalizes the document
 * language (e.g. "de-DE" -> "de") and returns it only if a pattern exists, else "" (off).
 * Any explicit value ("", "de", "en") is returned unchanged. Pure.
 */
fun resolveHyphenationLang(setting: String, docLanguage: String): String {
    if (setting != HYPHENATION_AUTO) return setting
    val base = docLanguage.substringBefore('-').lowercase()
    return if (base in SUPPORTED_HYPHENATION) base else ""
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :domain:testDebugUnitTest --tests "com.komgareader.domain.render.HyphenationResolverTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add domain/src/main/kotlin/com/komgareader/domain/render/HyphenationResolver.kt domain/src/test/kotlin/com/komgareader/domain/render/HyphenationResolverTest.kt
git commit -m "feat(render): pure resolveHyphenationLang for auto detection"
```

---

## Task 4: Feed document language into the reflow config

`NovelReaderViewModel` builds `reflowConfig` from settings flows before the book is open. Add the document language as state, set it after `open()`, and include it in the combine so "auto" resolves to a concrete language.

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/reader/NovelReaderViewModel.kt:93-113` (combine), and the `open()` body (~line 211 where `applyLayout` runs)

- [ ] **Step 1: Add a document-language StateFlow**

Near the other private state flows in `NovelReaderViewModel`:

```kotlin
private val _docLanguage = MutableStateFlow("")
```

(Import `kotlinx.coroutines.flow.MutableStateFlow` if missing.)

- [ ] **Step 2: Set it after the document opens**

In `open()`, right after the document is created and before/after `it.applyLayout(initialConfig)` inside the `documentMutex.withLock` block:

```kotlin
_docLanguage.value = it.contentLanguage()
```

- [ ] **Step 3: Include it in the reflowConfig combine and resolve auto**

Replace the inner combine that produces the `Triple(align, hyph, weight)` so it also carries the resolved hyphenation. The current inner combine is:

```kotlin
    combine(
        settings.novelTextAlign,
        settings.novelHyphenationLang,
        settings.novelFontWeight,
    ) { align, hyph, weight -> Triple(align, hyph, weight) },
```

Change to resolve against the document language:

```kotlin
    combine(
        settings.novelTextAlign,
        settings.novelHyphenationLang,
        settings.novelFontWeight,
        _docLanguage,
    ) { align, hyph, weight, docLang ->
        Triple(align, resolveHyphenationLang(hyph, docLang), weight)
    },
```

The outer combine already passes `alignHyphWeight.second` into `NovelSettings(hyphenationLang = ...)` — it now receives the resolved language, so `NovelSettings.toReflowConfig()` works unchanged. Add the import `com.komgareader.domain.render.resolveHyphenationLang`.

> `combine` with 4 flows is supported (typed overloads go up to 5). No nesting change needed beyond adding the 4th argument.

- [ ] **Step 4: Compile + run existing novel VM tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.komgareader.app.ui.reader.*Novel*" :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL; existing tests green (explicit de/en/off behavior unchanged because `resolveHyphenationLang` returns those values as-is).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/reader/NovelReaderViewModel.kt
git commit -m "feat(novel): resolve auto hyphenation from document language"
```

---

## Task 5: Settings + in-reader "Automatic" option + i18n

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/i18n/Strings.kt` (interface ~line 349-352, DE ~727-730, EN ~1096-1099)
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/settings/SettingsContent.kt:713-723` (SegmentedChoiceRow)
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/reader/NovelTypographyControls.kt:122-140` (ChoiceRows)

- [ ] **Step 1: Add the i18n key**

In `Strings.kt` interface (with the other `novelHyphenation*`):

```kotlin
    val novelHyphenationAuto: String
```

In `StringsDe`:

```kotlin
    override val novelHyphenationAuto = "Automatisch"
```

In `StringsEn`:

```kotlin
    override val novelHyphenationAuto = "Automatic"
```

- [ ] **Step 2: Add the option to the settings picker** — in `SettingsContent.kt`, add an "auto" segment first (so it reads Auto / Off / German / English):

```kotlin
        options = listOf(
            SegmentOption("auto", s.novelHyphenationAuto),
            SegmentOption("", s.novelHyphenationOff),
            SegmentOption("de", s.novelHyphenationDe),
            SegmentOption("en", s.novelHyphenationEn),
        ),
```

- [ ] **Step 3: Add the option to the in-reader panel** — in `NovelTypographyControls.kt`, add before the "Off" `ChoiceRow`:

```kotlin
ChoiceRow(
    label = strings.novelHyphenationAuto,
    selected = hyphenationLang == "auto",
    dense = true,
    onSelect = { onHyphenation("auto") },
)
```

- [ ] **Step 4: Compile** (this also enforces i18n DE/EN parity at compile time)

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (fails if either language is missing the key).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/i18n/Strings.kt app/src/main/kotlin/com/komgareader/app/ui/settings/SettingsContent.kt app/src/main/kotlin/com/komgareader/app/ui/reader/NovelTypographyControls.kt
git commit -m "feat(novel): expose Automatic hyphenation option (de+en)"
```

---

## Task 6: Docs sync + verification

**Files:**
- Modify: `.claude/rules/architecture-seams.md` (Naht B: new `contentLanguage()` on `ReflowableDocument` + JNI `nativeLanguage`)

- [ ] **Step 1: Update the seam doc** — add a dated (2026-06-15) note under Naht B render seam: `ReflowableDocument.contentLanguage()` (default `""`) surfaces the EPUB `dc:language` via new JNI `nativeLanguage` (wraps `LVDocView::getLanguage`); the novel reader's `"auto"` hyphenation resolves it through the pure `resolveHyphenationLang`.

- [ ] **Step 2: Commit**

```bash
git add .claude/rules/architecture-seams.md
git commit -m "docs: sync novel auto-hyphenation seam change (Naht B)"
```

- [ ] **Step 3: Full unit suite**

Run: `./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all green.

- [ ] **Step 4: Emulator E2E**

On the `eink_test` emulator, open an EPUB whose `dc:language` is German with the hyphenation setting on "Automatic"; verify German hyphenation is applied (compare with an English-language EPUB → English patterns; an unsupported-language EPUB → no hyphenation). Note: the crengine `.so` must be present for the test architecture; if x86_64 lacks it (per project notes), verify on the arm64 Boox instead.

---

## Self-Review Coverage

- "auto" setting value + sentinel: Task 3 ✓
- Document language exposed (JNI + interface, default ""): Tasks 1,2 ✓
- Pure resolution (auto/de/en/off × doc langs): Task 3 ✓
- Wiring into reflow, explicit values unchanged: Task 4 ✓
- Settings + in-reader UI + i18n parity: Task 5 ✓
- Unsupported language → off: Task 3 test + Task 6.4 ✓
- docs-match-code: Task 6 ✓
