plugins {
    alias(libs.plugins.kotlin.jvm)
    `maven-publish`
    `java-library`
}

// Naht-A-Quellen-Vertrag (MediaSource & Co.) als eigenes Modul — das compileOnly der Plugins
// (Mihon-Modell), re-exportiert über :plugin-api. Hängt nur an :domain (Inhalts-Modelle), nie
// an Android/Netz/UI. Nach mavenLocal publiziert, damit ein Plugin-APK, das plugin-api
// compileOnly linkt, diesen Vertrag transitiv auflösen kann. (Spätere Härtung: geshadetes SDK-Jar.)
group = "com.komgareader"
version = "0.1.0"

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

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "source-api"
            from(components["java"])
        }
    }
}
