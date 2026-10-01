import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.katiusu.hyperautofillfix"
    // Miuix 0.9.x 的 AAR 元数据声明 minCompileSdk = 37，而 AGP 8.9.2 最高支持 36；
    // 界面没有用到 36/37 的新 API，因此用 34 构建并在 gradle.properties 里关闭该校验。
    compileSdk = 34
    buildToolsVersion = "34.0.0"

    defaultConfig {
        applicationId = "com.katiusu.hyperautofillfix"
        minSdk = 26
        targetSdk = 34
        versionCode = 2026100101
        versionName = "2.1.0"
    }

    buildTypes {
        release {
            // UI + Xposed 模块同一个 APK：release 开混淆与资源压缩
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        // versionCode 是用户指定的日期式 build number（2026100101），lint 认为它「接近 Int 上限」
        // 而报 HighAppVersionCode。该值仍在 Int 范围内（上限 2147483647），本项目也不走 Play 商店，
        // 因此显式关掉这一条；若换回普通自增版本号，可以删掉这个块。
        disable += "HighAppVersionCode"
        // 打包（assembleRelease）时不再自动跑 lintVitalRelease：AGP 默认会把一次完整的 lint 分析
        // 插进 release 打包流程（本机实测约 40~50s，占整次构建的一多半）。
        // 需要 lint 时单独跑 ./gradlew :app:lintDebug。
        checkReleaseBuilds = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    // Xposed API 仅编译期可见，运行时由框架注入
    compileOnly("de.robv.android.xposed:api:82")

    // HyperOS / MIUIX 风格 Compose 组件
    implementation(libs.miuix.ui)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.icons)
    // 液态玻璃底栏：backdrop 捕获 + textureBlur（API < 33 时由代码降级为不透明底栏）
    implementation(libs.miuix.blur)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
}
