// GENERIERT von tools/icons/generate.mjs — NICHT von Hand editieren.
// Quelle: Lucide (ISC), https://github.com/lucide-icons/lucide  ·  Stroke: siehe STROKE.
package com.komgareader.app.ui.icons

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

    val ArrowLeft: ImageVector by lazy { lucide("ArrowLeft", "m12 19-7-7 7-7 M19 12H5") }
    val ArrowRight: ImageVector by lazy { lucide("ArrowRight", "M5 12h14 m12 5 7 7-7 7") }
    val BookOpen: ImageVector by lazy { lucide("BookOpen", "M12 7v14 M3 18a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1h5a4 4 0 0 1 4 4 4 4 0 0 1 4-4h5a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1h-6a3 3 0 0 0-3 3 3 3 0 0 0-3-3z") }
    val Bookmark: ImageVector by lazy { lucide("Bookmark", "m19 21-7-4-7 4V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v16z") }
    val Check: ImageVector by lazy { lucide("Check", "M20 6 9 17l-5-5") }
    val ChevronDown: ImageVector by lazy { lucide("ChevronDown", "m6 9 6 6 6-6") }
    val ChevronRight: ImageVector by lazy { lucide("ChevronRight", "m9 18 6-6-6-6") }
    val ChevronUp: ImageVector by lazy { lucide("ChevronUp", "m18 15-6-6-6 6") }
    val CircleStop: ImageVector by lazy { lucide("CircleStop", "M2 12A10 10 0 1 0 22 12A10 10 0 1 0 2 12 M10 9H14A1 1 0 0 1 15 10V14A1 1 0 0 1 14 15H10A1 1 0 0 1 9 14V10A1 1 0 0 1 10 9Z") }
    val Cloud: ImageVector by lazy { lucide("Cloud", "M17.5 19H9a7 7 0 1 1 6.71-9h1.79a4.5 4.5 0 1 1 0 9Z") }
    val CloudDownload: ImageVector by lazy { lucide("CloudDownload", "M12 13v8l-4-4 m12 21 4-4 M4.393 15.269A7 7 0 1 1 15.71 8h1.79a4.5 4.5 0 0 1 2.436 8.284") }
    val Contrast: ImageVector by lazy { lucide("Contrast", "M2 12A10 10 0 1 0 22 12A10 10 0 1 0 2 12 M12 18a6 6 0 0 0 0-12v12z") }
    val Download: ImageVector by lazy { lucide("Download", "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4 M7 10L12 15L17 10 M12 15L12 3") }
    val EllipsisVertical: ImageVector by lazy { lucide("EllipsisVertical", "M11 12A1 1 0 1 0 13 12A1 1 0 1 0 11 12 M11 5A1 1 0 1 0 13 5A1 1 0 1 0 11 5 M11 19A1 1 0 1 0 13 19A1 1 0 1 0 11 19") }
    val GalleryVertical: ImageVector by lazy { lucide("GalleryVertical", "M3 2h18 M5 6H19A2 2 0 0 1 21 8V16A2 2 0 0 1 19 18H5A2 2 0 0 1 3 16V8A2 2 0 0 1 5 6Z M3 22h18") }
    val Grid2x2: ImageVector by lazy { lucide("Grid2x2", "M5 3H19A2 2 0 0 1 21 5V19A2 2 0 0 1 19 21H5A2 2 0 0 1 3 19V5A2 2 0 0 1 5 3Z M3 12h18 M12 3v18") }
    val HardDriveDownload: ImageVector by lazy { lucide("HardDriveDownload", "M12 2v8 m16 6-4 4-4-4 M4 14H20A2 2 0 0 1 22 16V20A2 2 0 0 1 20 22H4A2 2 0 0 1 2 20V16A2 2 0 0 1 4 14Z M6 18h.01 M10 18h.01") }
    val Info: ImageVector by lazy { lucide("Info", "M2 12A10 10 0 1 0 22 12A10 10 0 1 0 2 12 M12 16v-4 M12 8h.01") }
    val Languages: ImageVector by lazy { lucide("Languages", "m5 8 6 6 m4 14 6-6 2-3 M2 5h12 M7 2h1 m22 22-5-10-5 10 M14 18h6") }
    val LayoutDashboard: ImageVector by lazy { lucide("LayoutDashboard", "M4 3H9A1 1 0 0 1 10 4V11A1 1 0 0 1 9 12H4A1 1 0 0 1 3 11V4A1 1 0 0 1 4 3Z M15 3H20A1 1 0 0 1 21 4V7A1 1 0 0 1 20 8H15A1 1 0 0 1 14 7V4A1 1 0 0 1 15 3Z M15 12H20A1 1 0 0 1 21 13V20A1 1 0 0 1 20 21H15A1 1 0 0 1 14 20V13A1 1 0 0 1 15 12Z M4 16H9A1 1 0 0 1 10 17V20A1 1 0 0 1 9 21H4A1 1 0 0 1 3 20V17A1 1 0 0 1 4 16Z") }
    val LayoutGrid: ImageVector by lazy { lucide("LayoutGrid", "M4 3H9A1 1 0 0 1 10 4V9A1 1 0 0 1 9 10H4A1 1 0 0 1 3 9V4A1 1 0 0 1 4 3Z M15 3H20A1 1 0 0 1 21 4V9A1 1 0 0 1 20 10H15A1 1 0 0 1 14 9V4A1 1 0 0 1 15 3Z M15 14H20A1 1 0 0 1 21 15V20A1 1 0 0 1 20 21H15A1 1 0 0 1 14 20V15A1 1 0 0 1 15 14Z M4 14H9A1 1 0 0 1 10 15V20A1 1 0 0 1 9 21H4A1 1 0 0 1 3 20V15A1 1 0 0 1 4 14Z") }
    val Library: ImageVector by lazy { lucide("Library", "m16 6 4 14 M12 6v14 M8 8v12 M4 4v16") }
    val List: ImageVector by lazy { lucide("List", "M3 12h.01 M3 18h.01 M3 6h.01 M8 12h13 M8 18h13 M8 6h13") }
    val ListFilter: ImageVector by lazy { lucide("ListFilter", "M3 6h18 M7 12h10 M10 18h4") }
    val Minus: ImageVector by lazy { lucide("Minus", "M5 12h14") }
    val Palette: ImageVector by lazy { lucide("Palette", "M13 6.5A0.5 0.5 0 1 0 14 6.5A0.5 0.5 0 1 0 13 6.5 M17 10.5A0.5 0.5 0 1 0 18 10.5A0.5 0.5 0 1 0 17 10.5 M8 7.5A0.5 0.5 0 1 0 9 7.5A0.5 0.5 0 1 0 8 7.5 M6 12.5A0.5 0.5 0 1 0 7 12.5A0.5 0.5 0 1 0 6 12.5 M12 2C6.5 2 2 6.5 2 12s4.5 10 10 10c.926 0 1.648-.746 1.648-1.688 0-.437-.18-.835-.437-1.125-.29-.289-.438-.652-.438-1.125a1.64 1.64 0 0 1 1.668-1.668h1.996c3.051 0 5.555-2.503 5.555-5.554C21.965 6.012 17.461 2 12 2z") }
    val Plus: ImageVector by lazy { lucide("Plus", "M5 12h14 M12 5v14") }
    val Puzzle: ImageVector by lazy { lucide("Puzzle", "M15.39 4.39a1 1 0 0 0 1.68-.474 2.5 2.5 0 1 1 3.014 3.015 1 1 0 0 0-.474 1.68l1.683 1.682a2.414 2.414 0 0 1 0 3.414L19.61 15.39a1 1 0 0 1-1.68-.474 2.5 2.5 0 1 0-3.014 3.015 1 1 0 0 1 .474 1.68l-1.683 1.682a2.414 2.414 0 0 1-3.414 0L8.61 19.61a1 1 0 0 0-1.68.474 2.5 2.5 0 1 1-3.014-3.015 1 1 0 0 0 .474-1.68l-1.683-1.682a2.414 2.414 0 0 1 0-3.414L4.39 8.61a1 1 0 0 1 1.68.474 2.5 2.5 0 1 0 3.014-3.015 1 1 0 0 1-.474-1.68l1.683-1.682a2.414 2.414 0 0 1 3.414 0z") }
    val RefreshCw: ImageVector by lazy { lucide("RefreshCw", "M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8 M21 3v5h-5 M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16 M8 16H3v5") }
    val Search: ImageVector by lazy { lucide("Search", "M3 11A8 8 0 1 0 19 11A8 8 0 1 0 3 11 m21 21-4.3-4.3") }
    val Server: ImageVector by lazy { lucide("Server", "M4 2H20A2 2 0 0 1 22 4V8A2 2 0 0 1 20 10H4A2 2 0 0 1 2 8V4A2 2 0 0 1 4 2Z M4 14H20A2 2 0 0 1 22 16V20A2 2 0 0 1 20 22H4A2 2 0 0 1 2 20V16A2 2 0 0 1 4 14Z M6 6L6.01 6 M6 18L6.01 18") }
    val Settings: ImageVector by lazy { lucide("Settings", "M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z M9 12A3 3 0 1 0 15 12A3 3 0 1 0 9 12") }
    val SquarePen: ImageVector by lazy { lucide("SquarePen", "M12 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7 M18.375 2.625a1 1 0 0 1 3 3l-9.013 9.014a2 2 0 0 1-.853.505l-2.873.84a.5.5 0 0 1-.62-.62l.84-2.873a2 2 0 0 1 .506-.852z") }
    val Trash2: ImageVector by lazy { lucide("Trash2", "M3 6h18 M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6 M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2 M10 11L10 17 M14 11L14 17") }
    val X: ImageVector by lazy { lucide("X", "M18 6 6 18 m6 6 12 12") }
}
