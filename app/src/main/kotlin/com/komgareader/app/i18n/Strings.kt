package com.komgareader.app.i18n

/** Alle sichtbaren UI-Texte als typsichere Keys. Jede Sprache implementiert dies. */
interface Strings {
    val appName: String
    val libraryTitle: String
    val libraryEmpty: String
    val settingsTitle: String
    val settingsLanguage: String
    val settingsTheme: String
    val themeLight: String
    val themeDark: String
    val themeSystem: String
    val settingsDisplayMode: String
    val displayModeHelper: String
    val displayEink: String
    val displaySmartphone: String
    val settingsServer: String
    val serverDisplayName: String
    val serverUrl: String
    val serverUrlHint: String
    val serverUrlHelper: String
    val serverApiKeyOptional: String
    val orSeparator: String
    val serverUsername: String
    val serverPassword: String
    val connect: String
    val disconnect: String
    val connected: String
    val notConnected: String
    // Detail-Screen
    val chapters: String
    val chapterViewSwitchToGrid: String
    val chapterViewSwitchToList: String
    val chapterInfo: String
    val backToSeries: String
    val noDescription: String
    val read: String
    val stream: String
    val download: String
    val downloadShort: String
    val downloadAll: String
    val downloaded: String
    val downloadedShort: String
    val removeDownload: String
    val loading: String
    val statusRead: String
    val resumeHere: String
    val markRead: String
    val markUnread: String
    val downloadCancelled: String
    val downloadFolder: String
    val chooseFolder: String
    val defaultFolder: String
    val resetFolder: String
    val sizeLabel: String
    val formatLabel: String
    val fileLabel: String
    val createdLabel: String
    val modifiedLabel: String
    val pagesShort: String
    // Serien-Status (lokalisiert)
    val statusOngoing: String
    val statusEnded: String
    val statusAbandoned: String
    val statusHiatus: String
    // Gruppen-Tabs
    val tabBrowse: String
    val tabGroups: String
    val newGroup: String
    val groupName: String
    val tag: String
    val tagManga: String
    val tagComic: String
    val tagNovel: String
    val tagWebtoon: String
    val tagAuto: String
    val typeUnknown: String
    val assignType: String
    val server: String
    val create: String
    val cancel: String
    val save: String
    val noServerHint: String
    val deleteGroup: String
    val noGroupsHint: String
    // Bibliothek-Modal
    val createLibrary: String
    val editLibrary: String
    val selectLibraries: String
    val fallbackType: String
    // Navigation
    val navPlugins: String
    val pluginsComingSoon: String
    // Settings-Landing (Kachel-Titel)
    val settingsConnection: String
    val settingsAppearance: String
    val settingsReader: String
    val settingsDownloads: String
    val settingsAbout: String
    val aboutDevice: String
    val versionLabel: String
    // Suche
    val searchMediaHint: String
    val searchSettingsHint: String
    val searchAction: String
    val searchNoResults: String
    // Farbfilter
    val settingsColorFilter: String
    val colorFilterSummary: String
    val colorFilterProfiles: String
    val colorFilterAdjust: String
    val colorFilterSaturation: String
    val colorFilterContrast: String
    val colorFilterBrightness: String
    val colorFilterSaveAsNew: String
    val colorFilterUpdate: String
    val colorFilterDelete: String
    val colorFilterProfileName: String
    val colorFilterPreview: String
    val colorFilterCopySuffix: String
    val colorFilterNewProfile: String
    val colorFilterNewProfileHint: String
    val colorFilterPrevImage: String
    val colorFilterNextImage: String
    val close: String
    // Reader-Einstellungen
    val settingsWebtoon: String
    val webtoonOverlapHelper: String
    val webtoonOverlap: String
}

