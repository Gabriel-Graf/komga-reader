pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositories {
        maven { url = uri("https://maven.ghostscript.com") }
        // Onyx Boox SDK (EpdController für E-Ink-Refresh-Steuerung)
        maven {
            url = uri("http://repo.boox.com/repository/maven-public/")
            isAllowInsecureProtocol = true
        }
        google()
        mavenCentral()
    }
}
rootProject.name = "komga-reader"
include(":domain")
include(":ui-api")
include(":source-komga")
include(":source-opds")
include(":app")
include(":render-core")
include(":render-crengine")
include(":data")
include(":guided-view")
include(":eink-onyx")
include(":source-api")
include(":plugin-api")
include(":plugin-host")
include(":plugin-sdk")
