package com.komgareader.data.plugin.repo

/** SPDX identifiers permitted for installable FONT plugins. Hard allowlist (P2 design §D). */
val FONT_LICENSE_ALLOWLIST: Set<String> = setOf(
    "OFL-1.1", "Apache-2.0", "CC0-1.0", "MIT", "Ubuntu-1.0",
)

/**
 * True iff [spdx] is an allowed font license. Comparison is trimmed and case-insensitive
 * (SPDX identifier matching is officially case-insensitive). Blank or unknown → false → blocked.
 */
fun isLicenseAllowed(spdx: String): Boolean {
    val value = spdx.trim()
    if (value.isEmpty()) return false
    return FONT_LICENSE_ALLOWLIST.any { it.equals(value, ignoreCase = true) }
}