object StringsDe : Strings {
    override val appName = "Komga Reader"
    override val libraryTitle = "Bibliothek"
    override val libraryEmpty = "Noch keine Inhalte. Verbinde einen Komga-Server in den Einstellungen."
    override val settingsTitle = "Einstellungen"
    override val settingsLanguage = "Sprache"
    override val settingsTheme = "Erscheinungsbild"
    override val themeLight = "Hell"
    override val themeDark = "Dunkel"
    override val themeSystem = "System"
    override val settingsDisplayMode = "Anzeige-Modus"
    override val displayModeHelper = "Optimiert die App für E-Ink (Frame-Sprünge, keine Animationen) oder Smartphone."
    override val displayEink = "E-Ink"
    override val displaySmartphone = "Smartphone"
    override val settingsServer = "Komga-Server"
    override val serverDisplayName = "Anzeigename"
    override val serverUrl = "Server-URL"
    override val serverUrlHint = "https://komga.example.org"
    override val serverUrlHelper = "/api/v1/ wird automatisch ergänzt"
    override val serverApiKeyOptional = "API-Schlüssel (optional)"
    override val orSeparator = "— oder —"
    override val serverUsername = "Benutzername"
    override val serverPassword = "Passwort"
    override val connect = "Verbinden"
    override val disconnect = "Trennen"
    override val connected = "Verbunden"
    override val notConnected = "Nicht verbunden"
    // Detail-Screen
    override val chapters = "Kapitel"
    override val chapterViewSwitchToGrid = "Kachelansicht"
    override val chapterViewSwitchToList = "Listenansicht"
    override val chapterInfo = "Beschreibung"
    override val backToSeries = "Zurück zur Serie"
    override val noDescription = "Keine Beschreibung vorhanden"
    override val read = "Lesen"
    override val stream = "Stream"
    override val download = "Herunterladen"
    override val downloadShort = "Laden"
    override val downloadAll = "Alle laden"
    override val downloaded = "Heruntergeladen ✓"
    override val downloadedShort = "Gespeichert"
    override val removeDownload = "Entfernen"
    override val loading = "Lädt…"
    override val statusRead = "Gelesen"
    override val resumeHere = "Hier aufgehört"
    override val markRead = "Als gelesen markieren"
    override val markUnread = "Als ungelesen markieren"
    override val downloadCancelled = "Download abgebrochen"
    override val typeUnknown = "Unbekannt"
    override val assignType = "Typ zuweisen"
    override val downloadFolder = "Download-Ordner"
    override val chooseFolder = "Ordner wählen"
    override val defaultFolder = "Standard (intern)"
    override val resetFolder = "Zurücksetzen"
    override val sizeLabel = "Größe"
    override val formatLabel = "Format"
    override val fileLabel = "Datei"
    override val createdLabel = "Erstellt"
    override val modifiedLabel = "Geändert"
    override val pagesShort = "S."
    // Serien-Status
    override val statusOngoing = "Laufend"
    override val statusEnded = "Abgeschlossen"
    override val statusAbandoned = "Abgebrochen"
    override val statusHiatus = "Pausiert"
    // Bibliotheken-Tabs
    override val tabBrowse = "Stöbern"
    override val tabGroups = "Bibliotheken"
    override val newGroup = "Neue Bibliothek"
    override val groupName = "Name"
    override val tag = "Typ"
    override val tagManga = "Manga"
    override val tagComic = "Comic"
    override val tagNovel = "Roman"
    override val tagWebtoon = "Webtoon"
    override val tagAuto = "Auto"
    override val server = "Server"
    override val create = "Erstellen"
    override val cancel = "Abbrechen"
    override val save = "Speichern"
    override val noServerHint = "Kein Server verbunden. Bitte zuerst in den Einstellungen einen Server hinzufügen."
    override val deleteGroup = "Bibliothek löschen"
    override val noGroupsHint = "Noch keine Bibliotheken. Tippe auf + um eine anzulegen."
    // Bibliothek-Modal
    override val createLibrary = "Bibliothek erstellen"
    override val editLibrary = "Bibliothek bearbeiten"
    override val selectLibraries = "Komga-Libraries"
    override val fallbackType = "Fallback-Typ (optional)"
    // Navigation
    override val navPlugins = "Plugins"
    override val pluginsComingSoon = "Bald verfügbar — externe Quellen-Plugins laden."
    // Settings-Landing
    override val settingsConnection = "Verbindung"
    override val settingsAppearance = "Darstellung"
    override val settingsReader = "Reader"
    override val settingsDownloads = "Downloads"
    override val settingsAbout = "Über"
    override val aboutDevice = "Optimiert für Onyx Boox Go Color 7 Gen2"
    override val versionLabel = "Version"
    // Suche
    override val searchMediaHint = "Bibliothek durchsuchen"
    override val searchSettingsHint = "Einstellungen durchsuchen"
    override val searchAction = "Suchen"
    override val searchNoResults = "Keine Treffer"
    // Farbfilter
    override val settingsColorFilter = "Farbfilter"
    override val colorFilterSummary = "Bilder fürs E-Ink-Display anpassen"
    override val colorFilterProfiles = "Profile"
    override val colorFilterAdjust = "Anpassen"
    override val colorFilterSaturation = "Sättigung"
    override val colorFilterContrast = "Kontrast"
    override val colorFilterBrightness = "Helligkeit"
    override val colorFilterSaveAsNew = "Als neues Profil speichern"
    override val colorFilterUpdate = "Profil aktualisieren"
    override val colorFilterDelete = "Profil löschen"
    override val colorFilterProfileName = "Profilname"
    override val colorFilterPreview = "Vorschau"
    override val colorFilterCopySuffix = " (Kopie)"
    override val colorFilterNewProfile = "Neues Profil"
    override val colorFilterNewProfileHint = "Vom aktiven Profil ausgehen, anpassen und speichern"
    override val colorFilterPrevImage = "Vorheriges"
    override val colorFilterNextImage = "Nächstes"
    override val close = "Schließen"
    // Reader-Einstellungen
    override val settingsWebtoon = "Webtoon"
    override val webtoonOverlapHelper = "Überlappung zwischen Streifen (verhindert sichtbare Lücken beim Blättern)."
    override val webtoonOverlap = "Überlappung"
}

