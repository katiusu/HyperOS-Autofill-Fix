// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    // AGP 9.x 自带 Kotlin 支持：app 模块只应用 android-application + kotlin-compose，
    // 这里保留 kotlin-android / android-library 的声明是为了版本目录完整（apply false 不生效）。
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

// 离线 / 证书受限环境下跳过 distributionUrl 的联网校验（发行版已在本地缓存）。
tasks.wrapper {
    validateDistributionUrl = false
}
