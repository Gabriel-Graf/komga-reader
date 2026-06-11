plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

// Naht-A-Quellen-Vertrag (MediaSource, BrowsableSource, SyncingSource, SourceId, PageRefs …).
// Re-exportiert :domain über api(project(":domain")).
// Für externe Plugin-Autoren gebündelt im geshadeten :plugin-sdk (kein direktes Publish mehr).
dependencies {
    api(project(":domain"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.turbine)
    testImplementation(kotlin("test"))
}
tasks.test { useJUnitPlatform() }
kotlin { jvmToolchain(21) }