object StringsEn : Strings {
    override val appName = "Komga Reader"
    override val libraryTitle = "Library"
    override val libraryEmpty = "No content yet. Connect a Komga server in Settings."
    override val settingsTitle = "Settings"
    override val settingsLanguage = "Language"
    override val settingsTheme = "Appearance"
    override val themeLight = "Light"
    override val themeDark = "Dark"
    override val themeSystem = "System"
    override val settingsDisplayMode = "Display Mode"
    override val displayModeHelper = "Optimises the app for E-Ink (frame jumps, no animations) or smartphone."
    override val displayEink = "E-Ink"
    override val displaySmartphone = "Smartphone"
    override val settingsServer = "Komga Server"
    override val serverDisplayName = "Display Name"
    override val serverUrl = "Server URL"
    override val serverUrlHint = "https://komga.example.org"
    override val serverUrlHelper = "/api/v1/ is appended automatically"
    override val serverApiKeyOptional = "API Key (optional)"
    override val orSeparator = "— or —"
    override val serverUsername = "Username"
    override val serverPassword = "Password"
    override val connect = "Connect"
    override val disconnect = "Disconnect"
    override val connected = "Connected"
    override val notConnected = "Not connected"
    // Detail-Screen
    override val chapters = "Chapters"
    override val chapterViewSwitchToGrid = "Grid view"
    override val chapterViewSwitchToList = "List view"
    override val chapterInfo = "Description"
    override val backToSeries = "Back to series"
    override val noDescription = "No description available"
    override val read = "Read"
    override val stream = "Stream"
    override val download = "Download"
    override val downloadShort = "Save"
    override val downloadAll = "Download all"
    override val downloaded = "Downloaded ✓"
    override val downloadedShort = "Saved"
    override val removeDownload = "Remove"
    override val loading = "Loading…"
    override val statusRead = "Read"
    override val resumeHere = "Stopped here"
    override val markRead = "Mark as read"
    override val markUnread = "Mark as unread"
    override val downloadCancelled = "Download cancelled"
    override val typeUnknown = "Unknown"
    override val assignType = "Assign type"
    override val downloadFolder = "Download Folder"
    override val chooseFolder = "Choose Folder"
    override val defaultFolder = "Default (internal)"
    override val resetFolder = "Reset"
    override val sizeLabel = "Size"
    override val formatLabel = "Format"
    override val fileLabel = "File"
    override val createdLabel = "Created"
    override val modifiedLabel = "Modified"
    override val pagesShort = "pp."
    // Series status
    override val statusOngoing = "Ongoing"
    override val statusEnded = "Ended"
    override val statusAbandoned = "Abandoned"
    override val statusHiatus = "Hiatus"
    // Libraries tabs
    override val tabBrowse = "Browse"
    override val tabGroups = "Libraries"
    override val newGroup = "New Library"
    override val groupName = "Name"
    override val tag = "Type"
    override val tagManga = "Manga"
    override val tagComic = "Comic"
    override val tagNovel = "Novel"
    override val tagWebtoon = "Webtoon"
    override val tagAuto = "Auto"
    override val server = "Server"
    override val create = "Create"
    override val cancel = "Cancel"
    override val save = "Save"
    override val noServerHint = "No server connected. Please add a server in Settings first."
    override val deleteGroup = "Delete library"
    override val noGroupsHint = "No libraries yet. Tap + to create one."
    // Library modal
    override val createLibrary = "Create library"
    override val editLibrary = "Edit library"
    override val selectLibraries = "Komga libraries"
    override val fallbackType = "Fallback type (optional)"
    // Navigation
    override val navPlugins = "Plugins"
    override val pluginsComingSoon = "Coming soon — load external source plugins."
    // Settings landing
    override val settingsConnection = "Connection"
    override val settingsAppearance = "Appearance"
    override val settingsReader = "Reader"
    override val settingsDownloads = "Downloads"
    override val settingsAbout = "About"
    override val aboutDevice = "Optimised for Onyx Boox Go Color 7 Gen2"
    override val versionLabel = "Version"
    // Search
    override val searchMediaHint = "Search library"
    override val searchSettingsHint = "Search settings"
    override val searchAction = "Search"
    override val searchNoResults = "No results"
    // Color filter
    override val settingsColorFilter = "Color Filter"
    override val colorFilterSummary = "Tune images for the e-ink display"
    override val colorFilterProfiles = "Profiles"
    override val colorFilterAdjust = "Adjust"
    override val colorFilterSaturation = "Saturation"
    override val colorFilterContrast = "Contrast"
    override val colorFilterBrightness = "Brightness"
    override val colorFilterSaveAsNew = "Save as new profile"
    override val colorFilterUpdate = "Update profile"
    override val colorFilterDelete = "Delete profile"
    override val colorFilterProfileName = "Profile name"
    override val colorFilterPreview = "Preview"
    override val colorFilterCopySuffix = " (copy)"
    override val colorFilterNewProfile = "New profile"
    override val colorFilterNewProfileHint = "Start from the active profile, tune and save"
    override val colorFilterPrevImage = "Previous"
    override val colorFilterNextImage = "Next"
    override val close = "Close"
    // Reader settings
    override val settingsWebtoon = "Webtoon"
    override val webtoonOverlapHelper = "Overlap between strips (prevents visible gaps when scrolling)."
    override val webtoonOverlap = "Overlap"
}

