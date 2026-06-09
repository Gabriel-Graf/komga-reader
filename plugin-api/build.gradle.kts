plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Plugin-Vertrag (Typ c Color-Presets, später a Quellen). Kandidat für compileOnly der Plugin-APKs.
// NICHT eingefroren/versioniert: die App entwickelt sich noch.
// Hängt nur an :domain (ColorProfile-Modell), nie an Android/Netz/UI.
dependencies {
    implementation(project(":domain"))
    testImplementation(kotlin("test"))
}
tasks.test { useJUnitPlatform() }
kotlin { jvmToolchain(21) }
