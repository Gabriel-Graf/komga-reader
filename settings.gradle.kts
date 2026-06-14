pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositories {
        // MuPDF (Render-Engine). Single-purpose Repo → auf die MuPDF-Gruppe content-gefiltert
        // (verhindert Dependency-Confusion: dieser Repo kann nichts anderes ausliefern).
        maven {
            url = uri("https://maven.ghostscript.com")
            content { includeGroup("com.artifex.mupdf") }
        }
        // Onyx Boox SDK (EpdController für E-Ink-Refresh-Steuerung).
        // Insecure HTTP (Onyx' Server hat kein nutzbares TLS — weak-DH). Auf die Onyx-Gruppe
        // content-gefiltert, damit dieser Repo NUR den SDK ausliefern kann und nicht als
        // MITM-Vektor für andere Dependencies missbraucht werden kann. Die Integrität des
        // Artefakts selbst ist zusätzlich in gradle/verification-metadata.xml per SHA-256 gepinnt.
        maven {
            url = uri("http://repo.boox.com/repository/maven-public/")
            isAllowInsecureProtocol = true
            content { includeGroup("com.onyx.android.sdk") }
        }
        google()
        mavenCentral()
        // comic-cutter panel-detection library. Currently resolved from mavenLocal during development;
        // content-filtered to its group (this repo can deliver nothing else). TODO: swap for the public
        // JitPack/Maven-Central coordinate before CI/release.
        mavenLocal {
            content { includeGroup("io.github.gabriel-graf") }
        }
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
