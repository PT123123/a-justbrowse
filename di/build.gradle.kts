plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.justbrowse.di"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(project(":core:webview"))
    implementation(project(":core:scripts"))
    implementation(project(":core:adblock"))
    implementation(project(":core:sync"))

    implementation(libs.room.runtime)
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
}
