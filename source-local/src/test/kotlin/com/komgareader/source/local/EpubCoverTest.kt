package com.komgareader.source.local

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Renderer-free embedded EPUB cover extraction (OPF → cover image), used by [LocalSource.coverBytes]. */
class EpubCoverTest {

    @Test
    fun `resolveZipPath joins opf dir with relative href and collapses dot-dot`() {
        assertEquals("OEBPS/images/cover.jpg", resolveZipPath("OEBPS", "images/cover.jpg"))
        assertEquals("cover.jpg", resolveZipPath("", "cover.jpg"))
        assertEquals("images/cover.jpg", resolveZipPath("OEBPS", "../images/cover.jpg"))
    }

    @Test
    fun `coverHrefFromOpf reads EPUB3 cover-image property`() {
        val opf = """
            <package><manifest>
              <item id="c" href="cover.jpg" media-type="image/jpeg" properties="cover-image"/>
              <item id="x" href="ch1.xhtml" media-type="application/xhtml+xml"/>
            </manifest></package>
        """.trimIndent()
        assertEquals("cover.jpg", coverHrefFromOpf(opf))
    }

    @Test
    fun `coverHrefFromOpf reads EPUB2 meta cover plus manifest item`() {
        val opf = """
            <package>
              <metadata><meta name="cover" content="cover-img"/></metadata>
              <manifest>
                <item id="cover-img" href="img/front.png" media-type="image/png"/>
              </manifest>
            </package>
        """.trimIndent()
        assertEquals("img/front.png", coverHrefFromOpf(opf))
    }

    @Test
    fun `coverHrefFromOpf reads EPUB2 meta with content before name`() {
        val opf = """
            <package>
              <metadata><meta content="cv" name="cover"/></metadata>
              <manifest><item id="cv" href="c.jpg"/></manifest>
            </package>
        """.trimIndent()
        assertEquals("c.jpg", coverHrefFromOpf(opf))
    }

    @Test
    fun `coverHrefFromOpf prefers EPUB3 cover-image over EPUB2 meta`() {
        val opf = """
            <package>
              <metadata><meta name="cover" content="old"/></metadata>
              <manifest>
                <item id="old" href="legacy.jpg" media-type="image/jpeg"/>
                <item id="new" href="modern.jpg" media-type="image/jpeg" properties="cover-image"/>
              </manifest>
            </package>
        """.trimIndent()
        assertEquals("modern.jpg", coverHrefFromOpf(opf))
    }

    @Test
    fun `coverHrefFromOpf url-decodes spaces in href`() {
        val opf = """
            <package><manifest>
              <item id="c" href="my%20cover.jpg" properties="cover-image"/>
            </manifest></package>
        """.trimIndent()
        assertEquals("my cover.jpg", coverHrefFromOpf(opf))
    }

    @Test
    fun `coverHrefFromOpf returns null when no cover is declared`() {
        val opf = """<package><manifest><item id="x" href="ch1.xhtml"/></manifest></package>"""
        assertNull(coverHrefFromOpf(opf))
    }

    @Test
    fun `extractEpubCoverImage returns the declared cover bytes`() {
        val coverBytes = byteArrayOf(1, 2, 3, 4, 5)
        val epub = buildEpub(
            opfPath = "OEBPS/content.opf",
            opf = """
                <package><metadata><meta name="cover" content="cv"/></metadata>
                <manifest><item id="cv" href="images/cover.jpg" media-type="image/jpeg"/></manifest>
                </package>
            """.trimIndent(),
            coverEntry = "OEBPS/images/cover.jpg",
            coverBytes = coverBytes,
        )
        assertTrue(coverBytes.contentEquals(extractEpubCoverImage(epub)))
    }

    @Test
    fun `extractEpubCoverImage returns null when the declared cover entry is missing`() {
        val epub = buildEpub(
            opfPath = "OEBPS/content.opf",
            opf = """
                <package><manifest>
                <item id="cv" href="images/cover.jpg" properties="cover-image"/>
                </manifest></package>
            """.trimIndent(),
            coverEntry = null, // declared in the OPF but absent from the zip
            coverBytes = null,
        )
        assertNull(extractEpubCoverImage(epub))
    }

    @Test
    fun `extractEpubCoverImage returns null without a cover`() {
        val epub = buildEpub(
            opfPath = "OEBPS/content.opf",
            opf = """<package><manifest><item id="x" href="ch1.xhtml"/></manifest></package>""",
            coverEntry = null,
            coverBytes = null,
        )
        assertNull(extractEpubCoverImage(epub))
    }

    /** Minimal in-memory EPUB zip: container.xml → opf, optional cover image entry. */
    private fun buildEpub(
        opfPath: String,
        opf: String,
        coverEntry: String?,
        coverBytes: ByteArray?,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            fun write(name: String, data: ByteArray) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(data)
                zos.closeEntry()
            }
            write(
                "META-INF/container.xml",
                """<container><rootfiles><rootfile full-path="$opfPath"/></rootfiles></container>"""
                    .toByteArray(),
            )
            write(opfPath, opf.toByteArray())
            if (coverEntry != null && coverBytes != null) write(coverEntry, coverBytes)
        }
        return out.toByteArray()
    }
}
