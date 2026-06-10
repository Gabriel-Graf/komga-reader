package com.komgareader.app.ui.collections

/**
 * Darf bei jedem Tab-Öffnen voll gesynct werden? Nur auf bewegungs-/akku-unkritischen Geräten
 * (LCD/Smartphone). Auf E-Ink läuft der Voll-Sync nur an Server-Connect/App-Start und manuell.
 * Bewusst keine binäre `isEink`-Zementierung: leitet aus dem Display-Mode ab, künftig aus
 * DisplayBehavior.allowsMotion.
 */
fun aggressiveSyncAllowed(displayMode: String): Boolean = displayMode != "EINK"
