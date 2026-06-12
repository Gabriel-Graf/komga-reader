package com.komgareader.domain.model

/**
 * Ein extern installierbarer **UI-Pack** (data-only Plugin-Kategorie UI_PACK): ein deklarativer
 * Deskriptor, der Teile der Oberfläche ersetzt. **Reine Primitive** (wie [ReaderPreset]) — keine
 * ui-api-/Compose-Typen, damit `domain`/`data` rein bleiben. Die Konvertierung in ui-api/Compose-Typen
 * (IconPack, ShellDescriptor, Token-Override) passiert **nur** in `:app`.
 *
 * Jede Sektion ist optional (Subset-Packs: nur Icons, nur Shell, nur Theme oder Kombinationen) —
 * fehlend = Host-Default.
 *
 * @property navStyle "BOTTOM_BAR" | "DRAWER" | null — überschreibt den Form-Faktor-Default des Shells.
 * @property iconRemap IconKey-Name → IconKey-Name (Remap unter den bestehenden Lucide-Glyphen).
 * @property accentHex Akzentfarbe (`#RRGGBB`) — host-erzwungen NUR angewandt, wenn die Geräteklasse
 *   Akzentfarbe erlaubt (mono E-Ink ignoriert).
 * @property cornerRadiusDp Eckenradius in dp (invariant-neutral, gilt immer).
 */
data class UiPackSpec(
    val packageName: String,
    val displayName: String,
    val abiVersion: Int,
    val navStyle: String? = null,
    val iconRemap: Map<String, String> = emptyMap(),
    val accentHex: String? = null,
    val cornerRadiusDp: Int? = null,
) {
    /** true, wenn der Pack mindestens eine Sektion liefert (sonst ist er wirkungslos). */
    val hasAnyOverride: Boolean
        get() = navStyle != null || iconRemap.isNotEmpty() || accentHex != null || cornerRadiusDp != null
}
