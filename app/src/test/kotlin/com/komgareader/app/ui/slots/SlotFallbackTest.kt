package com.komgareader.app.ui.slots

import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * Die UI-Slot-Naht ist das Gegenstück zur Theme-Pack-Naht ([com.komgareader.app.ui.theme.UiPack]),
 * eine Ebene tiefer: nicht *wie* der Look (Farbe/Typo/Token), sondern *welcher Baustein* eine
 * adressierbare Chrome-Region füllt. Gebaute Regionen: **header** (erste) + **homeHeader** (zweite)
 * + **dialog** (dritte) + **settings** (vierte) + **tiles** (fünfte).
 *
 * Geprüft wird die **Auflösungs-Logik** [UiSlots.resolve] als pure Funktion über nullbare
 * Referenzen: ein fehlender Slot fällt auf [DefaultSlots] zurück (nie `null`, analog `StubSource`),
 * ein gelieferter überschreibt den Default. Bewusst über **referenzielle Identität** (`assertSame`),
 * nicht inhaltliche Gleichheit: ein `@Composable`-Lambda hat keine verlässliche `equals`-Semantik
 * (der Compose-Compiler transformiert es), aber der Resolver reicht die **Referenz** unverändert
 * durch — genau das ist hier testbar und aussagekräftig.
 */
class SlotFallbackTest {

    @Test
    fun `fehlender header-slot fällt auf das default-pack zurück`() {
        val pack = UiSlotPack(header = null)

        val resolved = UiSlots.resolve(pack)

        assertSame(DefaultSlots.header, resolved.header)
    }

    @Test
    fun `gelieferter slot überschreibt den default`() {
        val custom: HeaderSlot = { _, _, _ -> }

        val resolved = UiSlots.resolve(UiSlotPack(header = custom))

        assertSame(custom, resolved.header)
    }

    @Test
    fun `fehlender homeHeader-slot fällt auf das default-pack zurück`() {
        val pack = UiSlotPack(homeHeader = null)

        val resolved = UiSlots.resolve(pack)

        assertSame(DefaultSlots.homeHeader, resolved.homeHeader)
    }

    @Test
    fun `gelieferter homeHeader-slot überschreibt den default`() {
        val custom: HomeHeaderSlot = { _ -> }

        val resolved = UiSlots.resolve(UiSlotPack(homeHeader = custom))

        assertSame(custom, resolved.homeHeader)
    }

    @Test
    fun `fehlender dialog-slot fällt auf das default-pack zurück`() {
        val pack = UiSlotPack(dialog = null)

        val resolved = UiSlots.resolve(pack)

        assertSame(DefaultSlots.dialog, resolved.dialog)
    }

    @Test
    fun `gelieferter dialog-slot überschreibt den default`() {
        val custom: DialogSlot = { _ -> }

        val resolved = UiSlots.resolve(UiSlotPack(dialog = custom))

        assertSame(custom, resolved.dialog)
    }

    @Test
    fun `fehlender settings-slot fällt auf das default-pack zurück`() {
        val pack = UiSlotPack(settings = null)

        val resolved = UiSlots.resolve(pack)

        assertSame(DefaultSlots.settings, resolved.settings)
    }

    @Test
    fun `gelieferter settings-slot überschreibt den default`() {
        val custom: SettingsSlot = { _ -> }

        val resolved = UiSlots.resolve(UiSlotPack(settings = custom))

        assertSame(custom, resolved.settings)
    }

    @Test
    fun `fehlender tiles-slot fällt auf das default-pack zurück`() {
        val pack = UiSlotPack(tiles = null)

        val resolved = UiSlots.resolve(pack)

        assertSame(DefaultSlots.tiles, resolved.tiles)
    }

    @Test
    fun `gelieferter tiles-slot überschreibt den default`() {
        val custom: TilesSlot = { _, _ -> }

        val resolved = UiSlots.resolve(UiSlotPack(tiles = custom))

        assertSame(custom, resolved.tiles)
    }
}
