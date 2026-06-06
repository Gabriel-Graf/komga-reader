pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositories {
        maven { url = uri("https://maven.ghostscript.com") }
        google()
        mavenCentral()
    }
}
rootProject.name = "komga-reader"
include(":domain")
include(":source-komga")
include(":source-opds")
include(":app")
include(":render-core")
include(":data")
include(":guided-view")
