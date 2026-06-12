// GENERIERT von tools/icons/generate.mjs — NICHT von Hand editieren.
// Quelle: Lucide (ISC), https://github.com/lucide-icons/lucide  ·  Stroke: siehe STROKE.
package com.komgareader.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Lucide-Glyphen mit E-Ink-Stroke. Stroke zentral hier tunbar — keine Neu-Generierung nötig. */
object LucideIcons {
    /** E-Ink-Stroke-Breite (Lucide-Default 2f; hier dicker für E-Ink-Sichtbarkeit). */
    const val STROKE: Float = 2.5f

    private fun lucide(name: String, pathData: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = PathParser().parsePathString(pathData).toNodes(),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ).build()

    /**
     * Gefüllte Variante eines (geschlossenen) Lucide-Pfads — als **Aktiv-Zustand** eines sonst
     * outline-gezeichneten Glyphs (z. B. Lesezeichen gesetzt). Füllung statt Strich; `Icon(…)`
     * tönt sie wie jeden anderen Glyph. Nur für geschlossene Pfade sinnvoll.
     */
    private fun lucideFilled(name: String, pathData: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = PathParser().parsePathString(pathData).toNodes(),
            fill = SolidColor(Color.Black),
        ).build()

    val AlignJustify: ImageVector by lazy { lucide("AlignJustify", "M3 12H21 M3 18H21 M3 6H21") }
    val AlignLeft: ImageVector by lazy { lucide("AlignLeft", "M15 12H3 M17 18H3 M21 6H3") }
    val ArrowLeft: ImageVector by lazy { lucide("ArrowLeft", "M12 19L5 12 12 5 M19 12H5") }
    val ArrowRight: ImageVector by lazy { lucide("ArrowRight", "M5 12H19 M12 5L19 12 12 19") }
    val BookOpen: ImageVector by lazy { lucide("BookOpen", "M12 7V21 M3 18A1 1 0 0 1 2 17V4A1 1 0 0 1 3 3H8A4 4 0 0 1 12 7 4 4 0 0 1 16 3H21A1 1 0 0 1 22 4V17A1 1 0 0 1 21 18H15A3 3 0 0 0 12 21 3 3 0 0 0 9 18Z") }
    val Bookmark: ImageVector by lazy { lucide("Bookmark", "M19 21L12 17 5 21V5A2 2 0 0 1 7 3H17A2 2 0 0 1 19 5V21Z") }
    val BookmarkFilled: ImageVector by lazy { lucideFilled("BookmarkFilled", "M19 21L12 17 5 21V5A2 2 0 0 1 7 3H17A2 2 0 0 1 19 5V21Z") }
    val Check: ImageVector by lazy { lucide("Check", "M20 6L9 17 4 12") }
    val ChevronDown: ImageVector by lazy { lucide("ChevronDown", "M6 9L12 15 18 9") }
    val ChevronRight: ImageVector by lazy { lucide("ChevronRight", "M9 18L15 12 9 6") }
    val ChevronUp: ImageVector by lazy { lucide("ChevronUp", "M18 15L12 9 6 15") }
    val CircleStop: ImageVector by lazy { lucide("CircleStop", "M2 12A10 10 0 1 0 22 12A10 10 0 1 0 2 12 M10 9H14A1 1 0 0 1 15 10V14A1 1 0 0 1 14 15H10A1 1 0 0 1 9 14V10A1 1 0 0 1 10 9Z") }
    val Cloud: ImageVector by lazy { lucide("Cloud", "M17.5 19H9A7 7 0 1 1 15.71 10H17.5A4.5 4.5 0 1 1 17.5 19Z") }
    val CloudDownload: ImageVector by lazy { lucide("CloudDownload", "M12 13V21L8 17 M12 21L16 17 M4.393 15.269A7 7 0 1 1 15.71 8H17.5A4.5 4.5 0 0 1 19.936 16.284") }
    val Contrast: ImageVector by lazy { lucide("Contrast", "M2 12A10 10 0 1 0 22 12A10 10 0 1 0 2 12 M12 18A6 6 0 0 0 12 6V18Z") }
    val Download: ImageVector by lazy { lucide("Download", "M21 15V19A2 2 0 0 1 19 21H5A2 2 0 0 1 3 19V15 M7 10L12 15L17 10 M12 15L12 3") }
    val EllipsisVertical: ImageVector by lazy { lucide("EllipsisVertical", "M11 12A1 1 0 1 0 13 12A1 1 0 1 0 11 12 M11 5A1 1 0 1 0 13 5A1 1 0 1 0 11 5 M11 19A1 1 0 1 0 13 19A1 1 0 1 0 11 19") }
    val GalleryVertical: ImageVector by lazy { lucide("GalleryVertical", "M3 2H21 M5 6H19A2 2 0 0 1 21 8V16A2 2 0 0 1 19 18H5A2 2 0 0 1 3 16V8A2 2 0 0 1 5 6Z M3 22H21") }
    val Grid2x2: ImageVector by lazy { lucide("Grid2x2", "M5 3H19A2 2 0 0 1 21 5V19A2 2 0 0 1 19 21H5A2 2 0 0 1 3 19V5A2 2 0 0 1 5 3Z M3 12H21 M12 3V21") }
    val HardDriveDownload: ImageVector by lazy { lucide("HardDriveDownload", "M12 2V10 M16 6L12 10 8 6 M4 14H20A2 2 0 0 1 22 16V20A2 2 0 0 1 20 22H4A2 2 0 0 1 2 20V16A2 2 0 0 1 4 14Z M6 18H6.01 M10 18H10.01") }
    val House: ImageVector by lazy { lucide("House", "M15 21V13A1 1 0 0 0 14 12H10A1 1 0 0 0 9 13V21 M3 10A2 2 0 0 1 3.709 8.472L10.709 2.473A2 2 0 0 1 13.291 2.473L20.291 8.472A2 2 0 0 1 21 10V19A2 2 0 0 1 19 21H5A2 2 0 0 1 3 19Z") }
    val Info: ImageVector by lazy { lucide("Info", "M2 12A10 10 0 1 0 22 12A10 10 0 1 0 2 12 M12 16V12 M12 8H12.01") }
    val Languages: ImageVector by lazy { lucide("Languages", "M5 8L11 14 M4 14L10 8 12 5 M2 5H14 M7 2H8 M22 22L17 12 12 22 M14 18H20") }
    val LayoutDashboard: ImageVector by lazy { lucide("LayoutDashboard", "M4 3H9A1 1 0 0 1 10 4V11A1 1 0 0 1 9 12H4A1 1 0 0 1 3 11V4A1 1 0 0 1 4 3Z M15 3H20A1 1 0 0 1 21 4V7A1 1 0 0 1 20 8H15A1 1 0 0 1 14 7V4A1 1 0 0 1 15 3Z M15 12H20A1 1 0 0 1 21 13V20A1 1 0 0 1 20 21H15A1 1 0 0 1 14 20V13A1 1 0 0 1 15 12Z M4 16H9A1 1 0 0 1 10 17V20A1 1 0 0 1 9 21H4A1 1 0 0 1 3 20V17A1 1 0 0 1 4 16Z") }
    val LayoutGrid: ImageVector by lazy { lucide("LayoutGrid", "M4 3H9A1 1 0 0 1 10 4V9A1 1 0 0 1 9 10H4A1 1 0 0 1 3 9V4A1 1 0 0 1 4 3Z M15 3H20A1 1 0 0 1 21 4V9A1 1 0 0 1 20 10H15A1 1 0 0 1 14 9V4A1 1 0 0 1 15 3Z M15 14H20A1 1 0 0 1 21 15V20A1 1 0 0 1 20 21H15A1 1 0 0 1 14 20V15A1 1 0 0 1 15 14Z M4 14H9A1 1 0 0 1 10 15V20A1 1 0 0 1 9 21H4A1 1 0 0 1 3 20V15A1 1 0 0 1 4 14Z") }
    val Library: ImageVector by lazy { lucide("Library", "M16 6L20 20 M12 6V20 M8 8V20 M4 4V20") }
    val List: ImageVector by lazy { lucide("List", "M3 12H3.01 M3 18H3.01 M3 6H3.01 M8 12H21 M8 18H21 M8 6H21") }
    val ListFilter: ImageVector by lazy { lucide("ListFilter", "M3 6H21 M7 12H17 M10 18H14") }
    val Minus: ImageVector by lazy { lucide("Minus", "M5 12H19") }
    val Palette: ImageVector by lazy { lucide("Palette", "M13 6.5A0.5 0.5 0 1 0 14 6.5A0.5 0.5 0 1 0 13 6.5 M17 10.5A0.5 0.5 0 1 0 18 10.5A0.5 0.5 0 1 0 17 10.5 M8 7.5A0.5 0.5 0 1 0 9 7.5A0.5 0.5 0 1 0 8 7.5 M6 12.5A0.5 0.5 0 1 0 7 12.5A0.5 0.5 0 1 0 6 12.5 M12 2C6.5 2 2 6.5 2 12S6.5 22 12 22C12.926 22 13.648 21.254 13.648 20.312 13.648 19.875 13.468 19.477 13.211 19.187 12.921000000000001 18.898 12.773 18.535 12.773 18.062A1.64 1.64 0 0 1 14.440999999999999 16.394000000000002H16.436999999999998C19.488 16.394000000000002 21.991999999999997 13.891000000000002 21.991999999999997 10.840000000000002 21.965 6.012 17.461 2 12 2Z") }
    val Plus: ImageVector by lazy { lucide("Plus", "M5 12H19 M12 5V19") }
    val Puzzle: ImageVector by lazy { lucide("Puzzle", "M15.39 4.39A1 1 0 0 0 17.07 3.9159999999999995 2.5 2.5 0 1 1 20.084 6.930999999999999 1 1 0 0 0 19.61 8.610999999999999L21.293 10.293A2.414 2.414 0 0 1 21.293 13.706999999999999L19.61 15.39A1 1 0 0 1 17.93 14.916 2.5 2.5 0 1 0 14.916 17.931 1 1 0 0 1 15.39 19.611L13.707 21.293A2.414 2.414 0 0 1 10.293000000000001 21.293L8.61 19.61A1 1 0 0 0 6.93 20.084 2.5 2.5 0 1 1 3.916 17.069 1 1 0 0 0 4.39 15.389L2.707 13.706999999999999A2.414 2.414 0 0 1 2.707 10.293L4.39 8.61A1 1 0 0 1 6.069999999999999 9.084 2.5 2.5 0 1 0 9.084 6.068999999999999 1 1 0 0 1 8.61 4.388999999999999L10.293 2.7069999999999994A2.414 2.414 0 0 1 13.706999999999999 2.7069999999999994Z") }
    val RefreshCw: ImageVector by lazy { lucide("RefreshCw", "M3 12A9 9 0 0 1 12 3 9.75 9.75 0 0 1 18.740000000000002 5.74L21 8 M21 3V8H16 M21 12A9 9 0 0 1 12 21 9.75 9.75 0 0 1 5.26 18.259999999999998L3 16 M8 16H3V21") }
    val Search: ImageVector by lazy { lucide("Search", "M3 11A8 8 0 1 0 19 11A8 8 0 1 0 3 11 M21 21L16.7 16.7") }
    val Server: ImageVector by lazy { lucide("Server", "M4 2H20A2 2 0 0 1 22 4V8A2 2 0 0 1 20 10H4A2 2 0 0 1 2 8V4A2 2 0 0 1 4 2Z M4 14H20A2 2 0 0 1 22 16V20A2 2 0 0 1 20 22H4A2 2 0 0 1 2 20V16A2 2 0 0 1 4 14Z M6 6L6.01 6 M6 18L6.01 18") }
    val Settings: ImageVector by lazy { lucide("Settings", "M12.22 2H11.780000000000001A2 2 0 0 0 9.780000000000001 4V4.18A2 2 0 0 1 8.780000000000001 5.91L8.350000000000001 6.16A2 2 0 0 1 6.350000000000001 6.16L6.200000000000001 6.08A2 2 0 0 0 3.470000000000001 6.8100000000000005L3.250000000000001 7.19A2 2 0 0 0 3.980000000000001 9.92L4.130000000000001 10.02A2 2 0 0 1 5.130000000000001 11.74V12.25A2 2 0 0 1 4.130000000000001 13.99L3.980000000000001 14.08A2 2 0 0 0 3.250000000000001 16.81L3.470000000000001 17.189999999999998A2 2 0 0 0 6.200000000000001 17.919999999999998L6.350000000000001 17.84A2 2 0 0 1 8.350000000000001 17.84L8.780000000000001 18.09A2 2 0 0 1 9.780000000000001 19.82V20A2 2 0 0 0 11.780000000000001 22H12.22A2 2 0 0 0 14.22 20V19.82A2 2 0 0 1 15.22 18.09L15.65 17.84A2 2 0 0 1 17.65 17.84L17.799999999999997 17.919999999999998A2 2 0 0 0 20.529999999999998 17.189999999999998L20.749999999999996 16.799999999999997A2 2 0 0 0 20.019999999999996 14.069999999999997L19.869999999999997 13.989999999999997A2 2 0 0 1 18.869999999999997 12.249999999999996V11.749999999999996A2 2 0 0 1 19.869999999999997 10.009999999999996L20.019999999999996 9.919999999999996A2 2 0 0 0 20.749999999999996 7.189999999999996L20.529999999999998 6.809999999999996A2 2 0 0 0 17.799999999999997 6.0799999999999965L17.65 6.159999999999997A2 2 0 0 1 15.649999999999999 6.159999999999997L15.219999999999999 5.909999999999997A2 2 0 0 1 14.219999999999999 4.179999999999996V4A2 2 0 0 0 12.219999999999999 2Z M9 12A3 3 0 1 0 15 12A3 3 0 1 0 9 12") }
    val SquarePen: ImageVector by lazy { lucide("SquarePen", "M12 3H5A2 2 0 0 0 3 5V19A2 2 0 0 0 5 21H19A2 2 0 0 0 21 19V12 M18.375 2.625A1 1 0 0 1 21.375 5.625L12.362 14.639A2 2 0 0 1 11.509 15.144L8.636 15.984A0.5 0.5 0 0 1 8.016 15.364L8.856 12.491A2 2 0 0 1 9.362 11.639Z") }
    val Trash2: ImageVector by lazy { lucide("Trash2", "M3 6H21 M19 6V20C19 21 18 22 17 22H7C6 22 5 21 5 20V6 M8 6V4C8 3 9 2 10 2H14C15 2 16 3 16 4V6 M10 11L10 17 M14 11L14 17") }
    val Type: ImageVector by lazy { lucide("Type", "M4 7L4 4L20 4L20 7 M9 20L15 20 M12 4L12 20") }
    val X: ImageVector by lazy { lucide("X", "M18 6L6 18 M6 6L18 18") }
}
