package com.komgareader.ui.theme

import com.komgareader.domain.model.DisplayBehavior

/**
 * Registry der verfügbaren [UiPack]s — die In-Tree-Stelle, an der Packs **aufgezählt, per `id`
 * aufgelöst und (künftig) registriert** werden. Analog zu `SourceManager` bei den Quellen: heute nur
 * die drei Built-ins, später der Einhängepunkt für ein nutzer-/community-geliefertes Pack.
 *
 * **Bewusst klein und in-tree, kein eingefrorenes ABI / kein APK-Lader** (User-Constraint
 * „ABI nicht freezen jetzt"). Der externe Lade-Weg (separates APK via `PackageManager`,
 * ABI-Versionsgate) ist Phase 4 und hängt sich genau hier ein — erst wenn die drei Built-ins den
 * [UiPack]-Vertrag bewiesen haben. Bis dahin: keine Annahmen ins Interface backen, die ein externes
 * Pack später nicht erfüllen kann.
 *
 * Auswahl-Strategie heute: **automatisch nach Geräteklasse** ([forBehavior] → [packFor]). Ein
 * späteres „UI-Pack auswählen" (manueller Override in den Einstellungen) liest stattdessen [byId];
 * der Vertrag steht dafür schon.
 */
object UiPackRegistry {

    private val builtIns: List<UiPack> = listOf(MonoEinkPack, KaleidoPack, AuroraPack, LcdPack)

    /** Alle aktuell verfügbaren Packs (heute die drei Built-ins). */
    fun all(): List<UiPack> = builtIns

    /** Pack per stabiler [UiPack.id], oder `null` wenn unbekannt (z. B. deinstalliertes Pack). */
    fun byId(id: String): UiPack? = builtIns.firstOrNull { it.id == id }

    /**
     * Das aktive Pack zur Geräteklasse. Default-Auflösung der Built-ins über [packFor]; fällt für
     * unbekannte Klassen sicher auf mono. Hier setzt später ein manueller Override / externes Pack an.
     */
    fun forBehavior(behavior: DisplayBehavior): UiPack = packFor(behavior)
}
