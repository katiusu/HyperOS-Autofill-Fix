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
        versionCode = 3
        versionName = "2.0.0"
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

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
}
