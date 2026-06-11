# Data-only Plugin-Template

Kopiervorlage für Color-Preset-, Reader-Preset- und Sprach-Plugins (data-only, kein Code).

- `src/main/AndroidManifest.xml` — `DATA_CATEGORY` (COLOR_PRESET|READER_PRESET|LANGUAGE),
  `DATA_ASSET` (Asset-Name), `ABI_VERSION` (in `[1,2]`).
- `src/main/assets/data.json` — die Nutzlast. Form je Kategorie (Schema in Spec 2 /
  `docs/superpowers/specs/2026-06-12-data-plugin-foundation-design.md`). Aktuell leer (`[]`).

Build: minimales Android-Library/App-APK-Setup analog zum Kindle-Color-Preset-Plugin.
Das Asset wird vom Host nur **gelesen**, nie ausgeführt.
