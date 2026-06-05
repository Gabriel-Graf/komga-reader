plugins {
    alias(libs.plugins.kotlin.jvm)
}
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
