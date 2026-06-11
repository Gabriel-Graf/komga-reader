plugins {
    alias(libs.plugins.kotlin.jvm)
    `maven-publish`
    `java-library`
}

// Wird nach mavenLocal publiziert, weil :plugin-api (das Plugin-SDK) die Naht-Typen
// re-exportiert (api(source-api)→api(domain)) — ein Plugin-APK, das plugin-api compileOnly
// linkt, braucht diese Transitiv-Artefakte zur Kompilierung. Koordinaten konsistent mit
// :source-api und :plugin-api. (Spätere Härtung: ein einzelnes geshadetes SDK-Jar.)
group = "com.komgareader"
version = "0.1.0"

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

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "domain"
            from(components["java"])
        }
    }
}
