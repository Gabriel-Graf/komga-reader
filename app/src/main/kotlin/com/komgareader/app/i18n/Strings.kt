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
    val downloaded: String
    val sizeLabel: String
    val formatLabel: String
    val fileLabel: String
    val createdLabel: String
    val modifiedLabel: String
    val pagesShort: String
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
    override val downloaded = "Heruntergeladen ✓"
    override val sizeLabel = "Größe"
    override val formatLabel = "Format"
    override val fileLabel = "Datei"
    override val createdLabel = "Erstellt"
    override val modifiedLabel = "Geändert"
    override val pagesShort = "S."
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
    override val downloaded = "Downloaded ✓"
    override val sizeLabel = "Size"
    override val formatLabel = "Format"
    override val fileLabel = "File"
    override val createdLabel = "Created"
    override val modifiedLabel = "Modified"
    override val pagesShort = "pp."
}

enum class Language(val code: String) { DE("de"), EN("en") }

fun stringsFor(language: Language): Strings = when (language) {
    Language.DE -> StringsDe
    Language.EN -> StringsEn
}
