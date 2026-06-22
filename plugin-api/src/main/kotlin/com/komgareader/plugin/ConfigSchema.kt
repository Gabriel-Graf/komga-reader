package com.komgareader.plugin

/** Generic config schema declared by a plugin to the host (plugin plan: per-plugin settings seed). */
data class ConfigSchema(val fields: List<ConfigField>)

data class ConfigField(
    /** Storage key in ServerConfig.extras or the plugincfg KV store. */
    val key: String,
    /** Label supplied by the plugin (already localised). */
    val label: String,
    val type: FieldType,
    val required: Boolean = true,
    val default: String = "",
    /** NUMBER only: slider bounds and step size. null for other types. */
    val min: Double? = null,
    val max: Double? = null,
    val step: Double? = null,
) {
    /**
     * Binary-compatibility overload for plugins compiled against the pre-NUMBER SDK.
     *
     * `min`/`max`/`step` were added to the primary constructor (ABI 3→4). In Kotlin, adding
     * constructor parameters — even with defaults — changes the primary constructor's JVM
     * signature and removes the old 5-arg `<init>(String, String, FieldType, boolean, String)`.
     * An already-distributed plugin APK still calls that exact signature and would otherwise throw
     * NoSuchMethodError at load (observed on-device with the Kavita source plugin). This explicit
     * secondary constructor restores that JVM method so old binaries keep loading. Do NOT remove,
     * and apply the same pattern for any future field added here — the ABI integer gate cannot
     * catch a constructor-signature break (see PluginAbi).
     */
    constructor(key: String, label: String, type: FieldType, required: Boolean, default: String) :
        this(key, label, type, required, default, null, null, null)
}

enum class FieldType { TEXT, SECRET, URL, BOOL, NUMBER }
