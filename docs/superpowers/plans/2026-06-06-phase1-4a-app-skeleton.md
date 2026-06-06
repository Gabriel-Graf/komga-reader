# Phase 1 · Plan 4a/… — Android App-Skeleton

> **For agentic workers:** REQUIRED SUB-SKILL: subagent-driven-development / executing-plans. Checkbox steps.

**Goal:** Eine bootfähige Android-App (Compose): zwei Top-Level-Screens **Bibliothek** (Home, leerer Zustand) und **Einstellungen**, navigierbar; E-Ink-Theme (hell/dunkel, hoher Kontrast, Border statt Schatten); typsichere i18n DE+EN mit Laufzeit-Umschaltung; Material-Icons. Auf dem Emulator installierbar und startet ohne Crash.

**Architecture:** Neues Android-Modul `:app` (`com.android.application`) mit Jetpack Compose + Navigation. Kein Hilt/Room hier (kommen mit 1.4c). i18n als reine Kotlin-Lösung: ein `Strings`-Interface mit `de`/`en`-Implementierungen, bereitgestellt über ein `CompositionLocal`, Sprache als `mutableStateOf`. Theme als zentrale Token (Farben/Spacing/Radius/Typo).

**Tech Stack:** AGP 8.7.2 · Kotlin 2.0.21 · Compose Compiler Plugin · Compose BOM 2024.10.01 · Material3 · Navigation-Compose · material-icons-extended · minSdk 28 / target+compileSdk 34.

**Hinweis Versionen:** Sollte eine exakte Version nicht auflösen/inkompatibel sein, die nächste passende wählen und als Abweichung berichten. Die App MUSS am Ende auf dem Emulator booten — das ist das Abnahmekriterium, nicht eine bestimmte Versionsnummer.

## Emulator
Eine AVD `eink_test` (Android 14) existiert. Prüfen ob online: `adb devices` zeigt `emulator-5554  device`. Falls „offline"/leer: `adb wait-for-device` und bis `getprop sys.boot_completed`==1 warten. SDK unter `~/Android/Sdk`, `adb` unter `/usr/bin/adb`.

---

### Task 0: `:app`-Modul + Gradle/AGP-Setup

**Files:**
- Modify: `settings.gradle.kts`, `gradle/libs.versions.toml`, `build.gradle.kts`
- Create: `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `gradle.properties`

- [ ] **Step 1: Version-Catalog erweitern**

In `gradle/libs.versions.toml` unter `[versions]` ergänzen:
```toml
agp = "8.7.2"
composeBom = "2024.10.01"
activityCompose = "1.9.3"
navigationCompose = "2.8.4"
coreKtx = "1.13.1"
lifecycle = "2.8.7"
```
unter `[libraries]`:
```toml
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
androidx-lifecycle-runtime-ktx = { module = "androidx.lifecycle:lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigationCompose" }
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
compose-ui = { module = "androidx.compose.ui:ui" }
compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
compose-material3 = { module = "androidx.compose.material3:material3" }
compose-material-icons-extended = { module = "androidx.compose.material:material-icons-extended" }
```
unter `[plugins]`:
```toml
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

- [ ] **Step 2: Root-Build + gradle.properties**

In `build.gradle.kts` (Root) die Plugins ergänzen (apply false):
```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
```
Create `gradle.properties` (falls nicht vorhanden) mit:
```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
org.gradle.caching=true
```

- [ ] **Step 3: Modul registrieren + build.gradle.kts**

In `settings.gradle.kts` ergänzen: `include(":app")`.

Create `app/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.komgareader.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.komgareader.app"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)
}
```

Create `app/src/main/AndroidManifest.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:allowBackup="true"
        android:label="Komga Reader"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.Material.NoActionBar">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

- [ ] **Step 4: Build verifizieren**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL, APK unter `app/build/outputs/apk/debug/app-debug.apk`. (Erster Build lädt AGP/Compose — dauert.)

- [ ] **Step 5: Commit**
```bash
git add settings.gradle.kts build.gradle.kts gradle.properties gradle/libs.versions.toml app/build.gradle.kts app/src/main/AndroidManifest.xml
git commit -m "build: Android :app-Modul (Compose, AGP 8.7.2)"
```

---

### Task 1: i18n — typsichere Strings DE+EN

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/i18n/Strings.kt`
- Create: `app/src/main/kotlin/com/komgareader/app/i18n/Localization.kt`

