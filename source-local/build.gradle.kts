plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}
android {
    namespace = "com.komgareader.source.local"
    compileSdk = 34
    defaultConfig { minSdk = 28 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(project(":domain"))
    implementation(project(":source-api"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.documentfile)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}
