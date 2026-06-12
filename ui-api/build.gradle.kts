plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// UI-Vertrags-Modul (A1): die In-Tree-UI-Verträge (Region-Slots, Shell-Pack, Theme-Pack, Icon-Pack)
// als eigenes Modul — das UI-Gegenstück zu :source-api. Enthält die Capability-Surfaces, Slot-/Pack-
// Verträge, reine Wert-/Resolve-Typen, CompositionLocal-Deklarationen UND die entkoppelten Built-ins
// (Theme-Packs, Icon-Stack). Die gekoppelten Default-Renderer (Onyx-Look an app-i18n/-Komponenten)
// bleiben in :app — das KomgaSource-Äquivalent. DAG: domain → ui-api → app, zyklenfrei.
// Noch NICHT eingefroren (kein ABI-Gate) — das kommt mit dem Pack-Lader (L1/L2).
android {
    namespace = "com.komgareader.ui"
    compileSdk = 34
    defaultConfig { minSdk = 28 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    api(project(":domain")) // re-exportiert Series, DisplayBehavior, ShellLayoutMode
    implementation(platform(libs.compose.bom))
    api(libs.compose.ui) // @Composable, Modifier, Color, ImageVector, BoxScope …
    api(libs.compose.material3) // ColorScheme, Shapes, Typography (UiPack-Vertrag)
    implementation("androidx.compose.foundation:foundation")

    testImplementation(libs.junit.jupiter)
    testImplementation(kotlin("test"))
}

// JUnit5 für die reinen Vertrags-Tests (SlotFallback/ShellSelection/IconPack).
tasks.withType<Test> { useJUnitPlatform() }
