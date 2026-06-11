package com.komgareader.plugin.host

import java.security.MessageDigest

/** Reine Helfer für den Signatur-Cert-Digest (TOFU-Pinning). Trennt die pure Hash-/Vergleichslogik
 *  von der Android-PackageManager-Abfrage, damit sie unit-testbar bleibt. */
object PluginSignature {
    /** SHA-256-Hex (lowercase, ohne Trenner) der rohen Zertifikat-Bytes. */
    fun sha256(certBytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(certBytes)
            .joinToString("") { "%02x".format(it) }

    /** Vergleicht zwei Digests case-insensitiv und whitespace-tolerant. */
    fun matches(pinned: String, actual: String): Boolean =
        pinned.trim().equals(actual.trim(), ignoreCase = true)
}
