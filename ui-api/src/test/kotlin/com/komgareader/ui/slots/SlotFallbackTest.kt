package com.komgareader.ui.slots

import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * Die UI-Slot-Naht ist das Gegenstück zur Theme-Pack-Naht ([com.komgareader.ui.theme.UiPack]),
 * eine Ebene tiefer: nicht *wie* der Look (Farbe/Typo/Token), sondern *welcher Baustein* eine
 * adressierbare Chrome-Region füllt. Gebaute Regionen: **header** (erste) + **homeHeader** (zweite)
 * + **dialog** (dritte) + **settings** (vierte) + **tiles** (fünfte) + **overlay** (sechste) +
 * **detail** (siebte: das Vollbild-Detail-Gerüst) + **readerChrome** (achte: das ganze Reader-Gerüst).
 *
 * Geprüft wird die **Auflösungs-Logik** [UiSlots.resolve] als pure Funktion über nullbare
 * Referenzen: ein fehlender Slot fällt auf die übergebenen Default-Slots zurück (nie `null`, analog
 * `StubSource`), ein gelieferter überschreibt den Default. Die Defaults liefert in A1 der Host
 * (app-`DefaultSlots`) — der Onyx-Look koppelt an app-i18n/-Komponenten und liegt darum nicht im
 * Modul `:ui-api`. Dieser Test arbeitet deshalb mit **trivialen Fake-Slots** statt den echten
 * Renderern.
 *
 * Bewusst über **referenzielle Identität** (`assertSame`), nicht inhaltliche Gleichheit: ein
 * `@Composable`-Lambda hat keine verlässliche `equals`-Semantik (der Compose-Compiler transformiert
 * es), aber der Resolver reicht die **Referenz** unverändert durch — genau das ist hier testbar und
 * aussagekräftig.
 */
class SlotFallbackTest {

    // Triviale, eindeutig identifizierbare Fake-Default-Slots (statt der app-gekoppelten DefaultSlots).
    private val fakeHeader: HeaderSlot = { _ -> }
    private val fakeHomeHeader: HomeHeaderSlot = { _ -> }
    private val fakeDialog: DialogSlot = { _ -> }
    private val fakeSettings: SettingsSlot = { _ -> }
    private val fakeTiles: TilesSlot = { _, _ -> }
    private val fakeOverlay: OverlaySlot = { _ -> }
    private val fakeDetail: DetailSlot = { _ -> }
    private val fakeReaderChrome: ReaderChromeSlot = { _ -> }

    private val fakeDefaults = ResolvedSlots(
        headerSlot = fakeHeader,
        homeHeader = fakeHomeHeader,
        dialog = fakeDialog,
        settings = fakeSettings,
        tiles = fakeTiles,
        overlay = fakeOverlay,
        detail = fakeDetail,
        readerChrome = fakeReaderChrome,
    )

    @Test
    fun `fehlender header-slot fällt auf das default-pack zurück`() {
        val pack = UiSlotPack(header = null)

        val resolved = UiSlots.resolve(pack, fakeDefaults)

        assertSame(fakeHeader, resolved.headerSlot)
    }

    @Test
    fun `gelieferter slot überschreibt den default`() {
        val custom: HeaderSlot = { _ -> }

        val resolved = UiSlots.resolve(UiSlotPack(header = custom), fakeDefaults)

        assertSame(custom, resolved.headerSlot)
    }

    @Test
    fun `fehlender homeHeader-slot fällt auf das default-pack zurück`() {
        val pack = UiSlotPack(homeHeader = null)

        val resolved = UiSlots.resolve(pack, fakeDefaults)

        assertSame(fakeHomeHeader, resolved.homeHeader)
    }

    @Test
    fun `gelieferter homeHeader-slot überschreibt den default`() {
        val custom: HomeHeaderSlot = { _ -> }

        val resolved = UiSlots.resolve(UiSlotPack(homeHeader = custom), fakeDefaults)

        assertSame(custom, resolved.homeHeader)
    }

    @Test
    fun `fehlender dialog-slot fällt auf das default-pack zurück`() {
        val pack = UiSlotPack(dialog = null)

        val resolved = UiSlots.resolve(pack, fakeDefaults)

        assertSame(fakeDialog, resolved.dialog)
    }

    @Test
    fun `gelieferter dialog-slot überschreibt den default`() {
        val custom: DialogSlot = { _ -> }

        val resolved = UiSlots.resolve(UiSlotPack(dialog = custom), fakeDefaults)

        assertSame(custom, resolved.dialog)
    }

    @Test
    fun `fehlender settings-slot fällt auf das default-pack zurück`() {
        val pack = UiSlotPack(settings = null)

        val resolved = UiSlots.resolve(pack, fakeDefaults)

        assertSame(fakeSettings, resolved.settings)
    }

    @Test
    fun `gelieferter settings-slot überschreibt den default`() {
        val custom: SettingsSlot = { _ -> }

        val resolved = UiSlots.resolve(UiSlotPack(settings = custom), fakeDefaults)

        assertSame(custom, resolved.settings)
    }

    @Test
    fun `fehlender tiles-slot fällt auf das default-pack zurück`() {
        val pack = UiSlotPack(tiles = null)

        val resolved = UiSlots.resolve(pack, fakeDefaults)

        assertSame(fakeTiles, resolved.tiles)
    }

    @Test
    fun `gelieferter tiles-slot überschreibt den default`() {
        val custom: TilesSlot = { _, _ -> }

        val resolved = UiSlots.resolve(UiSlotPack(tiles = custom), fakeDefaults)

        assertSame(custom, resolved.tiles)
    }

    @Test
    fun `fehlender overlay-slot fällt auf das default-pack zurück`() {
        val pack = UiSlotPack(overlay = null)

        val resolved = UiSlots.resolve(pack, fakeDefaults)

        assertSame(fakeOverlay, resolved.overlay)
    }

    @Test
    fun `gelieferter overlay-slot überschreibt den default`() {
        val custom: OverlaySlot = { _ -> }

        val resolved = UiSlots.resolve(UiSlotPack(overlay = custom), fakeDefaults)

        assertSame(custom, resolved.overlay)
    }

    @Test
    fun `fehlender detail-slot fällt auf das default-pack zurück`() {
        val pack = UiSlotPack(detail = null)

        val resolved = UiSlots.resolve(pack, fakeDefaults)

        assertSame(fakeDetail, resolved.detail)
    }

    @Test
    fun `gelieferter detail-slot überschreibt den default`() {
        val custom: DetailSlot = { _: DetailScaffoldState -> }

        val resolved = UiSlots.resolve(UiSlotPack(detail = custom), fakeDefaults)

        assertSame(custom, resolved.detail)
    }

    @Test
    fun `fehlender readerChrome-slot fällt auf das default-pack zurück`() {
        val pack = UiSlotPack(readerChrome = null)

        val resolved = UiSlots.resolve(pack, fakeDefaults)

        assertSame(fakeReaderChrome, resolved.readerChrome)
    }

    @Test
    fun `gelieferter readerChrome-slot überschreibt den default`() {
        val custom: ReaderChromeSlot = { _: ReaderScaffoldState -> }

        val resolved = UiSlots.resolve(UiSlotPack(readerChrome = custom), fakeDefaults)

        assertSame(custom, resolved.readerChrome)
    }
}
