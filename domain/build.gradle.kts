plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

// Domain-Modelle, UseCases, Repository-Interfaces — reine Kotlin-Schicht ohne Android/Netz.
// Wird von :source-api und :plugin-api über api(project(":domain")) re-exportiert.
// Für externe Plugin-Autoren gebündelt im geshadeten :plugin-sdk (kein direktes Publish mehr).
dependencies {
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(kotlin("test"))
}
tasks.test { useJUnitPlatform() }
kotlin { jvmToolchain(21) }
