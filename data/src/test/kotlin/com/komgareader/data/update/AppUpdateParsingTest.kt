package com.komgareader.data.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Pure update logic (version comparison + release JSON parsing) — no network, no Android. */
class AppUpdateParsingTest {

    // --- isNewerVersion -------------------------------------------------------

    @Test fun newerPatch() = assertTrue(isNewerVersion("0.1.1", "0.1.0"))

    @Test fun newerWithVPrefix() = assertTrue(isNewerVersion("v0.2.0", "0.1.0"))

    @Test fun equalIsNotNewer() = assertFalse(isNewerVersion("0.1.0", "0.1.0"))

    @Test fun olderIsNotNewer() = assertFalse(isNewerVersion("0.1.0", "0.1.1"))

    @Test fun unevenLengthMajorWins() = assertTrue(isNewerVersion("1.0", "0.9.9"))

    // Numeric, not lexicographic: "10" > "9" only when compared as ints (the classic semver bug).
    @Test fun multiDigitComponentIsNumeric() = assertTrue(isNewerVersion("1.2.10", "1.2.9"))

    @Test fun trailingZeroEqualsShorter() = assertFalse(isNewerVersion("0.1.0", "0.1"))

    @Test fun preReleaseSuffixStripped() = assertTrue(isNewerVersion("v0.1.1-rc1", "0.1.0"))

    @Test fun garbageIsNotNewer() = assertFalse(isNewerVersion("", "0.1.0"))

    // --- parseLatestRelease ---------------------------------------------------

    @Test fun parsesTagHtmlAndApkAsset() {
        val json = """
            {
              "tag_name": "v0.1.1",
              "html_url": "https://github.com/owner/repo/releases/tag/v0.1.1",
              "body": "## Neu\n- Update-Check",
              "assets": [
                { "name": "notes.txt", "browser_download_url": "https://x/notes.txt" },
                { "name": "komga-reader-0.1.1.apk", "browser_download_url": "https://x/app.apk" }
              ]
            }
        """.trimIndent()
        val r = parseLatestRelease(json)!!
        assertEquals("v0.1.1", r.tag)
        assertEquals("0.1.1", r.versionName)
        assertEquals("https://github.com/owner/repo/releases/tag/v0.1.1", r.htmlUrl)
        assertEquals("https://x/app.apk", r.apkUrl)
        assertEquals("## Neu\n- Update-Check", r.body)
    }

    @Test fun nullApkWhenNoApkAsset() {
        val json = """{ "tag_name": "v0.1.0", "html_url": "https://h", "assets": [] }"""
        val r = parseLatestRelease(json)!!
        assertEquals("v0.1.0", r.tag)
        assertNull(r.apkUrl)
    }

    @Test fun nullWhenNoTag() = assertNull(parseLatestRelease("""{ "message": "Not Found" }"""))

    @Test fun nullOnGarbage() = assertNull(parseLatestRelease("not json"))
}
