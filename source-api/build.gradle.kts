plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Naht-A-Quellen-Vertrag (MediaSource & Co.) als eigenes Modul — Kandidat für das spätere
// compileOnly der Plugins (Mihon-Modell). NICHT eingefroren/versioniert: die App entwickelt
// sich noch. Haengt nur an :domain (Inhalts-Modelle), nie an Android/Netz/UI.
dependencies {
    implementation(project(":domain"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.turbine)
    testImplementation(kotlin("test"))
}
tasks.test { useJUnitPlatform() }
kotlin { jvmToolchain(21) }
