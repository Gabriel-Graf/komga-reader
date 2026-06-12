package com.komgareader.ui.icons

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * Die Icon-Pack-Naht ist das Gegenstück zur Theme-Pack- und UI-Slot-Naht, aber **prozess-global**
 * statt `CompositionLocal` (Spec §2): `AppIcons.*` wird auch außerhalb von Composition gelesen
 * (Datenklassen-Felder, Default-Argumente), wo ein `CompositionLocal` nicht lesbar wäre. Geprüft
 * wird die Auflösungs-Logik [ActiveIconPack.resolve]: Vollständigkeit des Default-Packs,
 * Fallback eines Teil-Packs auf den Default und das Überschreiben eines Glyphs.
 *
 * Über **referenzielle Identität** (`assertSame`): die `LucideIcons.*`-Glyphen sind `by lazy`-`val`s,
 * geben also bei wiederholtem Zugriff dieselbe Instanz zurück — der Resolver reicht die Referenz
 * unverändert durch.
 */
class IconPackTest {

    @AfterEach
    fun resetGlobalState() {
        ActiveIconPack.current = DefaultIconPack
    }

    @Test
    fun `default-pack deckt jeden IconKey ab`() {
        for (key in IconKey.values()) {
            assertNotNull(DefaultIconPack.resolve(key), "DefaultIconPack hat keinen Glyph für $key")
        }
    }

    @Test
    fun `fehlende Überschreibung fällt auf das default-pack zurück`() {
        val alt = IconPack { null }

        ActiveIconPack.current = alt

        assertSame(DefaultIconPack.resolve(IconKey.Home), ActiveIconPack.resolve(IconKey.Home))
    }

    @Test
    fun `geliefertes pack überschreibt den default`() {
        val alt = IconPack { key -> if (key == IconKey.Home) LucideIcons.Library else null }

        ActiveIconPack.current = alt

        assertSame(LucideIcons.Library, ActiveIconPack.resolve(IconKey.Home))
        assertSame(LucideIcons.Library, AppIcons.Home)
    }
}
