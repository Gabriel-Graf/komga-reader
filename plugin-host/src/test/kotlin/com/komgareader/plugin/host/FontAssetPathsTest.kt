package com.komgareader.plugin.host

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FontAssetPathsTest {
    @Test fun targetFileIsVersionKeyedByPackageAndBasename() {
        val root = File("/tmp/plugin-fonts")
        val target = fontAssetTargetFile(root, "com.example.lora", versionCode = 7, assetPath = "fonts/Lora.ttf")
        assertEquals(File("/tmp/plugin-fonts/com.example.lora/7/Lora.ttf"), target)
    }

    @Test fun staleVersionDirsListedForRemoval() {
        val pkgDir = createTempPkgDir(listOf("5", "6", "7"))
        val stale = staleVersionDirs(pkgDir, keepVersionCode = 7)
        assertEquals(setOf("5", "6"), stale.map { it.name }.toSet())
        pkgDir.deleteRecursively()
    }

    @Test fun staleVersionDirsEmptyWhenOnlyTargetPresent() {
        val pkgDir = createTempPkgDir(listOf("7"))
        assertTrue(staleVersionDirs(pkgDir, keepVersionCode = 7).isEmpty())
        pkgDir.deleteRecursively()
    }

    private fun createTempPkgDir(versions: List<String>): File {
        val base = File.createTempFile("pkg", "").let { it.delete(); it.mkdirs(); it }
        versions.forEach { File(base, it).mkdirs() }
        return base
    }
}
