pluginManagement {
    repositories {
        // 国内镜像优先（阿里云），官方仓库留在后面兜底：镜像里暂时没有的构件还能回源。
        // gradle-plugin 是 plugins.gradle.org 的镜像；google/public 覆盖 AGP、AndroidX、Miuix 等。
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 同上：阿里云 google（AGP / AndroidX / Miuix）+ public（Maven Central 等）优先
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
    }
}

rootProject.name = "hyperautofillfix"
include(":app")
