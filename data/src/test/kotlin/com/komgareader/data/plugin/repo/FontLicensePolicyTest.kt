package com.komgareader.data.plugin.repo

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FontLicensePolicyTest {
    @Test fun allowsEveryAllowlistEntry() {
        listOf("OFL-1.1", "Apache-2.0", "CC0-1.0", "MIT", "Ubuntu-1.0").forEach {
            assertTrue(isLicenseAllowed(it), "expected allowed: $it")
        }
    }

    @Test fun blankIsBlocked() {
        assertFalse(isLicenseAllowed(""))
        assertFalse(isLicenseAllowed("   "))
    }

    @Test fun unknownIsBlocked() {
        assertFalse(isLicenseAllowed("GPL-3.0-only"))
        assertFalse(isLicenseAllowed("Proprietary"))
    }

    @Test fun matchesCaseInsensitivelyAfterTrim() {
        assertTrue(isLicenseAllowed("ofl-1.1"))
        assertTrue(isLicenseAllowed("APACHE-2.0"))
        assertTrue(isLicenseAllowed("  MIT  "))
    }
}
