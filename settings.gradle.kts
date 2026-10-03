pluginManagement {
    repositories {
        // 国内镜像优先（阿里云）；官方仓库留在后面兜底。
        // 注意：本容器的 ~/.gradle/init.gradle 会在 settingsEvaluated 里清空并重写
        // 这里的仓库列表（dl.google.com / repo.maven.apache.org 在容器内不可达），
        // 所以这份声明主要服务于容器外的正常网络环境。
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
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
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
    }
}

rootProject.name = "hyperautofillfix"
include(":app")
