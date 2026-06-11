plugins {
    alias(libs.plugins.kotlin.jvm)
    `maven-publish`
    `java-library`
}

// Plugin-Vertrag (Typ c Color-Presets, später a Quellen). Kandidat für compileOnly der Plugin-APKs.
// NICHT eingefroren/versioniert: die App entwickelt sich noch.
// Re-exportiert Naht-Typen (BrowsableSource + domain-Modelle) über api(source-api), damit
// externe Plugin-APKs nur plugin-api compileOnly einbinden müssen.
group = "com.komgareader"
version = "0.1.0"

dependencies {
    api(project(":source-api"))
    testImplementation(kotlin("test"))
}
tasks.test { useJUnitPlatform() }
kotlin { jvmToolchain(21) }

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "plugin-api"
            from(components["java"])
        }
    }
}
