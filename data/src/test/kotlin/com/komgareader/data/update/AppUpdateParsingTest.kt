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

    // --- parseReleaseList -----------------------------------------------------

    @Test fun parsesReleaseArrayNewestFirst() {
        val json = """
            [
              { "tag_name": "v0.3.21", "body": "C", "assets": [] },
              { "tag_name": "v0.3.20", "body": "B", "assets": [] },
              { "tag_name": "v0.3.19", "body": "A", "assets": [] }
            ]
        """.trimIndent()
        val list = parseReleaseList(json)
        assertEquals(listOf("0.3.21", "0.3.20", "0.3.19"), list.map { it.versionName })
    }

    @Test fun emptyListOnGarbage() = assertTrue(parseReleaseList("not json").isEmpty())

    @Test fun skipsTaglessEntryKeepsRest() {
        // A tag-less entry mid-list is skipped (mapNotNull), the valid ones survive — no crash.
        val json = """
            [
              { "tag_name": "v0.3.21", "body": "C", "assets": [] },
              { "message": "no tag here" },
              { "tag_name": "v0.3.20", "body": "B", "assets": [] }
            ]
        """.trimIndent()
        assertEquals(listOf("0.3.21", "0.3.20"), parseReleaseList(json).map { it.versionName })
    }

    // --- pendingReleases ------------------------------------------------------

    @Test fun pendingKeepsOnlyNewerThanCurrentNewestFirst() {
        val all = listOf(
            ReleaseInfo("v0.3.20", "0.3.20", "", null, "B"),
            ReleaseInfo("v0.3.21", "0.3.21", "", null, "C"),
            ReleaseInfo("v0.3.19", "0.3.19", "", null, "A"),
        )
        // On 0.3.19: 0.3.20 and 0.3.21 are pending, sorted newest first; 0.3.19 itself excluded.
        assertEquals(listOf("0.3.21", "0.3.20"), pendingReleases(all, "0.3.19").map { it.versionName })
    }

    @Test fun pendingEmptyWhenUpToDate() {
        val all = listOf(ReleaseInfo("v0.3.21", "0.3.21", "", null, "C"))
        assertTrue(pendingReleases(all, "0.3.21").isEmpty())
    }

    // --- combinedReleaseNotes -------------------------------------------------

    @Test fun combinedNotesJoinsTaggedBlocksAndSkipsEmpty() {
        val pending = listOf(
            ReleaseInfo("v0.3.21", "0.3.21", "", null, "C"),
            ReleaseInfo("v0.3.20", "0.3.20", "", null, ""),   // no notes → skipped
            ReleaseInfo("v0.3.19b", "0.3.19b", "", null, "A"),
        )
        assertEquals("v0.3.21\nC\n\nv0.3.19b\nA", combinedReleaseNotes(pending))
    }

    @Test fun combinedNotesTrimsBodyWhitespace() {
        val pending = listOf(ReleaseInfo("v0.3.21", "0.3.21", "", null, "  C  \n"))
        assertEquals("v0.3.21\nC", combinedReleaseNotes(pending))
    }
}
