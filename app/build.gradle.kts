plugins {
    // AGP 9.x 内置 Kotlin 支持，无需再应用 org.jetbrains.kotlin.android
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.katiusu.hyperautofillfix"
    // Miuix 0.9.4 的 AAR 元数据要求依赖方 compileSdk >= 37，故用 37；
    // build-tools 固定 36.0.0：SDK 上 37.0.0 只有 x86_64 产物，本容器（aarch64）跑不了，
    // 而 36.0.0 内置的 aapt2 是 aarch64 且能正常解析 android-37.0 的 resources.arsc。
    compileSdk = 37
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.katiusu.hyperautofillfix"
        // minSdk 保持 26：本模块要覆盖老 HyperOS / MIUI 设备，不跟参考示例的 34 走。
        // 液态玻璃/模糊只在 API 33+ 生效，低版本由代码降级为不透明底栏。
        minSdk = 26
        targetSdk = 34
        versionCode = 2026100300
        versionName = "2.3.0"
    }

    buildTypes {
        release {
            // 设置界面 + Xposed 模块同一个 APK：release 开混淆与资源压缩
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
        // versionCode 是用户指定的日期式 build number（2026100200），lint 认为它「接近 Int 上限」
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

dependencies {
    // Xposed API 仅编译期可见，运行时由框架注入
    compileOnly("de.robv.android.xposed:api:82")

    // HyperOS / MIUIX 风格 Compose 组件
    implementation(libs.miuix.core)
    implementation(libs.miuix.ui)
    implementation(libs.miuix.shader)
    implementation(libs.miuix.blur)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.squircle)
    implementation(libs.miuix.nav)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.material.icons)
    implementation(libs.haze)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
