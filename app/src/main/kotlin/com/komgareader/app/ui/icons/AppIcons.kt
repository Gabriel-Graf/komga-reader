package com.komgareader.app.ui.icons

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Zentrale Icon-Registry (Single Source of Truth). Die UI nutzt **nur** `AppIcons.*` —
 * nie `LucideIcons.*` oder `androidx.compose.material.icons.*` direkt. Namen sind
 * **semantisch** (Zweck), nicht nach Glyph benannt: so kann der konkrete Glyph zentral
 * getauscht werden, ohne Aufruf-Stellen anzufassen.
 *
 * Neuer Bedarf: hier einen semantischen Namen ergänzen und auf einen Lucide-Glyph zeigen
 * (fehlt der Glyph, in `tools/icons/icon-set.mjs` ergänzen und `npm run generate`).
 */
object AppIcons {
    val Close: ImageVector get() = LucideIcons.X
    val Back: ImageVector get() = LucideIcons.ArrowLeft
    val Forward: ImageVector get() = LucideIcons.ArrowRight
    val Check: ImageVector get() = LucideIcons.Check
    val Plus: ImageVector get() = LucideIcons.Plus
    val Minus: ImageVector get() = LucideIcons.Minus
    val ChevronRight: ImageVector get() = LucideIcons.ChevronRight
    val ChevronDown: ImageVector get() = LucideIcons.ChevronDown
    val ChevronUp: ImageVector get() = LucideIcons.ChevronUp
    val Search: ImageVector get() = LucideIcons.Search
    val Refresh: ImageVector get() = LucideIcons.RefreshCw
    val Edit: ImageVector get() = LucideIcons.SquarePen
    val Settings: ImageVector get() = LucideIcons.Settings
    val Delete: ImageVector get() = LucideIcons.Trash2
    val Download: ImageVector get() = LucideIcons.CloudDownload
    val Local: ImageVector get() = LucideIcons.HardDriveDownload
    val Cloud: ImageVector get() = LucideIcons.Cloud
    val Info: ImageVector get() = LucideIcons.Info
    val Filter: ImageVector get() = LucideIcons.ListFilter
    val Overflow: ImageVector get() = LucideIcons.EllipsisVertical
    val Stop: ImageVector get() = LucideIcons.CircleStop
    val GridView: ImageVector get() = LucideIcons.LayoutGrid
    val ListView: ImageVector get() = LucideIcons.List
    val Bookmark: ImageVector get() = LucideIcons.Bookmark
    val Library: ImageVector get() = LucideIcons.Library
    val Groups: ImageVector get() = LucideIcons.LayoutDashboard
    val Plugins: ImageVector get() = LucideIcons.Puzzle
    val Contrast: ImageVector get() = LucideIcons.Contrast
    val Palette: ImageVector get() = LucideIcons.Palette
    val Reader: ImageVector get() = LucideIcons.BookOpen
    val Language: ImageVector get() = LucideIcons.Languages
    val Connection: ImageVector get() = LucideIcons.Server
    val ReaderMode: ImageVector get() = LucideIcons.GalleryVertical
    val PanelMode: ImageVector get() = LucideIcons.Grid2x2
    val Typography: ImageVector get() = LucideIcons.Type
    val AlignLeft: ImageVector get() = LucideIcons.AlignLeft
    val AlignJustify: ImageVector get() = LucideIcons.AlignJustify
}
