plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.justbrowse.core.webview"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())
}

dependencies {
    implementation(project(":domain"))

    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.coroutines.android)
    implementation(libs.lifecycle.runtime.ktx)

    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    // 依赖脚本与拦截器模块
    implementation(project(":core:scripts"))
    implementation(project(":core:adblock"))
}
