package com.komgareader.source.local

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class CbzArchiveTest {
    private val dir: File = createTempDirectory("cbz-test").toFile()
    @AfterTest fun cleanup() { dir.deleteRecursively() }

    private fun makeCbz(): File {
        val f = File(dir, "vol.cbz")
        ZipOutputStream(f.outputStream()).use { zip ->
            listOf("010.jpg" to "ten", "ComicInfo.xml" to "<ComicInfo/>", "002.jpg" to "two", "001.jpg" to "one")
                .forEach { (name, body) ->
                    zip.putNextEntry(ZipEntry(name)); zip.write(body.toByteArray()); zip.closeEntry()
                }
        }
        return f
    }
    @Test fun `image entries are listed in natural order, non-images excluded`() {
        val cbz = CbzArchive(makeCbz())
        assertEquals(3, cbz.pageCount())
        assertContentEquals("one".toByteArray(), cbz.pageBytes(0))
        assertContentEquals("two".toByteArray(), cbz.pageBytes(1))
        assertContentEquals("ten".toByteArray(), cbz.pageBytes(2))
    }
    @Test fun `comicinfo bytes returned when present`() {
        val cbz = CbzArchive(makeCbz())
        assertEquals("<ComicInfo/>", cbz.comicInfoXml())
    }
}