- [ ] **Step 1: Strings-Interface + Übersetzungen**

Create `Strings.kt`:
```kotlin
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
```

- [ ] **Step 2: CompositionLocal**

Create `Localization.kt`:
```kotlin
package com.komgareader.app.i18n

import androidx.compose.runtime.staticCompositionLocalOf

/** Liefert die aktuell aktiven [Strings] an jede Composable. Default Deutsch. */
val LocalStrings = staticCompositionLocalOf<Strings> { StringsDe }
```

- [ ] **Step 3: Kompilieren + Commit**
Run: `./gradlew :app:compileDebugKotlin` → SUCCESSFUL.
```bash
git add app/src/main/kotlin/com/komgareader/app/i18n/
git commit -m "feat(app): typsichere i18n DE+EN + LocalStrings"
```

---

### Task 2: Theme — E-Ink-Tokens (hell/dunkel)

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/ui/theme/Theme.kt`

- [ ] **Step 1: Theme anlegen**

Create `Theme.kt`:
```kotlin
package com.komgareader.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * E-Ink-Theme: maximaler Kontrast, nur Schwarz/Weiß/zwei Graustufen, keine
 * Akzentfarbe (Akzent = solides Schwarz bzw. invertiert Weiß). Tiefe entsteht im
 * UI über 1.5px-Border statt Schatten/Verläufe (Ghosting-arm).
 */
private val LightEink = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFE8E8E8),
    onSurfaceVariant = Color(0xFF222222),
    outline = Color.Black,
)

private val DarkEink = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFFDDDDDD),
    outline = Color.White,
)

private val EinkShapes = Shapes(
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
)

enum class ThemeMode { LIGHT, DARK, SYSTEM }

@Composable
fun KomgaReaderTheme(themeMode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkEink else LightEink,
        shapes = EinkShapes,
        content = content,
    )
}
```

- [ ] **Step 2: Kompilieren + Commit**
Run: `./gradlew :app:compileDebugKotlin` → SUCCESSFUL.
```bash
git add app/src/main/kotlin/com/komgareader/app/ui/theme/
git commit -m "feat(app): E-Ink-Theme (hell/dunkel, Border statt Schatten)"
```

---

### Task 3: Screens (Bibliothek + Einstellungen) + Navigation + MainActivity

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/ui/library/LibraryScreen.kt`
- Create: `app/src/main/kotlin/com/komgareader/app/ui/settings/SettingsScreen.kt`
- Create: `app/src/main/kotlin/com/komgareader/app/MainActivity.kt`

- [ ] **Step 1: LibraryScreen (leerer Zustand)**

Create `LibraryScreen.kt`:
```kotlin
package com.komgareader.app.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.komgareader.app.i18n.LocalStrings
import androidx.compose.runtime.CompositionLocalProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(onOpenSettings: () -> Unit) {
    val s = LocalStrings.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.libraryTitle) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = s.settingsTitle)
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Text(s.libraryEmpty, textAlign = TextAlign.Center, modifier = Modifier.padding(32.dp))
        }
    }
}
```
(Import `androidx.compose.ui.unit.dp` ergänzen.)

- [ ] **Step 2: SettingsScreen (Theme- + Sprach-Auswahl, funktional)**

Create `SettingsScreen.kt`:
```kotlin
package com.komgareader.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.komgareader.app.i18n.Language
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.ui.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    language: Language,
    onLanguageChange: (Language) -> Unit,
    onBack: () -> Unit,
) {
    val s = LocalStrings.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.settingsTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            Text(s.settingsTheme)
            ThemeMode.entries.forEach { mode ->
                val label = when (mode) {
                    ThemeMode.LIGHT -> s.themeLight
                    ThemeMode.DARK -> s.themeDark
                    ThemeMode.SYSTEM -> s.themeSystem
                }
                OptionRow(label, selected = mode == themeMode) { onThemeChange(mode) }
            }
            Text(s.settingsLanguage, modifier = Modifier.padding(top = 16.dp))
            Language.entries.forEach { lang ->
                OptionRow(lang.code.uppercase(), selected = lang == language) { onLanguageChange(lang) }
            }
        }
    }
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onSelect).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}
```

- [ ] **Step 3: MainActivity (NavHost + Theme/Sprach-State)**

