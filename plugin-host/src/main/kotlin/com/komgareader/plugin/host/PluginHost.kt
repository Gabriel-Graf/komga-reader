package com.komgareader.plugin.host

import android.content.Context
import android.content.pm.PackageManager
import com.komgareader.domain.model.SourceKind
import com.komgareader.domain.source.BrowsableSource
import com.komgareader.domain.source.SourceId
import com.komgareader.plugin.SourcePlugin

/**
 * Entdeckt, prüft und lädt Quellen-Plugin-APKs (Plugin-Plan-Entscheidungen 3–5).
 * Lädt via createPackageContext-Classloader mit Host als Parent — kein DexClassLoader,
 * keine heruntergeladenen .dex (OS macht Signatur/Integrität beim Install).
 *
 * Sicherheitsmodell: CONTEXT_IGNORE_SECURITY wird absichtlich beibehalten, damit
 * Drittanbieter-signierte Plugins überhaupt ladbar sind. Das eigentliche Trust-Gate
 * ist die gepinnte Cert-SHA-256 in [sourceFor] — ein Plugin wird nur ausgeführt,
 * wenn die aktuelle Paket-Signatur mit dem beim Hinzufügen bestätigten Pin übereinstimmt
 * (TOFU: Trust-on-First-Use).
 */
class PluginHost(private val context: Context) {

    /** Alle installierten, ABI-kompatiblen Quellen-Plugins. */
    fun discoverPlugins(): List<DiscoveredPlugin> {
        val pm = context.packageManager
        val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA)
        return packages.mapNotNull { pkg ->
            val meta = pkg.applicationInfo?.metaData ?: return@mapNotNull null
            val entry = meta.getString(PluginManifestKeys.ENTRY_CLASS) ?: return@mapNotNull null
            val abi = meta.getInt(PluginManifestKeys.ABI_VERSION, -1)
            if (!AbiGate.isCompatible(abi)) return@mapNotNull null
            val sig = signatureSha256(pkg.packageName) ?: return@mapNotNull null
            val plugin = instantiate(pkg.packageName, entry) ?: return@mapNotNull null
            DiscoveredPlugin(pkg.packageName, sig, abi, plugin.metadata, plugin.configSchema(), entry)
        }
    }

    /**
     * Erzeugt die laufende BrowsableSource für eine konfigurierte Plugin-Quelle — NUR wenn die
     * aktuelle Paket-Signatur dem beim Hinzufügen gepinnten [expectedSignature] entspricht (TOFU).
     * Bei Signatur-Mismatch (ausgetauschtes/untergeschobenes APK) wird NICHT geladen → null.
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

    /** Stabile sourceId für eine Plugin-Quelle (packageName + configHash als Namespace). */
    fun sourceId(packageName: String, displayName: String, config: Map<String, String>): Long =
        SourceId.of(displayName, SourceKind.PLUGIN, "$packageName/${PluginConfigHash.of(config)}")

    /** Liest die SHA-256-Hex des ersten Signing-Zertifikats des Pakets (API 28+). */
    private fun signatureSha256(packageName: String): String? = runCatching {
        val info = context.packageManager.getPackageInfo(
            packageName, PackageManager.GET_SIGNING_CERTIFICATES,
        )
        val signers = info.signingInfo?.apkContentsSigners ?: return null
        val cert = signers.firstOrNull() ?: return null
        PluginSignature.sha256(cert.toByteArray())
    }.getOrNull()

    private fun instantiate(packageName: String, entryClass: String): SourcePlugin? = runCatching {
        val flags = Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY
        val pluginContext = context.createPackageContext(packageName, flags)
        val clazz = pluginContext.classLoader.loadClass(entryClass)
        clazz.getDeclaredConstructor().newInstance() as SourcePlugin
    }.getOrNull()
}
