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
    val read: String
    val stream: String
    val download: String
    val downloadShort: String
    val downloaded: String
    val downloadedShort: String
    val removeDownload: String
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
    val server: String
    val create: String
    val cancel: String
    val noServerHint: String
    val deleteGroup: String
    val noGroupsHint: String
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
    override val read = "Lesen"
    override val stream = "Stream"
    override val download = "Herunterladen"
    override val downloadShort = "Laden"
    override val downloaded = "Heruntergeladen ✓"
    override val downloadedShort = "Gespeichert"
    override val removeDownload = "Entfernen"
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
    // Gruppen-Tabs
    override val tabBrowse = "Stöbern"
    override val tabGroups = "Gruppen"
    override val newGroup = "Neue Gruppe"
    override val groupName = "Name"
    override val tag = "Typ"
    override val tagManga = "Manga"
    override val tagComic = "Comic"
    override val tagNovel = "Roman"
    override val tagWebtoon = "Webtoon"
    override val server = "Server"
    override val create = "Erstellen"
    override val cancel = "Abbrechen"
    override val noServerHint = "Kein Server verbunden. Bitte zuerst in den Einstellungen einen Server hinzufügen."
    override val deleteGroup = "Gruppe löschen"
    override val noGroupsHint = "Noch keine Gruppen. Tippe auf + um eine anzulegen."
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
    override val read = "Read"
    override val stream = "Stream"
    override val download = "Download"
    override val downloadShort = "Save"
    override val downloaded = "Downloaded ✓"
    override val downloadedShort = "Saved"
    override val removeDownload = "Remove"
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
    // Groups tabs
    override val tabBrowse = "Browse"
    override val tabGroups = "Groups"
    override val newGroup = "New Group"
    override val groupName = "Name"
    override val tag = "Type"
    override val tagManga = "Manga"
    override val tagComic = "Comic"
    override val tagNovel = "Novel"
    override val tagWebtoon = "Webtoon"
    override val server = "Server"
    override val create = "Create"
    override val cancel = "Cancel"
    override val noServerHint = "No server connected. Please add a server in Settings first."
    override val deleteGroup = "Delete group"
    override val noGroupsHint = "No groups yet. Tap + to create one."
}

enum class Language(val code: String) { DE("de"), EN("en") }

fun stringsFor(language: Language): Strings = when (language) {
    Language.DE -> StringsDe
    Language.EN -> StringsEn
}
