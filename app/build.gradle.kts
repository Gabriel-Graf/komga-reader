import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Credentials for the dev-local "*LiveTest" androidTest suite (NOT the CI `ci.` package, which uses
// static fixture Basic-Auth — see CiKomga). Sourced from a Gradle property, an env var, or the
// gitignored local.properties — NEVER committed. Empty by default → those tests skip (assumeTrue).
// Configure once: add `komga.test.apiKey=<key>` to local.properties (see CONTRIBUTING.md).
val komgaTestApiKey: String = run {
    fun prop(k: String) = (project.findProperty(k) as String?)?.takeIf { it.isNotBlank() }
    val fromLocal = rootProject.file("local.properties").takeIf { it.exists() }?.let { f ->
        Properties().apply { f.inputStream().use { load(it) } }.getProperty("komga.test.apiKey")
    }?.takeIf { it.isNotBlank() }
    prop("komga.test.apiKey") ?: System.getenv("KOMGA_TEST_API_KEY")?.takeIf { it.isNotBlank() } ?: fromLocal ?: ""
}
val komgaTestBaseUrl: String =
    (project.findProperty("komga.test.baseUrl") as String?)?.takeIf { it.isNotBlank() }
        ?: System.getenv("KOMGA_TEST_BASE_URL")?.takeIf { it.isNotBlank() }
        ?: "http://10.0.2.2:25600/api/v1/"

android {
    namespace = "com.komgareader.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.komgareader.app"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "com.komgareader.app.HiltTestRunner"
        buildConfigField("String", "KOMGA_TEST_API_KEY", "\"$komgaTestApiKey\"")
        buildConfigField("String", "KOMGA_TEST_BASE_URL", "\"$komgaTestBaseUrl\"")
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
}

dependencies {
    implementation(project(":data"))
    implementation(project(":domain"))
    implementation(project(":ui-api"))
    implementation(project(":source-api"))
    implementation(project(":plugin-api"))
    implementation(project(":plugin-host"))
    implementation(project(":eink-onyx"))
    implementation(project(":source-komga"))
    implementation(project(":source-opds"))
    implementation(project(":render-core"))
    implementation(project(":render-crengine"))
    implementation(project(":guided-view"))
    implementation(libs.coil.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation("androidx.compose.foundation:foundation")
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit.jupiter)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.room.runtime)
    androidTestImplementation(libs.room.ktx)
    androidTestImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.hilt.android.testing)
    androidTestImplementation(libs.androidx.test.core)
    kspAndroidTest(libs.hilt.compiler)
    debugImplementation(libs.compose.ui.test.manifest)
}

tasks.withType<Test> { useJUnitPlatform() }
