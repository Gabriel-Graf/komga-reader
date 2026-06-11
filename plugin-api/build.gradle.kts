plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

// Plugin-Vertrag (SourcePlugin, ConfigSchema, PluginMetadata, PluginAbi, ColorPresetSpec).
// Re-exportiert Naht-Typen über api(project(":source-api")) → api(project(":domain")).
// NICHT eingefroren/versioniert: die App entwickelt sich noch.
// Externe Plugin-Autoren linken das geshadete :plugin-sdk (kein direktes Publish mehr):
//   compileOnly("com.komgareader:plugin-sdk:0.1.0")
dependencies {
    api(project(":source-api"))
    testImplementation(kotlin("test"))
}
tasks.test { useJUnitPlatform() }
kotlin { jvmToolchain(21) }
