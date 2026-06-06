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
}

enum class Language(val code: String) { DE("de"), EN("en") }

fun stringsFor(language: Language): Strings = when (language) {
    Language.DE -> StringsDe
    Language.EN -> StringsEn
}