Create `MainActivity.kt`:
```kotlin
package com.komgareader.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.komgareader.app.i18n.Language
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.i18n.stringsFor
import com.komgareader.app.ui.library.LibraryScreen
import com.komgareader.app.ui.settings.SettingsScreen
import com.komgareader.app.ui.theme.KomgaReaderTheme
import com.komgareader.app.ui.theme.ThemeMode

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var themeMode by remember { mutableStateOf(ThemeMode.SYSTEM) }
            var language by remember { mutableStateOf(Language.DE) }

            CompositionLocalProvider(LocalStrings provides stringsFor(language)) {
                KomgaReaderTheme(themeMode = themeMode) {
                    val nav = rememberNavController()
                    NavHost(navController = nav, startDestination = "library") {
                        composable("library") {
                            LibraryScreen(onOpenSettings = { nav.navigate("settings") })
                        }
                        composable("settings") {
                            SettingsScreen(
                                themeMode = themeMode,
                                onThemeChange = { themeMode = it },
                                language = language,
                                onLanguageChange = { language = it },
                                onBack = { nav.popBackStack() },
                            )
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 4: Build verifizieren**
Run: `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**
```bash
git add app/src/main/kotlin/com/komgareader/app/
git commit -m "feat(app): Bibliothek + Einstellungen + Navigation (Skeleton)"
```

---

### Task 4: E2E — Emulator-Smoke-Test

**Files:**
- Create: `tools/e2e/app_smoke.sh`

- [ ] **Step 1: Smoke-Test-Script**

Create `tools/e2e/app_smoke.sh` (Oneliner-Befehle, keine Newlines in einzelnen Kommandos):
```bash
#!/usr/bin/env bash
set -euo pipefail
ADB=/usr/bin/adb
APK=app/build/outputs/apk/debug/app-debug.apk
PKG=com.komgareader.app

echo "[1/5] Warte auf Emulator..."
"$ADB" wait-for-device
until [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 2; done
echo "[2/5] Boot abgeschlossen."

echo "[3/5] Installiere APK..."
"$ADB" install -r -t "$APK"

echo "[4/5] Starte App + leere Logcat..."
"$ADB" logcat -c
"$ADB" shell am start -W -n "$PKG/.MainActivity"
sleep 4

echo "[5/5] Pruefe auf Crash..."
if "$ADB" logcat -d | grep -E "FATAL EXCEPTION|AndroidRuntime.*$PKG" | grep -q .; then
  echo "FAIL: Crash im Logcat"; "$ADB" logcat -d | grep -A20 "FATAL EXCEPTION" | head -30; exit 1
fi
PID="$("$ADB" shell pidof "$PKG" | tr -d '\r' || true)"
if [ -z "$PID" ]; then echo "FAIL: Prozess laeuft nicht"; exit 1; fi
"$ADB" exec-out screencap -p > /tmp/app_smoke.png || true
echo "PASS: App laeuft (PID $PID), Screenshot /tmp/app_smoke.png"
```
`chmod +x tools/e2e/app_smoke.sh`.

- [ ] **Step 2: Smoke-Test ausführen**
Run: `bash tools/e2e/app_smoke.sh`
Expected: endet mit `PASS: App laeuft ...`. Falls der Emulator noch nicht da ist, wartet das Script. Falls Crash → Logcat-Ausschnitt analysieren und beheben (Stepdown: erst MainActivity/Theme/Compose-Fehler).

- [ ] **Step 3: Commit**
```bash
git add tools/e2e/app_smoke.sh
git commit -m "test(app): Emulator-Smoke-Test (Install + Start + Crash-Check)"
```

---

## Self-Review-Notiz (Autor)
- **Spec-Abdeckung:** §8 UI (ein Home-Tab Bibliothek + Settings, Theme hell/dunkel/system, E-Ink-Designsprache) → Tasks 2,3; §9 i18n (typsicher DE+EN, Laufzeit-Umschaltung) → Task 1. Material-Icons via `material-icons-extended` (Compose-Äquivalent zu Material Symbols).
- **Bewusst verschoben:** Hilt/Room (1.4c), echte Bibliotheks-Inhalte/KomgaSource-Anbindung (1.4d), Reader (1.4e), persistierte Settings (aktuell nur In-Memory-State — Persistenz mit DataStore/Room in 1.4c/d), Custom-Controls statt Material3-RadioButton (Politur später).
- **Abnahme:** `./gradlew :app:assembleDebug` grün UND `tools/e2e/app_smoke.sh` endet mit PASS (App bootet crashfrei auf dem Emulator).
