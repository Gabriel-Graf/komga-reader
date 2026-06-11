plugins {
    alias(libs.plugins.kotlin.jvm)
    id("com.gradleup.shadow") version "8.3.11"
    `maven-publish`
    `java-library`
}

// Geshadetes Single-Jar-SDK für externe Plugin-Autoren.
// Bündelt die Klassen von :domain, :source-api und :plugin-api in einem einzigen Jar –
// ohne Paket-Relocation, ohne Drittanbieter-Libs (kotlin-stdlib, kotlinx-coroutines).
// Ein Plugin-APK linkt nur noch: compileOnly("com.komgareader:plugin-sdk:0.1.0")
group = "com.komgareader"
version = "0.1.0"

dependencies {
    // Bringt :source-api und :domain transitiv mit
    implementation(project(":plugin-api"))
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveClassifier.set("")

    // Nur unsere eigenen Projektklassen einschließen – keine Stdlib, keine externen Libs.
    // Shadow-dependencies-Block schränkt ein, welche aufgelösten Artefakte gemergt werden.
    dependencies {
        include(project(":domain"))
        include(project(":source-api"))
        include(project(":plugin-api"))
    }
}

// Standard-jar-Task deaktivieren, damit nur das Shadow-Jar veröffentlicht wird
tasks.named<Jar>("jar") {
    enabled = false
}

publishing {
    publications {
        create<MavenPublication>("shadow") {
            artifactId = "plugin-sdk"
            // Shadow-Komponente publiziert das gefettete Jar mit bereinigtem POM
            from(components["shadow"])
        }
    }
}

kotlin { jvmToolchain(21) }
