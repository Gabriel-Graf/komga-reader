package com.komgareader.app.ui.pack

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.komgareader.domain.model.UiPackSpec
import com.komgareader.ui.icons.DefaultIconPack
import com.komgareader.ui.icons.IconKey
import com.komgareader.ui.icons.IconPack
import com.komgareader.ui.shell.ShellDescriptor
import com.komgareader.ui.shell.ShellNavStyle

/**
 * App-seitige Konverter, die einen reinen [UiPackSpec] (domain, Primitive) in die ui-api/Compose-Typen
 * übersetzen. Bewusst **nur hier** (in `:app`) — `domain`/`data` bleiben Compose-/ui-api-frei (Spec §4).
 * Jeder Konverter gibt `null` zurück, wenn die Sektion fehlt oder unbrauchbar ist → der Host fällt sauber
 * auf seinen Default zurück (analog StubSource/Slot-Fallback).
 */

/** Token-Override eines UI-Packs: nur die Felder, die ein deklarativer Pack setzen darf (Spec §5.3). */
data class TokenOverride(val accent: Color?, val cornerRadius: Dp?)

/**
 * Baut aus [UiPackSpec.iconRemap] ein [IconPack]. Bedeutung pro Eintrag: „rendere die Semantik des
 * Schlüssel-IconKeys mit dem Glyphen des Wert-IconKeys". Ungültige IconKey-Namen (Schlüssel ODER Wert)
 * werden verworfen — kein Crash. `null`, wenn keine gültigen Einträge übrig bleiben (→ DefaultIconPack).
 */
fun UiPackSpec.toIconPack(): IconPack? {
    val remap: Map<IconKey, IconKey> = iconRemap.mapNotNull { (from, to) ->
        val fromKey = IconKey.entries.firstOrNull { it.name == from } ?: return@mapNotNull null
        val toKey = IconKey.entries.firstOrNull { it.name == to } ?: return@mapNotNull null
        fromKey to toKey
    }.toMap()
    if (remap.isEmpty()) return null
    return IconPack { key -> remap[key]?.let { DefaultIconPack.resolve(it) } }
}

/**
 * Übersetzt [UiPackSpec.navStyle] in einen [ShellDescriptor], der den Form-Faktor-Default überschreibt.
 * `null`, wenn kein/ein ungültiger navStyle gesetzt ist (→ Form-Faktor-Default bleibt).
 */
fun UiPackSpec.shellOverride(): ShellDescriptor? {
    val style = navStyle?.let { name -> ShellNavStyle.entries.firstOrNull { it.name == name } } ?: return null
    return ShellDescriptor(style)
}

/**
 * Übersetzt die theme-Sektion in einen [TokenOverride]. Akzent als `#RRGGBB`/`RRGGBB` tolerant geparst
 * (ungültig → null), Eckradius auf 0..32 dp geclampt. `null`, wenn keine theme-Felder gesetzt sind.
 * Das **E-Ink-Gate** (Akzent nur bei `allowsAccentColor`) liegt NICHT hier, sondern host-erzwungen in
 * [com.komgareader.app.ui.theme.KomgaReaderTheme] — dieser Konverter ist rein.
 */
fun UiPackSpec.tokenOverride(): TokenOverride? {
    val accent = accentHex?.let(::parseHexColor)
    val corner = cornerRadiusDp?.coerceIn(0, 32)?.dp
    if (accent == null && corner == null) return null
    return TokenOverride(accent, corner)
}

/** Parst `#RRGGBB` oder `RRGGBB` in eine [Color]; `null` bei ungültigem Format. Rein, ohne Android. */
fun parseHexColor(hex: String): Color? {
    val cleaned = hex.removePrefix("#")
    if (cleaned.length != 6) return null
    val rgb = cleaned.toLongOrNull(16) ?: return null
    return Color(0xFF000000L or rgb)
}
