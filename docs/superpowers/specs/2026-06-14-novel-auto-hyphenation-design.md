# Novel Auto-Hyphenation — Design

Date: 2026-06-14
Status: Approved (brainstorming)
Scope: one small implementation plan (quick win).

## Goal

Let the novel reader pick the hyphenation pattern automatically from the EPUB's
own language tag, instead of forcing the user to set German/English by hand.

## Background (existing infra)

Hyphenation already exists as a manual setting:

- `SettingsRepository.novelHyphenationLang: Flow<String>` (Room key
  `novel_hyphenation_lang`), values today: `""` (off), `de`, `en`.
- The crengine engine already loads hyphenation patterns; the selected language
  is passed into the reflow path.
- Settings UI exposes Off / Deutsch / Englisch (i18n keys
  `novelHyphenation*`).

## Design

Add a new sentinel value `"auto"`:

1. **Setting:** `novelHyphenationLang` may hold `"auto"`. No migration (free-form
   string key, same as `language`/`active_ui_pack`).
2. **Document language:** expose the EPUB content language from the reflow seam.
   crengine knows the document `lang` / `xml:lang`; surface it as
   `ReflowableDocument.contentLanguage(): String` (`""` if unknown), default no-op
   in the domain interface so non-crengine factories need no change.
3. **Resolution (pure):** a small pure function
   `resolveHyphenationLang(setting: String, docLang: String): String` maps:
   - `"auto"` → normalized `docLang` if it maps to a supported pattern, else off.
   - any other value → returned as-is (off/de/en).
   Unit-tested for: auto+de-doc → de, auto+en-doc → en, auto+unknown → off,
   explicit de regardless of doc, off.
4. **Wiring:** the novel reader resolves the effective language via the pure
   function and feeds it into the reflow config it already builds.
5. **Settings UI:** add an "Automatisch" / "Automatic" option to the existing
   hyphenation picker. New i18n keys `novelHyphenationAuto` (de + en).

## Out of scope

- Adding new hyphenation pattern languages beyond what crengine already bundles.
  `"auto"` only selects among already-supported patterns; unsupported doc
  languages fall back to off.

## Testing

- **Pure unit:** `resolveHyphenationLang` table (auto/de/en/off × doc langs).
- **E2E:** open an EPUB with a `lang` tag on the emulator with the setting on
  "auto"; verify hyphenation matches the document language.

## Docs to update in the same commit (docs-match-code)

`.claude/rules/architecture-seams.md` (Naht B: new `contentLanguage()` on
`ReflowableDocument`) if the interface gains the method.