enum class Language(val code: String) { DE("de"), EN("en") }

fun stringsFor(language: Language): Strings = when (language) {
    Language.DE -> StringsDe
    Language.EN -> StringsEn
}

/**
 * Übersetzt den quellen-gelieferten Status-Rohwert (Komga: ONGOING/ENDED/…) in
 * lokalisierten Text. Unbekannte Werte werden unverändert durchgereicht (Title-Case).
 */
fun Strings.localizedSeriesStatus(raw: String): String = when (raw.uppercase()) {
    "ONGOING" -> statusOngoing
    "ENDED" -> statusEnded
    "ABANDONED" -> statusAbandoned
    "HIATUS" -> statusHiatus
    else -> raw.lowercase().replaceFirstChar { it.uppercase() }
}

/** Lokalisierter Anzeigename eines Inhaltstyps; `null` = unbekannt. */
fun Strings.localizedContentType(type: com.komgareader.domain.model.ContentType?): String = when (type) {
    com.komgareader.domain.model.ContentType.MANGA -> tagManga
    com.komgareader.domain.model.ContentType.COMIC -> tagComic
    com.komgareader.domain.model.ContentType.NOVEL -> tagNovel
    com.komgareader.domain.model.ContentType.WEBTOON -> tagWebtoon
    null -> typeUnknown
}
