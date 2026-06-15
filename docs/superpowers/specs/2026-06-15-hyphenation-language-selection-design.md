# Hyphenation Language Selection — Design

Date: 2026-06-15
Status: Approved (brainstorming)
Scope: one implementation plan. Extends the novel auto-hyphenation feature
(`2026-06-14-novel-auto-hyphenation-design.md`).

## Goal

Turn the novel hyphenation control into a first-class language picker: make
**Automatic the default**, keep **Off** as an explicit option, and let the user
choose any **specific language** from a modal listing every hyphenation pattern
the app ships. Bundle crengine-ng's full set of pattern dictionaries so the
modal actually offers ~25 languages instead of just German/English.

Out of scope (deferred): plugin-provided hyphenation patterns (a LANGUAGE
plugin shipping its own `.pattern` via runtime `HyphMan` registration). Not
needed here because crengine already bundles the common languages; revisit only
for languages crengine does not ship.

## Background — where hyphenation patterns come from

- crengine-ng bundles ~30 TeX `.pattern` files at build time
  (`render-crengine/native/prefix/aarch64-linux-android/share/crengine-ng/hyph/`):
  fr, es, it, nl, pt, pl, cs, da, fi, ru, uk, el, hu, bg, ar, fa, and several
  Indic languages, plus combo/romanization files.
- The app currently **ships only two** as assets
  (`app/src/main/assets/hyph/hyph-en-us.pattern`, `hyph-de-1996.pattern`). At
  startup `nativeInit` calls `HyphMan::initDictionaries(hyphDir)`, which scans
  that directory and registers **every** `.pattern` it finds by language tag.
- `ReflowCss.PATTERN_DICTS` (render) is the app-side allowlist mapping a language
  code → pattern filename. `HyphenationResolver.SUPPORTED_HYPHENATION` (domain)
  is a parallel `{de, en}` set used by `resolveHyphenationLang`.
- So today only de/en are available **purely because only two files are
  bundled**. Adding more is: drop the files into assets + extend the maps.

## Design

### 1. Default + values

`novelHyphenationLang` may hold `"auto"`, `""` (off), or a language code
(`"de"`, `"fr"`, `"it"`, …). The default changes from `""` to **`"auto"`**:

- In `RoomSettingsRepository`, the observe mapping becomes
  `dao.observe(KEY_NOVEL_HYPHENATION).map { it ?: "auto" }` (was `?: ""`).
- No Room migration (free-form key/value; absence → `"auto"`).
- **Behavior-change caveat:** existing users who never touched the setting now
  get Automatic instead of Off. This is safe: `resolveHyphenationLang("auto",
  docLang)` returns the document language only when it is a bundled pattern,
  otherwise `""` (off). Users who explicitly chose Off (`""`) stay off.

### 2. Bundle crengine's pattern set

- Copy the **single-language** `.pattern` files from the native prefix into
  `app/src/main/assets/hyph/`. Skip the combo and romanization files
  (`hyph-ru-ru,en-us.pattern`, `hyph-zh-latn-pinyin.pattern`) and ambiguous
  variants — pick one canonical file per base language (en → `hyph-en-us`,
  de → `hyph-de-1996`, el → `hyph-el-monoton`, ru → `hyph-ru-ru`, etc.).
- `HyphMan::initDictionaries` already registers everything in the directory, so
  no native change is needed — only the asset set grows.
- APK size grows by a few MB (each pattern is a few hundred KB). Acceptable.

### 3. Single source of truth for the language list

The set of supported hyphenation languages must agree across three places (the
bundled asset files, the domain resolver, the render filename map). To avoid
drift:

- **Domain owns the canonical code list:** a new value
  `HyphenationLanguages.SUPPORTED: List<String>` (BCP-47 base codes), placed
  next to `resolveHyphenationLang`. `resolveHyphenationLang` uses it instead of
  the hard-coded `{de, en}` set.
- **Render maps code → filename:** `ReflowCss.PATTERN_DICTS` is keyed by exactly
  those codes (render already depends on `domain`).
- **Parity guard (unit test, render module):** assert
  `PATTERN_DICTS.keys == HyphenationLanguages.SUPPORTED.toSet()`. A
  bundled-but-unmapped or mapped-but-unlisted language fails the test. (Asset
  file presence is verified by the E2E run, not the JVM unit test.)
