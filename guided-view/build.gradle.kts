plugins {
    alias(libs.plugins.kotlin.jvm)
}
dependencies {
    implementation(project(":domain"))
    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
}
tasks.test { useJUnitPlatform() }
kotlin { jvmToolchain(21) }
