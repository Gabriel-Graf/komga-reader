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
    val connectedServers: String
    val addServer: String
    val removeServer: String
    val serverSectionKind: String
    val serverSectionServer: String
    val serverSectionAuth: String
    // Detail-Screen
    val chapters: String
    val chapterViewSwitchToGrid: String
    val chapterViewSwitchToList: String
    val chapterInfo: String
    val backToSeries: String
    val noDescription: String
    val readMore: String
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
    val clearSearch: String
    val filterByType: String
    val filterTypePlaceholder: String
    val filterDownloaded: String
    val filterDownloadedEmpty: String
    // Collections
    val collections: String
    val collectionsEmpty: String
    val newCollection: String
    val collectionName: String
    val collectionKindSeries: String
    val collectionKindBook: String
    val addToCollection: String
    val removeFromCollection: String
    val collectionSyncNow: String
    val collectionLocalOnly: String
    val collectionSyncInfoTitle: String
    val collectionSyncUnsupported: String
    val collectionSyncForbidden: String
    val deleteCollection: String
    val deleteCollectionServerToo: String
    // Comic-Reader
    /** Angezeigt, wenn Panel-Modus AUS ist (Tippen schaltet ihn AN). */
    val readerPanelModeOn: String
    /** Angezeigt, wenn Panel-Modus AN ist (Tippen schaltet ihn AUS). */
    val readerPanelModeOff: String
    // Reader-Chrome (Start-Hinweis + Tap-Zonen-Hints)
    val readerTapHint: String
    val readerPrevPage: String
    val readerNextPage: String
    // Guided-View (Debug)
    val settingsGuidedDebug: String
    val readerPanelOverlay: String
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
    val colorFilterAdvanced: String
    val colorFilterBlackPoint: String
    val colorFilterWhitePoint: String
    val colorFilterGamma: String
    val colorFilterSharpen: String
    val colorFilterSharpenRadius: String
    val colorFilterDither: String
    val colorFilterDitherNone: String
    val colorFilterDitherFloyd: String
    val colorFilterDitherOrdered: String
    val colorFilterDitherLevels: String
    val colorFilterReaderOnlyHint: String
    val colorFilterDitherAbout: String
    val colorFilterDitherNoneDesc: String
    val colorFilterDitherFloydDesc: String
    val colorFilterDitherOrderedDesc: String
    val colorFilterDitherLevelsAbout: String
    val close: String
    // Reader-Einstellungen
    val settingsWebtoon: String
    val webtoonOverlapHelper: String
    val webtoonOverlap: String
    // E-Ink-Refresh
    val settingsEinkRefresh: String
    val deviceManagedRefresh: String
    val deviceManagedRefreshHelper: String
    // Roman-Typografie-Panel
    val novelTypography: String
    val novelFontSize: String
    val novelLineHeight: String
    val novelFontWeight: String
    val novelMargin: String
    val novelMarginNarrow: String
    val novelMarginNormal: String
    val novelMarginWide: String
    val novelFontFamily: String
    val novelTextAlign: String
    val novelAlignLeft: String
    val novelAlignJustify: String
    val novelHyphenation: String
    val novelHyphenationOff: String
    val novelHyphenationDe: String
    val novelHyphenationEn: String
    // Roman-Status-Fuß
    val novelProgressPercent: String
    val novelPageOfCount: String
    // Roman-Inhaltsverzeichnis
    val novelToc: String
    val novelTocEmpty: String
    // Roman-Volltextsuche + Gehe-zu-%
    val novelSearch: String
    val novelSearchPlaceholder: String
    val novelSearchEmpty: String
    val novelSearchNoResults: String
    val novelGoToPercent: String
    /** Platzhalter im „Springe zu"-Feld: akzeptiert eine Seitenzahl oder einen Prozentwert. */
    val novelGoToPlaceholder: String
    /** Beschriftung des Springen-Buttons (Barrierefreiheit). */
    val novelGoToAction: String
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
    override val connectedServers = "Verbundene Server"
    override val addServer = "Server hinzufügen"
    override val serverSectionKind = "Quellenart"
    override val serverSectionServer = "Server"
    override val serverSectionAuth = "Anmeldung"
    override val removeServer = "Entfernen"
    // Detail-Screen
    override val chapters = "Kapitel"
    override val chapterViewSwitchToGrid = "Kachelansicht"
    override val chapterViewSwitchToList = "Listenansicht"
    override val chapterInfo = "Beschreibung"
    override val backToSeries = "Zurück zur Serie"
    override val noDescription = "Keine Beschreibung vorhanden"
    override val readMore = "Mehr lesen"
    override val read = "Lesen"
    override val stream = "Stream"
    override val download = "Herunterladen"
    override val downloadShort = "Laden"
    override val downloadAll = "Alle herunterladen"
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
    override val clearSearch = "Suche zurücksetzen"
    override val filterByType = "Nach Werk-Typ filtern"
    override val filterTypePlaceholder =
        "Keine Werke mit dem gewählten Typ gefunden.\n\n" +
            "Die App muss wissen, welcher Lesemodus zu welchem Werk gehört " +
            "(Manga, Comic, Webtoon, Roman). Lege den Typ entweder gesammelt im Tab " +
            "\"Bibliotheken\" fest (Bibliothek bearbeiten → Werk-Typ wählen — gilt für alle " +
            "Werke darin) oder einzeln in den Serien-Details über das Drei-Punkte-Menü oben " +
            "rechts → \"Typ zuweisen\"."
    override val filterDownloaded = "Heruntergeladen"
    override val filterDownloadedEmpty =
        "Keine heruntergeladenen Werke.\n\n" +
            "Lade ein Werk herunter (langes Tippen auf ein Cover oder in den Serien-Details " +
            "über „Alle herunterladen“), dann erscheint es hier — auch offline."
    // Collections
    override val collections = "Collections"
    override val collectionsEmpty = "Noch keine Collections. Sammle Werke in einer eigenen Liste."
    override val newCollection = "Neue Collection"
    override val collectionName = "Name"
    override val collectionKindSeries = "Serien"
    override val collectionKindBook = "Bücher"
    override val addToCollection = "Zu Collection hinzufügen"
    override val removeFromCollection = "Aus Collection entfernen"
    override val collectionSyncNow = "Jetzt synchronisieren"
    override val collectionLocalOnly = "Nur lokal"
    override val collectionSyncInfoTitle = "Sync-Status"
    override val collectionSyncUnsupported = "Die hinterlegte Quelle unterstützt keinen Sync — diese Werke bleiben nur lokal, kein Abgleich zwischen Geräten."
    override val collectionSyncForbidden = "Dein Konto darf am Server keine Collections anlegen (nur Admins) — diese Werke bleiben nur lokal."
    override val deleteCollection = "Collection löschen"
    override val deleteCollectionServerToo = "Auch auf dem Server löschen"
    // Comic-Reader
    override val readerPanelModeOn = "Panel-Modus an"
    override val readerPanelModeOff = "Panel-Modus aus"
    override val readerTapHint = "Tippe mittig für die Menüleiste"
    override val readerPrevPage = "Letzte Seite"
    override val readerNextPage = "Nächste Seite"
    // Guided-View (Debug)
    override val settingsGuidedDebug = "Guided-View (Debug)"
    override val readerPanelOverlay = "Erkannte Panel-Rahmen einblenden"
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
    override val colorFilterAdvanced = "Erweitert"
    override val colorFilterBlackPoint = "Schwarzpunkt"
    override val colorFilterWhitePoint = "Weißpunkt"
    override val colorFilterGamma = "Gamma"
    override val colorFilterSharpen = "Schärfe"
    override val colorFilterSharpenRadius = "Schärfe-Radius"
    override val colorFilterDither = "Dithering"
    override val colorFilterDitherNone = "Aus"
    override val colorFilterDitherFloyd = "Floyd-Steinberg"
    override val colorFilterDitherOrdered = "Ordered"
    override val colorFilterDitherLevels = "Stufen"
    override val colorFilterReaderOnlyHint = "Wirkt nur beim Lesen, nicht auf Bibliotheks-Cover. Erhöht den Akku-Verbrauch."
    override val colorFilterDitherAbout = "Dithering reduziert das Bild auf wenige Helligkeitsstufen pro Farbkanal und verteilt den dabei entstehenden Rundungsfehler auf die Nachbarpixel. So entsteht aus wenigen echten Tönen der Eindruck weicher Verläufe — das vermeidet sichtbare Stufen (Banding) und kaschiert das Farbraster des Kaleido-Displays. Kostet zusätzliche Rechenzeit und damit etwas Akku."
    override val colorFilterDitherNoneDesc = "Keine Reduktion — das Bild wird unverändert durchgereicht (Standard, kein Mehrverbrauch)."
    override val colorFilterDitherFloydDesc = "Fehlerdiffusion: Der Rundungsfehler wird gewichtet an die folgenden Pixel weitergegeben. Beste Ergebnisse bei Verläufen und Hauttönen, wirkt am natürlichsten. Läuft pixelweise nacheinander, daher am rechenintensivsten (höchster Akku-Verbrauch)."
    override val colorFilterDitherOrderedDesc = "Feste Bayer-Rastermatrix als Schwellwert. Deutlich billiger und parallel berechenbar, hinterlässt aber ein leichtes, regelmäßiges Punktmuster. Gut, wenn Akku wichtiger ist als maximale Glätte."
    override val colorFilterDitherLevelsAbout = "Stufen: Anzahl der Helligkeitswerte pro Kanal, auf die reduziert wird (2 = sehr grob und stark sichtbar, 64 = fein und dezent). Niedriger = stärkerer Dither-Effekt."
    override val close = "Schließen"
    // Reader-Einstellungen
    override val settingsWebtoon = "Webtoon"
    override val webtoonOverlapHelper = "Überlappung zwischen Streifen (verhindert sichtbare Lücken beim Blättern)."
    override val webtoonOverlap = "Überlappung"
    override val settingsEinkRefresh = "E-Ink-Refresh"
    override val deviceManagedRefresh = "Refresh dem Gerät überlassen"
    override val deviceManagedRefreshHelper =
        "Das Gerät (Onyx) steuert das Ghosting-Clear selbst — die App erzwingt keinen Voll-Refresh. " +
            "Schnelles Blättern bleibt aktiv. Aus = die App steuert (Voll-Refresh alle paar Seiten)."
    override val novelTypography = "Typografie"
    override val novelFontSize = "Schriftgröße"
    override val novelLineHeight = "Zeilenabstand"
    override val novelFontWeight = "Schriftstärke"
    override val novelMargin = "Seitenränder"
    override val novelMarginNarrow = "Schmal"
    override val novelMarginNormal = "Normal"
    override val novelMarginWide = "Breit"
    override val novelFontFamily = "Schriftart"
    override val novelTextAlign = "Ausrichtung"
    override val novelAlignLeft = "Linksbündig"
    override val novelAlignJustify = "Blocksatz"
    override val novelHyphenation = "Silbentrennung"
    override val novelHyphenationOff = "Aus"
    override val novelHyphenationDe = "Deutsch"
    override val novelHyphenationEn = "Englisch"
    override val novelProgressPercent = "Fortschritt"
    override val novelPageOfCount = "Seite"
    override val novelToc = "Inhaltsverzeichnis"
    override val novelTocEmpty = "Kein Inhaltsverzeichnis vorhanden."
    override val novelSearch = "Suche"
    override val novelSearchPlaceholder = "Im Buch suchen…"
    override val novelSearchEmpty = "Suchbegriff eingeben und bestätigen."
    override val novelSearchNoResults = "Keine Treffer gefunden."
    override val novelGoToPercent = "Gehe zu (%)"
    override val novelGoToPlaceholder = "Seite oder %"
    override val novelGoToAction = "Springen"
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
    override val connectedServers = "Connected servers"
    override val addServer = "Add server"
    override val removeServer = "Remove"
    override val serverSectionKind = "Source type"
    override val serverSectionServer = "Server"
    override val serverSectionAuth = "Sign-in"
    // Detail-Screen
    override val chapters = "Chapters"
    override val chapterViewSwitchToGrid = "Grid view"
    override val chapterViewSwitchToList = "List view"
    override val chapterInfo = "Description"
    override val backToSeries = "Back to series"
    override val noDescription = "No description available"
    override val readMore = "Read more"
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
    override val clearSearch = "Clear search"
    override val filterByType = "Filter by type"
    override val filterTypePlaceholder =
        "No works found for the selected type.\n\n" +
            "The app needs to know which reading mode each work uses " +
            "(Manga, Comic, Webtoon, Novel). Set the type either in bulk under the " +
            "\"Libraries\" tab (edit a library → choose work type — applies to everything " +
            "in it) or per series in the series details via the three-dot menu in the top " +
            "right → \"Assign type\"."
    override val filterDownloaded = "Downloaded"
    override val filterDownloadedEmpty =
        "No downloaded works.\n\n" +
            "Download a work (long-press a cover or use \"Download all\" in the series " +
            "details), then it shows up here — even offline."
    // Collections
    override val collections = "Collections"
    override val collectionsEmpty = "No collections yet. Gather works into your own list."
    override val newCollection = "New collection"
    override val collectionName = "Name"
    override val collectionKindSeries = "Series"
    override val collectionKindBook = "Books"
    override val addToCollection = "Add to collection"
    override val removeFromCollection = "Remove from collection"
    override val collectionSyncNow = "Sync now"
    override val collectionLocalOnly = "Local only"
    override val collectionSyncInfoTitle = "Sync status"
    override val collectionSyncUnsupported = "The configured source does not support syncing — these works stay local only, no cross-device sync."
    override val collectionSyncForbidden = "Your account cannot create collections on the server (admins only) — these works stay local only."
    override val deleteCollection = "Delete collection"
    override val deleteCollectionServerToo = "Also delete on server"
    // Comic reader
    override val readerPanelModeOn = "Panel mode on"
    override val readerPanelModeOff = "Panel mode off"
    override val readerTapHint = "Tap the center for the menu bar"
    override val readerPrevPage = "Previous page"
    override val readerNextPage = "Next page"
    // Guided view (debug)
    override val settingsGuidedDebug = "Guided view (debug)"
    override val readerPanelOverlay = "Show detected panel borders"
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
    override val colorFilterAdvanced = "Advanced"
    override val colorFilterBlackPoint = "Black point"
    override val colorFilterWhitePoint = "White point"
    override val colorFilterGamma = "Gamma"
    override val colorFilterSharpen = "Sharpen"
    override val colorFilterSharpenRadius = "Sharpen radius"
    override val colorFilterDither = "Dithering"
    override val colorFilterDitherNone = "Off"
    override val colorFilterDitherFloyd = "Floyd-Steinberg"
    override val colorFilterDitherOrdered = "Ordered"
    override val colorFilterDitherLevels = "Levels"
    override val colorFilterReaderOnlyHint = "Applies only while reading, not to library covers. Increases battery use."
    override val colorFilterDitherAbout = "Dithering reduces the image to a few brightness levels per colour channel and spreads the resulting rounding error across neighbouring pixels. A handful of real tones then read as smooth gradients — avoiding visible banding and masking the Kaleido display's colour grid. It costs extra computation and therefore some battery."
    override val colorFilterDitherNoneDesc = "No reduction — the image passes through unchanged (default, no extra cost)."
    override val colorFilterDitherFloydDesc = "Error diffusion: the rounding error is passed on, weighted, to the following pixels. Best results on gradients and skin tones, looks most natural. Runs pixel by pixel in sequence, so it is the most compute-heavy (highest battery use)."
    override val colorFilterDitherOrderedDesc = "A fixed Bayer threshold matrix. Much cheaper and computable in parallel, but leaves a faint regular dot pattern. Good when battery matters more than maximum smoothness."
    override val colorFilterDitherLevelsAbout = "Levels: how many brightness values per channel the image is reduced to (2 = very coarse and strong, 64 = fine and subtle). Lower = stronger dithering."
    override val close = "Close"
    // Reader settings
    override val settingsWebtoon = "Webtoon"
    override val webtoonOverlapHelper = "Overlap between strips (prevents visible gaps when scrolling)."
    override val webtoonOverlap = "Overlap"
    override val settingsEinkRefresh = "E-Ink refresh"
    override val deviceManagedRefresh = "Let the device manage refresh"
    override val deviceManagedRefreshHelper =
        "The device (Onyx) handles ghosting clears itself — the app forces no full refresh. " +
            "Fast paging stays on. Off = the app manages it (full refresh every few pages)."
    override val novelTypography = "Typography"
    override val novelFontSize = "Font size"
    override val novelLineHeight = "Line spacing"
    override val novelFontWeight = "Font weight"
    override val novelMargin = "Margins"
    override val novelMarginNarrow = "Narrow"
    override val novelMarginNormal = "Normal"
    override val novelMarginWide = "Wide"
    override val novelFontFamily = "Font"
    override val novelTextAlign = "Alignment"
    override val novelAlignLeft = "Left"
    override val novelAlignJustify = "Justify"
    override val novelHyphenation = "Hyphenation"
    override val novelHyphenationOff = "Off"
    override val novelHyphenationDe = "German"
    override val novelHyphenationEn = "English"
    override val novelProgressPercent = "Progress"
    override val novelPageOfCount = "Page"
    override val novelToc = "Table of contents"
    override val novelTocEmpty = "No table of contents available."
    override val novelSearch = "Search"
    override val novelSearchPlaceholder = "Search in book…"
    override val novelSearchEmpty = "Enter a search term and confirm."
    override val novelSearchNoResults = "No matches found."
    override val novelGoToPercent = "Go to (%)"
    override val novelGoToPlaceholder = "Page or %"
    override val novelGoToAction = "Go"
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
