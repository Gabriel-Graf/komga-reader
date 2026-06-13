package com.komgareader.domain.render

/**
 * One installable reflowable-reader font, declared by a data-only FONT plugin's JSON asset.
 *
 * [family] MUST equal the TTF's internal FreeType family name — crengine selects fonts by it
 * (the `novelFontFamily` setting feeds `font.face.default`). [license] is the per-font SPDX
 * identifier kept for display/provenance only; the install/registration gate uses the
 * APK-level SPDX (see the P2 design, §D/§E), not this field.
 */
data class FontSpec(
    val family: String,
    val label: String,
    val asset: String,
    val license: String = "",
)
