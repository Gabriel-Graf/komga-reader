package com.komgareader.ui.icons

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Typsicherer Schlüssel mit **genau einem Eintrag pro [AppIcons]-Property** (gleiche Namen).
 * Ein Icon-Pack wird gegen diese Schlüssel abgefragt, nicht gegen die semantischen Properties —
 * so bleibt die Zuordnung Glyph⟷Bedeutung an genau einer Stelle ([DefaultIconPack]).
 *
 * Neuer Bedarf: hier einen Schlüssel ergänzen **und** das Mapping in [DefaultIconPack] (Glyph ggf.
 * via `tools/icons` generieren). Der `default-pack deckt jeden IconKey ab`-Test fängt ein
 * vergessenes Mapping.
 */
enum class IconKey {
    Close, Back, Forward, Check, Plus, Minus, ChevronRight, ChevronDown, ChevronUp, Home, Search,
    Refresh, Edit, Settings, Delete, Download, Local, Cloud, Info, Filter, Overflow, Stop, GridView,
    LargeGridView, ListView, Bookmark, BookmarkFilled, Library, Groups, Plugins, Contrast, Palette,
    Reader, Language, Connection, ReaderMode, PanelMode, Typography, TableOfContents, AlignLeft, AlignJustify,
    PasswordShow, PasswordHide, Stats, Folder,
}

/**
 * Ein Icon-Pack liefert Glyphen für die semantischen [IconKey]s. Rückgabe `null` = diesen Key
 * **nicht** überschreiben → Fallback auf das [DefaultIconPack] (analog `StubSource`/Slot-Fallback).
 * So kann ein Teil-Pack nur einige Glyphen ersetzen, ohne ein Loch zu lassen.
 */
fun interface IconPack {
    fun resolve(key: IconKey): ImageVector?
}

/**
 * Das mitgelieferte Default-Pack: die heutige Lucide-Zuordnung — die **eine** Map (kein Duplikat in
 * [AppIcons]). [resolve] ist hier non-null (deckt alle [IconKey]s ab); der [IconPack]-Vertrag erlaubt
 * aber `null`, weil Teil-Packs nur einige Keys überschreiben.
 */
object DefaultIconPack : IconPack {
    override fun resolve(key: IconKey): ImageVector = when (key) {
        IconKey.Close -> LucideIcons.X
        IconKey.Back -> LucideIcons.ArrowLeft
        IconKey.Forward -> LucideIcons.ArrowRight
        IconKey.Check -> LucideIcons.Check
        IconKey.Plus -> LucideIcons.Plus
        IconKey.Minus -> LucideIcons.Minus
        IconKey.ChevronRight -> LucideIcons.ChevronRight
        IconKey.ChevronDown -> LucideIcons.ChevronDown
        IconKey.ChevronUp -> LucideIcons.ChevronUp
        IconKey.Home -> LucideIcons.House
        IconKey.Search -> LucideIcons.Search
        IconKey.Refresh -> LucideIcons.RefreshCw
        IconKey.Edit -> LucideIcons.SquarePen
        IconKey.Settings -> LucideIcons.Settings
        IconKey.Delete -> LucideIcons.Trash2
        IconKey.Download -> LucideIcons.CloudDownload
        IconKey.Local -> LucideIcons.HardDriveDownload
        IconKey.Cloud -> LucideIcons.Cloud
        IconKey.Info -> LucideIcons.Info
        IconKey.Filter -> LucideIcons.ListFilter
        IconKey.Overflow -> LucideIcons.EllipsisVertical
        IconKey.Stop -> LucideIcons.CircleStop
        IconKey.GridView -> LucideIcons.LayoutGrid
        IconKey.LargeGridView -> LucideIcons.Grid2x2 // bewusst derselbe Glyph wie PanelMode (2×2-Gitter)
        IconKey.ListView -> LucideIcons.List
        IconKey.Bookmark -> LucideIcons.Bookmark
        IconKey.BookmarkFilled -> LucideIcons.BookmarkFilled
        IconKey.Library -> LucideIcons.Library
        IconKey.Groups -> LucideIcons.LayoutDashboard
        IconKey.Plugins -> LucideIcons.Puzzle
        IconKey.Contrast -> LucideIcons.Contrast
        IconKey.Palette -> LucideIcons.Palette
        IconKey.Reader -> LucideIcons.BookOpen
        IconKey.Language -> LucideIcons.Languages
        IconKey.Connection -> LucideIcons.Server
        IconKey.ReaderMode -> LucideIcons.GalleryVertical
        IconKey.PanelMode -> LucideIcons.Grid2x2 // bewusst derselbe Glyph wie LargeGridView
        IconKey.Typography -> LucideIcons.Type
        IconKey.TableOfContents -> LucideIcons.List // bewusst derselbe Glyph wie ListView
        IconKey.AlignLeft -> LucideIcons.AlignLeft
        IconKey.AlignJustify -> LucideIcons.AlignJustify
        IconKey.PasswordShow -> LucideIcons.Eye
        IconKey.PasswordHide -> LucideIcons.EyeOff
        IconKey.Stats -> LucideIcons.BarChart3
        IconKey.Folder -> LucideIcons.Folder
    }
}

/**
 * App-weiter, austauschbarer Icon-Pack-Halter. [current] wird app-weit gesetzt (z. B. am Start aus
 * einer Einstellung, später vom Pack-Lader). Bewusst **prozess-global** statt `CompositionLocal`,
 * weil [AppIcons] auch außerhalb von Composition gelesen wird (Datenklassen-Felder, Default-Argumente)
 * — Spec §2. [resolve] fällt pro Key sauber auf [DefaultIconPack] zurück, sodass ein Teil-Pack nie
 * ein Loch lässt.
 *
 * **Einschränkung:** Ein Wechsel von [current] löst **keine** Recomposition aus (globaler `var`, kein
 * `MutableState`). Für einmaliges Setzen am App-Start ist das unerheblich; ein Laufzeit-Tausch (mit dem
 * späteren Pack-Lader, L1/L2) muss die Re-Composition selbst anstoßen — z. B. `MutableState`-Wrapper
 * oder Activity-Neustart.
 */
object ActiveIconPack {
    @Volatile
    var current: IconPack = DefaultIconPack

    fun resolve(key: IconKey): ImageVector = current.resolve(key) ?: DefaultIconPack.resolve(key)
}
