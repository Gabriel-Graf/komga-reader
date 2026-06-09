# Color-Preset-Format (Plugin-Typ c)

Spezifikation des JSON-Formats für importierbare Color-Presets. Diese Presets sind rein
deklarative Daten — kein Code, kein Classloader — und stellen den Proof des Lade-Wegs für
die Plugin-Architektur dar (Plugin-Entscheidung 5, `big-picture-and-goals.md`).

## ABI-Version

Aktuell: `1`. Gültig: `1 ≤ abiVersion ≤ 1` (beide Grenzen in `PluginAbi` in `plugin-api`).

## Format

```json
{
  "abiVersion": 1,
  "name": "Kaleido Warm",
  "saturation": 1.3,
  "contrast": 1.1,
  "brightness": 0.05
}
```

### Felder

| Feld | Typ | Beschreibung | Wertebereich (nach Clamp) |
|---|---|---|---|
| `abiVersion` | Integer | ABI-Versionskennung | 1..1 (aktuell) |
| `name` | String | Anzeigename des Presets | beliebig (nicht leer) |
| `saturation` | Float | Farbsättigung (1.0 = neutral, 0.0 = grau) | 0.5 .. 2.0 |
| `contrast` | Float | Kontrast (1.0 = neutral) | 0.5 .. 2.0 |
| `brightness` | Float | Helligkeit (0.0 = neutral) | −0.5 .. 0.5 |

Werte außerhalb des Wertebereichs werden beim Import auf das jeweilige Minimum/Maximum
geclampt (`ColorPresetImporter.toProfile`). Phase-2-Felder (Gamma, Schwarzpunkt,
Weißpunkt, Schärfe, Dithering) können noch nicht über die JSON-Schnittstelle gesetzt
werden — sie behalten ihre `ColorProfile`-Defaults.

## Implementierung

- `plugin-api` → `com.komgareader.plugin.ColorPresetSpec` + `PluginAbi`
- `data` → `com.komgareader.data.plugin.ColorPresetImporter`
- JSON-Parsing liegt in der `app`-Schicht (`ColorFilterViewModel.importPresetJson`) via
  `org.json.JSONObject` (Android-Laufzeit, keine Extra-Abhängigkeit in `plugin-api`).

## Import-Weg (In-App)

Settings → Farbfilter → „Preset importieren" → `ActivityResultContracts.OpenDocument`
(`application/json`) → JSON-Parsing → `ColorPresetImporter.toProfileOrNull` → DB-Upsert
→ Preset erscheint in der Profil-Liste, `builtIn = false`, löschbar.

## Provenance / Lizenz

Preset-Dateien sind nutzergenerierte Konfigurationsdaten (Dezimalzahlen) — kein
urheberrechtlich relevantes Werk. Keine Lizenzpflichten. Die App vertraut einzig auf
Typ-Sicherheit (geclampt) und ABI-Gate; der Nutzer importiert bewusst aus einer
selbst gewählten Datei.

Letzte Revision: 2026-06-09
