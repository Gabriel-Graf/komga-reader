package com.komgareader.ui.icons

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Zentrale Icon-Registry (Single Source of Truth). Die UI nutzt **nur** `AppIcons.*` —
 * nie `LucideIcons.*` oder `androidx.compose.material.icons.*` direkt. Namen sind
 * **semantisch** (Zweck), nicht nach Glyph benannt: so kann der konkrete Glyph zentral
 * getauscht werden, ohne Aufruf-Stellen anzufassen.
 *
 * Jede Property delegiert über [ActiveIconPack]/[IconKey] an das **aktive Icon-Pack**
 * (Default = [DefaultIconPack], die heutige Lucide-Zuordnung). Ein Pack kann die Glyphen
 * app-weit ersetzen, ohne diese Call-Sites zu berühren.
 *
 * Neuer Bedarf: einen [IconKey] ergänzen, das Mapping in [DefaultIconPack] hinzufügen und
 * hier eine delegierende Property anlegen (fehlt der Glyph, in `tools/icons/icon-set.mjs`
 * ergänzen und `npm run generate`).
 */
object AppIcons {
    val Close: ImageVector get() = ActiveIconPack.resolve(IconKey.Close)
    val Back: ImageVector get() = ActiveIconPack.resolve(IconKey.Back)
    val Forward: ImageVector get() = ActiveIconPack.resolve(IconKey.Forward)
    val Check: ImageVector get() = ActiveIconPack.resolve(IconKey.Check)
    val Plus: ImageVector get() = ActiveIconPack.resolve(IconKey.Plus)
    val Minus: ImageVector get() = ActiveIconPack.resolve(IconKey.Minus)
    val ChevronRight: ImageVector get() = ActiveIconPack.resolve(IconKey.ChevronRight)
    val ChevronDown: ImageVector get() = ActiveIconPack.resolve(IconKey.ChevronDown)
    val ChevronUp: ImageVector get() = ActiveIconPack.resolve(IconKey.ChevronUp)
    val Home: ImageVector get() = ActiveIconPack.resolve(IconKey.Home)
    val Search: ImageVector get() = ActiveIconPack.resolve(IconKey.Search)
    val Refresh: ImageVector get() = ActiveIconPack.resolve(IconKey.Refresh)
    val Edit: ImageVector get() = ActiveIconPack.resolve(IconKey.Edit)
    val Settings: ImageVector get() = ActiveIconPack.resolve(IconKey.Settings)
    val Delete: ImageVector get() = ActiveIconPack.resolve(IconKey.Delete)
    val Download: ImageVector get() = ActiveIconPack.resolve(IconKey.Download)
    val Local: ImageVector get() = ActiveIconPack.resolve(IconKey.Local)
    val Cloud: ImageVector get() = ActiveIconPack.resolve(IconKey.Cloud)
    val Info: ImageVector get() = ActiveIconPack.resolve(IconKey.Info)
    val Filter: ImageVector get() = ActiveIconPack.resolve(IconKey.Filter)
    val Overflow: ImageVector get() = ActiveIconPack.resolve(IconKey.Overflow)
    val Stop: ImageVector get() = ActiveIconPack.resolve(IconKey.Stop)
    val GridView: ImageVector get() = ActiveIconPack.resolve(IconKey.GridView)

    /** Größeres Cover-Gitter (3er-Raster) — Drittstufe des Ansichts-Umschalters. */
    val LargeGridView: ImageVector get() = ActiveIconPack.resolve(IconKey.LargeGridView)
    val ListView: ImageVector get() = ActiveIconPack.resolve(IconKey.ListView)
    val Bookmark: ImageVector get() = ActiveIconPack.resolve(IconKey.Bookmark)

    /** Aktiv-Zustand des Lesezeichens: gefüllt, wenn das Werk in mindestens einer Collection liegt. */
    val BookmarkFilled: ImageVector get() = ActiveIconPack.resolve(IconKey.BookmarkFilled)
    val Library: ImageVector get() = ActiveIconPack.resolve(IconKey.Library)
    val Groups: ImageVector get() = ActiveIconPack.resolve(IconKey.Groups)
    val Plugins: ImageVector get() = ActiveIconPack.resolve(IconKey.Plugins)
    val Contrast: ImageVector get() = ActiveIconPack.resolve(IconKey.Contrast)
    val Palette: ImageVector get() = ActiveIconPack.resolve(IconKey.Palette)
    val Reader: ImageVector get() = ActiveIconPack.resolve(IconKey.Reader)
    val Language: ImageVector get() = ActiveIconPack.resolve(IconKey.Language)
    val Connection: ImageVector get() = ActiveIconPack.resolve(IconKey.Connection)
    val ReaderMode: ImageVector get() = ActiveIconPack.resolve(IconKey.ReaderMode)
    val PanelMode: ImageVector get() = ActiveIconPack.resolve(IconKey.PanelMode)
    val Typography: ImageVector get() = ActiveIconPack.resolve(IconKey.Typography)
    val TableOfContents: ImageVector get() = ActiveIconPack.resolve(IconKey.TableOfContents)
    val AlignLeft: ImageVector get() = ActiveIconPack.resolve(IconKey.AlignLeft)
    val AlignJustify: ImageVector get() = ActiveIconPack.resolve(IconKey.AlignJustify)
    val PasswordShow: ImageVector get() = ActiveIconPack.resolve(IconKey.PasswordShow)
    val PasswordHide: ImageVector get() = ActiveIconPack.resolve(IconKey.PasswordHide)
    val Stats: ImageVector get() = ActiveIconPack.resolve(IconKey.Stats)
}