- Adding a language later = drop one `.pattern` file + one `PATTERN_DICTS` entry
  + one `SUPPORTED` entry; the parity test catches a forgotten map.

### 4. UI — shared picker + language modal

A single shared composable replaces the current segmented chips
(`SegmentedChoiceRow` in settings, the `ChoiceRow`s in the in-reader panel),
used in **both** places (`shared-structure-before-variants`):

- **`HyphenationPicker(value, onValue)`** renders two selectable chips
  **[Automatisch] [Aus]** plus a button **[Sprache: ‹name›]**. Selection derived
  from a pure helper `hyphenationSelection(value)`:
  - `"auto"` → Automatic selected.
  - `""` → Off selected.
  - any other code → the language button is selected and shows the localized
    language name; Automatic/Off unselected.
  Tapping Automatic sets `"auto"`, Off sets `""`, the language button opens the
  modal.
- **`HyphenationLanguageModal`** is an `EinkModal` (one dialog at a time,
  E-Ink design language): a scrollable list of the supported languages, each a
  selectable row showing the **localized** language name via
  `Locale(code).getDisplayLanguage(appLocale)`, sorted alphabetically by display
  name. The currently-selected code is marked. Selecting a row sets the code via
  `onValue` and dismisses. The app locale is the resolved UI language so names
  match the interface language.
- **In-reader panel:** the same `HyphenationPicker` + modal, so changing
  hyphenation while reading offers the full language list too. Standard
  one-dialog-over-the-reader rule applies (the modal is the single active
  dialog).

### 5. i18n

New keys (de + en, real umlauts): `novelHyphenationLanguage` ("Sprache" /
"Language") for the button label, `hyphenationLanguageTitle` ("Trennsprache" /
"Hyphenation language") for the modal title. Existing `novelHyphenationAuto` /
`novelHyphenationOff` are reused for the two chips. Language names are NOT i18n
keys — they come from `Locale`, auto-localized.

## Testing

- **Pure unit (domain):** extend `resolveHyphenationLang` tests for new bundled
  languages (e.g. `auto` + `it` → `it`, `auto` + `fr-FR` → `fr`, `auto` + an
  unbundled language → `""`). Test `hyphenationSelection(value)` derivation
  (auto/off/code → correct selected element).
- **Parity (render unit):** `PATTERN_DICTS.keys == HyphenationLanguages.SUPPORTED`.
- **E2E (emulator):** settings shows Automatic selected by default; the language
  button opens a modal listing many languages; picking Italian stores `"it"`;
  opening a German EPUB with Automatic applies German hyphenation. (crengine
  `.so` availability per the project's x86_64 note — verify on arm64 Boox if the
  emulator lacks it.)

## File structure

- `data/.../RoomSettingsRepository.kt` — default `"auto"`.
- `domain/.../render/HyphenationLanguages.kt` (new) — canonical code list.
- `domain/.../render/HyphenationResolver.kt` — use the list; add
  `hyphenationSelection` pure helper (or a small sibling file).
- `render-crengine/.../ReflowCss.kt` — extend `PATTERN_DICTS` to the full set.
- `render-crengine/.../ReflowCssHyphenationParityTest.kt` (new) — parity guard.
- `app/src/main/assets/hyph/*.pattern` — the bundled set.
- `app/.../ui/reader/HyphenationPicker.kt` (new) — shared picker + modal.
- `app/.../ui/settings/SettingsContent.kt`, `.../ui/reader/NovelTypographyControls.kt`
  — use the shared picker.
- `app/.../i18n/Strings.kt`, `MapBackedStrings.kt` — new keys.

## Coordination note

A parallel session has uncommitted work-in-progress on the crengine word-bookmark
capability (`cr3_bridge.cpp`, `CrengineDocument.kt`, `CrengineNative.kt`). This
feature touches **none** of those files (it changes `ReflowCss.kt`, assets,
domain, settings, UI). Implementation must avoid the parallel session's files to
prevent conflict.

## Docs to update in the same commit (docs-match-code)

`.claude/rules/architecture-seams.md` (the 2026-06-15 hyphenation note): the
supported-language list is now domain-owned with a render parity guard, the full
pattern set is bundled, and the picker offers a language modal.
