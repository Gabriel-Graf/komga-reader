package com.komgareader.plugin.host

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import dalvik.system.PathClassLoader
import java.io.File
import com.komgareader.domain.model.SourceKind
import com.komgareader.domain.source.BrowsableSource
import com.komgareader.domain.source.SourceId
import com.komgareader.plugin.PluginCategory
import com.komgareader.plugin.SourcePlugin

/**
 * Discovers, verifies, and loads source plugin APKs (plugin plan decisions 3–5).
 * Loads via [PathClassLoader] on the installed APK path with the host as parent (see
 * [instantiate]) — no DexClassLoader of downloaded .dex (the OS handles signature/integrity
 * at install time).
 *
 * Security model: CONTEXT_IGNORE_SECURITY is intentionally kept so that third-party-signed
 * plugins can be loaded at all. The actual trust gate is the pinned cert SHA-256 in [sourceFor]
 * — a plugin is only executed when the current package signature matches the pin confirmed at
 * add-time (TOFU: Trust-on-First-Use).
 */
class PluginHost(private val context: Context) {

    /** All installed, ABI-compatible source plugins. */
    fun discoverPlugins(): List<DiscoveredPlugin> {
        val pm = context.packageManager
        val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA)
        return packages.mapNotNull { pkg ->
            val meta = pkg.applicationInfo?.metaData ?: return@mapNotNull null
            val entry = meta.getString(PluginManifestKeys.ENTRY_CLASS) ?: return@mapNotNull null
            val abi = readAbiVersion(meta) ?: return@mapNotNull null
            if (!AbiGate.isCompatible(abi)) return@mapNotNull null
            val sig = signatureSha256(pkg.packageName) ?: return@mapNotNull null
            val plugin = instantiate(pkg.packageName, entry) ?: return@mapNotNull null
            DiscoveredPlugin(pkg.packageName, sig, abi, plugin.metadata, plugin.configSchema(), entry)
        }
    }

    /**
     * Generic discovery of all installed, ABI-compatible **data-only** plugins of a [category].
     * Reads the category + asset from the manifest metadata per package (via the pure
     * [resolveDataPluginManifest], with legacy `COLOR_PRESETS` support) and the asset via
     * `createPackageContext(pkg, 0)` — **flags 0 = resources only, NO code**: no PathClassLoader,
     * no signature check, no Multidex. Only a file is read; plugin code is never executed.
     * Package visibility via app-manifest `QUERY_ALL_PACKAGES`. JSON interpretation is left to
     * the caller (e.g. [discoverColorPresetPlugins] → [parsePresetSpecs]).
     */
    fun discoverDataPlugins(category: PluginCategory): List<DiscoveredDataPlugin> =
        scanDataPluginManifests(category).mapNotNull { s ->
            val json = runCatching {
                context.createPackageContext(s.packageName, 0)
                    .assets.open(s.assetName).bufferedReader().use { it.readText() }
            }.getOrNull() ?: return@mapNotNull null
            DiscoveredDataPlugin(s.packageName, s.category, s.abi, s.assetName, s.label, json, s.license, s.versionCode)
        }

    /**
     * Like [discoverDataPlugins], but WITHOUT reading the asset — identity/ABI/asset-name only. For
     * categories with large binary assets (PANEL_MODEL): the listing must never load multiple MB per scan.
     */
    fun discoverDataPluginInfos(category: PluginCategory): List<DataPluginInfo> =
        scanDataPluginManifests(category)
            .map { s -> DataPluginInfo(s.packageName, s.category, s.abi, s.assetName, s.label, s.configAssetName) }

    private data class ScannedDataPlugin(
        val packageName: String,
        val category: PluginCategory,
        val abi: Int,
        val assetName: String,
        val label: String,
        /** SPDX license identifier from the plugin manifest (empty when not declared). */
        val license: String = "",
        /** Android versionCode of the plugin APK; 0 when unavailable. */
        val versionCode: Long = 0,
        /** Asset name of the optional config schema (DATA_CONFIG), null when not declared. */
        val configAssetName: String? = null,
    )

    /**
     * Pure manifest scan of all installed, ABI-compatible data-only plugins of a [category]:
     * getInstalledPackages → [resolveDataPluginManifest] → category filter → [readAbiVersion] →
     * [AbiGate] → label fallback. Reads NO asset. Shared base for [discoverDataPlugins]
     * (which then reads the JSON) and [discoverDataPluginInfos] (which skips the asset).
     */
    private fun scanDataPluginManifests(category: PluginCategory): List<ScannedDataPlugin> {
        val pm = context.packageManager
        return pm.getInstalledPackages(PackageManager.GET_META_DATA).mapNotNull { pkg ->
            val meta = pkg.applicationInfo?.metaData ?: return@mapNotNull null
            val (resolvedCategory, assetName) = resolveDataPluginManifest(
                dataCategory = meta.getString(PluginManifestKeys.DATA_CATEGORY),
                dataAsset = meta.getString(PluginManifestKeys.DATA_ASSET),
                legacyColorPresets = meta.getString(PluginManifestKeys.COLOR_PRESETS),
            ) ?: return@mapNotNull null
            if (resolvedCategory != category) return@mapNotNull null
            val abi = readAbiVersion(meta) ?: return@mapNotNull null
            if (!AbiGate.isCompatible(abi)) return@mapNotNull null
            val label = pkg.applicationInfo
                ?.let { runCatching { pm.getApplicationLabel(it).toString() }.getOrNull()?.ifBlank { null } }
                ?: pkg.packageName
            val license = meta.getString(PluginManifestKeys.LICENSE)?.trim().orEmpty()
            @Suppress("DEPRECATION") val versionCode = pkg.versionCode.toLong()
            val configAssetName = meta.getString(PluginManifestKeys.DATA_CONFIG)?.trim()?.ifBlank { null }
            ScannedDataPlugin(pkg.packageName, resolvedCategory, abi, assetName, label, license, versionCode, configAssetName)
        }
    }

    /**
     * Reads the raw asset bytes of the first installed, ABI-compatible data-only plugin of the
     * [category] (via `createPackageContext(pkg, 0)`, flags 0 = resources only, NO code). Returns null
     * when none is installed or the asset cannot be read. For binary assets (ONNX model).
     */
    fun binaryDataPluginBytes(category: PluginCategory): ByteArray? {
        val info = discoverDataPluginInfos(category).firstOrNull() ?: return null
        return runCatching {
            context.createPackageContext(info.packageName, 0)
                .assets.open(info.assetName).use { it.readBytes() }
        }.getOrNull()
    }

    /**
     * Reads the optional config-schema asset (DATA_CONFIG) of the installed data-only plugin
     * [packageName] as a JSON string — resource-only via `createPackageContext(pkg, 0)`, NO code.
     * Returns null when the plugin declares no DATA_CONFIG or the asset cannot be read.
     *
     * Note: currently scoped to [PluginCategory.PANEL_MODEL] only; a data plugin in another
     * category that declares DATA_CONFIG will always return null until this method accepts a
     * category parameter.
     */
    fun dataPluginConfigJson(packageName: String): String? {
        val info = discoverDataPluginInfos(PluginCategory.PANEL_MODEL)
            .firstOrNull { it.packageName == packageName } ?: return null
        val asset = info.configAssetName ?: return null
        return runCatching {
            context.createPackageContext(packageName, 0).assets.open(asset).bufferedReader().use { it.readText() }
        }.getOrNull()
    }

    /**
     * All installed, ABI-compatible **data-only** color-preset plugins (type c) — thin wrapper around
     * [discoverDataPlugins] for [com.komgareader.plugin.PluginCategory.COLOR_PRESET]. Parses the
     * asset JSON via [parsePresetSpecs]; empty/broken assets are discarded.
     */
    fun discoverColorPresetPlugins(): List<DiscoveredPresetPlugin> =
        discoverDataPlugins(PluginCategory.COLOR_PRESET).mapNotNull { d ->
            val specs = parsePresetSpecs(d.assetJson, d.abiVersion)?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            DiscoveredPresetPlugin(d.packageName, d.displayName, d.abiVersion, specs)
        }

    /**
     * Extracts a data-only plugin asset (e.g. a TTF) to permanent, version-keyed storage:
     * `<destRoot>/<packageName>/<versionCode>/<asset-basename>`. Stale version dirs of the same
     * package are removed first (no stale TTF after an update). Returns the file, or null on
     * I/O error. Uses createPackageContext(pkg, 0) — resources only, no code load / no TOFU.
     */
    fun extractFontAsset(packageName: String, assetPath: String, destRoot: File): File? = runCatching {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val versionCode = pm.getPackageInfo(packageName, 0).versionCode.toLong()
        val target = fontAssetTargetFile(destRoot, packageName, versionCode, assetPath)
        val packageDir = target.parentFile?.parentFile // <destRoot>/<packageName>
        if (packageDir != null) staleVersionDirs(packageDir, versionCode).forEach { it.deleteRecursively() }
        if (!target.exists()) {
            target.parentFile?.mkdirs()
            context.createPackageContext(packageName, 0).assets.open(assetPath).use { input ->
                target.outputStream().use { input.copyTo(it) }
            }
        }
        target
    }.getOrNull()

    /**
     * Creates the live BrowsableSource for a configured plugin source — ONLY when the current
     * package signature matches the [expectedSignature] pinned at add-time (TOFU).
     * On signature mismatch (swapped/substituted APK) the plugin is NOT loaded → null.
     */
    fun sourceFor(
        packageName: String,
        entryClass: String,
        expectedSignature: String,
        config: Map<String, String>,
    ): BrowsableSource? {
        val actual = signatureSha256(packageName) ?: return null
        if (!PluginSignature.matches(expectedSignature, actual)) return null
        val plugin = instantiate(packageName, entryClass) ?: return null
        return plugin.create(config)
    }

    /** Stable sourceId for a plugin source (packageName + configHash as namespace). */
    fun sourceId(packageName: String, displayName: String, config: Map<String, String>): Long =
        SourceId.of(displayName, SourceKind.PLUGIN, "$packageName/${PluginConfigHash.of(config)}")

    /**
     * Reads the ABI version from manifest metadata robustly as either Int OR String. `aapt`
     * types `android:value="1"` as integer or string depending on the declaration; `getInt`
     * silently falls back to its default when the value is a string. Plugin authors may use either
     * form, so both paths are checked. `null` = key missing or unreadable.
     */
    private fun readAbiVersion(meta: Bundle): Int? {
        val asInt = meta.getInt(PluginManifestKeys.ABI_VERSION, Int.MIN_VALUE)
        if (asInt != Int.MIN_VALUE) return asInt
        return meta.getString(PluginManifestKeys.ABI_VERSION)?.trim()?.toIntOrNull()
    }

    /** Reads the SHA-256 hex of the first signing certificate of the package (API 28+). */
    private fun signatureSha256(packageName: String): String? = runCatching {
        val info = context.packageManager.getPackageInfo(
            packageName, PackageManager.GET_SIGNING_CERTIFICATES,
        )
        val signers = info.signingInfo?.apkContentsSigners ?: return null
        val cert = signers.firstOrNull() ?: return null
        PluginSignature.sha256(cert.toByteArray())
    }.getOrNull()

    /**
     * Loads the plugin class via a [PathClassLoader] on the APK path (`sourceDir`) with the host as
     * parent classloader (Mihon model). **Deliberately not `createPackageContext`:** its classloader
     * only loads the primary `classes.dex` for a third-party package — a plugin with larger libs
     * (Retrofit/serialization/coroutines) is always Multidex, so its entry class ends up in a
     * secondary `classesN.dex` → `ClassNotFoundException`. `PathClassLoader` over the APK path
     * enumerates **all** dex files. Parent = host classloader → the contract interfaces (SourcePlugin &
     * seam types) are the same classes as in the host (no `ClassCastException`). No DexClassLoader
     * of downloaded `.dex` — only the OS-installed, signature-verified APK is loaded.
     */
    private fun instantiate(packageName: String, entryClass: String): SourcePlugin? = runCatching {
        val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
        val loader = PathClassLoader(appInfo.sourceDir, appInfo.nativeLibraryDir, context.classLoader)
        val clazz = loader.loadClass(entryClass)
        clazz.getDeclaredConstructor().newInstance() as SourcePlugin
    }.getOrNull()
}
