package com.komgareader.plugin.host

import com.komgareader.plugin.PluginAbi

/** Reines 2-Int-ABI-Gate (Plugin-Plan-Entscheidung 2). */
object AbiGate {
    fun isCompatible(abiVersion: Int): Boolean =
        abiVersion in PluginAbi.MIN_SUPPORTED..PluginAbi.VERSION
}
