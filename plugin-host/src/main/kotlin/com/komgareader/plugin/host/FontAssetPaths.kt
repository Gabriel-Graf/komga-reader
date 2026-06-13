package com.komgareader.plugin.host

import java.io.File

/**
 * Permanent, version-keyed target file for an extracted plugin font asset:
 * `<root>/<packageName>/<versionCode>/<asset-basename>`. Pure path computation (no I/O).
 */
fun fontAssetTargetFile(root: File, packageName: String, versionCode: Long, assetPath: String): File {
    val basename = assetPath.substringAfterLast('/')
    return File(File(File(root, packageName), versionCode.toString()), basename)
}

/**
 * Version subdirectories under a package's font dir that are NOT [keepVersionCode]. Removing
 * these on extraction prevents a stale TTF lingering after a plugin update. Returns empty if
 * the package dir does not exist.
 */
fun staleVersionDirs(packageDir: File, keepVersionCode: Long): List<File> {
    val keep = keepVersionCode.toString()
    return packageDir.listFiles { f -> f.isDirectory && f.name != keep }?.toList().orEmpty()
}
