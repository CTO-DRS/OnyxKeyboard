pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        // Fallback mirrors: Gradle automatically falls through to the next
        // repository when the previous one fails (e.g. HTTP 429 throttling).
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/public")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Fallback mirrors (see note above) — only consulted when Central fails.
        maven("https://maven.aliyun.com/repository/central")
        maven("https://maven.aliyun.com/repository/public")
    }
}

rootProject.name = "Onyx Keyboard"
include(":app")
