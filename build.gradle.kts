plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.kapt) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

// 新版本 AndroidX（如 webkit 1.14+、部分 2025 库）会把 kotlin-stdlib 顶到 2.x，
// 而本工程编译器是 Kotlin 1.9。这些库运行时并不依赖 2.x 新增的 stdlib API，
// 统一把 stdlib 强制回编译器同版本即可同时满足编译与运行。
subprojects {
    configurations.configureEach {
        resolutionStrategy {
            force(
                "org.jetbrains.kotlin:kotlin-stdlib:${libs.versions.kotlin.get()}",
                "org.jetbrains.kotlin:kotlin-stdlib-jdk7:${libs.versions.kotlin.get()}",
                "org.jetbrains.kotlin:kotlin-stdlib-jdk8:${libs.versions.kotlin.get()}"
            )
        }
    }
}
